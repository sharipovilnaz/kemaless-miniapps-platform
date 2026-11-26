package com.kemaless.kemalessbank.operation;

import com.kemaless.kemalessbank.account.AccountResponse;
import com.kemaless.kemalessbank.account.AccountStatus;
import com.kemaless.kemalessbank.account.AccountTargetProjection;
import com.kemaless.kemalessbank.account.BankAccount;
import com.kemaless.kemalessbank.account.BankAccountRepository;
import com.kemaless.kemalessbank.common.BadRequestException;
import com.kemaless.kemalessbank.common.ConflictException;
import com.kemaless.kemalessbank.common.NotFoundException;
import com.kemaless.kemalessbank.user.BankUser;
import com.kemaless.kemalessbank.user.BankUserRepository;
import com.kemaless.kemalessbank.user.UserService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class OperationService {

    private static final BigDecimal MAX_TRANSFER_AMOUNT = new BigDecimal("999999999999999.99");

    private final UserService userService;
    private final BankUserRepository userRepository;
    private final BankAccountRepository accountRepository;
    private final OperationRepository operationRepository;

    public OperationService(
            UserService userService,
            BankUserRepository userRepository,
            BankAccountRepository accountRepository,
            OperationRepository operationRepository) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.operationRepository = operationRepository;
    }

    @Transactional(readOnly = true)
    public List<OperationResponse> history(String username) {
        BankUser user = userService.requireByUsername(username);
        return operationRepository.findAllByUserOrderByCreatedAtDesc(user).stream()
                .map(OperationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AccountStatementResponse statement(String username, Long accountId, OffsetDateTime from, OffsetDateTime to) {
        BankAccount account = requireUserAccount(username, accountId);
        OffsetDateTime toDate = to == null ? OffsetDateTime.now().plusSeconds(1) : to;
        OffsetDateTime fromDate = from == null ? toDate.minusMonths(1) : from;
        if (fromDate.isAfter(toDate)) {
            throw new BadRequestException("Дата начала выписки должна быть раньше даты окончания");
        }
        List<OperationResponse> operations = operationRepository
                .findAllByAccountAndCreatedAtBetweenOrderByCreatedAtDesc(account, fromDate, toDate)
                .stream()
                .map(OperationResponse::from)
                .toList();
        return new AccountStatementResponse(AccountResponse.from(account), fromDate, toDate, operations);
    }

    @Transactional
    public TransferResponse transferBetweenOwnAccounts(String username, InternalTransferRequest request) {
        if (request.fromAccountId().equals(request.toAccountId())) {
            throw new BadRequestException("Выберите два разных счета");
        }
        BigDecimal amount = normalizeAmount(request.amount());

        BankUser user = userService.requireByUsername(username);
        // Проверяем принадлежность обоих счетов скалярным запросом, не помещая сущности в L1-кеш до FOR UPDATE.
        accountRepository.findCurrencyByIdAndUser(request.fromAccountId(), user)
                .orElseThrow(() -> new NotFoundException("Счет не найден"));
        accountRepository.findCurrencyByIdAndUser(request.toAccountId(), user)
                .orElseThrow(() -> new NotFoundException("Счет не найден"));

        Map<Long, BankAccount> lockedAccounts = lockAccountsOrdered(request.fromAccountId(), request.toAccountId());
        BankAccount fromAccount = requireLockedOwnedAccount(lockedAccounts, request.fromAccountId(), user.getId());
        BankAccount toAccount = requireLockedOwnedAccount(lockedAccounts, request.toAccountId(), user.getId());
        requireActive(fromAccount, "Счет списания должен быть активным");
        requireActive(toAccount, "Счет зачисления должен быть активным");
        if (!currenciesMatch(fromAccount.getCurrency(), toAccount.getCurrency())) {
            throw new BadRequestException("Переводы между счетами в разных валютах недоступны (списание: "
                    + normalizeCurrency(fromAccount.getCurrency())
                    + ", зачисление: "
                    + normalizeCurrency(toAccount.getCurrency())
                    + ")");
        }
        if (fromAccount.getBalance().compareTo(amount) < 0) {
            throw new BadRequestException("Недостаточно средств на счете");
        }

        fromAccount.debit(amount);
        toAccount.credit(amount);
        String description = normalizeDescription(request.description());
        BankOperation outgoing = operationRepository.save(new BankOperation(
                fromAccount.getUser(),
                fromAccount,
                toAccount,
                OperationType.TRANSFER_OUT,
                amount,
                fromAccount.getCurrency(),
                description));
        BankOperation incoming = operationRepository.save(new BankOperation(
                toAccount.getUser(),
                toAccount,
                fromAccount,
                OperationType.TRANSFER_IN,
                amount,
                toAccount.getCurrency(),
                description));
        log.info(
                "Internal transfer completed: username={}, fromAccountId={}, toAccountId={}, amount={}",
                username,
                fromAccount.getId(),
                toAccount.getId(),
                amount);
        return new TransferResponse(
                AccountResponse.from(fromAccount),
                AccountResponse.from(toAccount),
                List.of(OperationResponse.from(outgoing), OperationResponse.from(incoming)));
    }

    @Transactional
    public TransferResponse transferToAnotherUser(String senderUsername, PeerTransferRequest request) {
        BigDecimal amount = normalizeAmount(request.amount());
        BankUser sender = userService.requireByUsername(senderUsername);
        // Скалярная выборка валюты до блокировки не даёт L1-кешу Hibernate затенить актуальный баланс после FOR UPDATE.
        String fromCurrency = accountRepository.findCurrencyByIdAndUser(request.fromAccountId(), sender)
                .orElseThrow(() -> new NotFoundException("Счет не найден"));

        BankUser recipient = resolveRecipient(request.recipient().trim());
        if (recipient.getId().equals(sender.getId())) {
            throw new BadRequestException("Нельзя перевести средства самому себе");
        }
        Long targetAccountId = resolveRecipientCreditAccountId(recipient, fromCurrency, request.toAccountNumber());

        Map<Long, BankAccount> lockedAccounts = lockAccountsOrdered(request.fromAccountId(), targetAccountId);
        BankAccount fromAccount = requireLockedOwnedAccount(lockedAccounts, request.fromAccountId(), sender.getId());
        BankAccount toAccount = requireLockedOwnedAccount(lockedAccounts, targetAccountId, recipient.getId());
        requireActive(fromAccount, "Счет списания должен быть активным");
        requireActive(toAccount, "Счёт зачисления получателя должен быть активным");
        if (!currenciesMatch(fromAccount.getCurrency(), toAccount.getCurrency())) {
            throw new BadRequestException("Валюта счёта зачисления ("
                    + normalizeCurrency(toAccount.getCurrency())
                    + ") должна совпадать со счётом списания ("
                    + normalizeCurrency(fromAccount.getCurrency())
                    + ")");
        }
        if (fromAccount.getBalance().compareTo(amount) < 0) {
            throw new BadRequestException("Недостаточно средств на счете");
        }

        fromAccount.debit(amount);
        toAccount.credit(amount);
        String description = normalizePeerDescription(request.description(), recipient.getUsername());
        BankOperation outgoing = operationRepository.save(new BankOperation(
                sender,
                fromAccount,
                toAccount,
                OperationType.TRANSFER_OUT,
                amount,
                fromAccount.getCurrency(),
                description));
        BankOperation incoming = operationRepository.save(new BankOperation(
                recipient,
                toAccount,
                fromAccount,
                OperationType.TRANSFER_IN,
                amount,
                toAccount.getCurrency(),
                description));
        log.info(
                "Peer transfer: from={}, toUser={}, amount={}, fromAccountId={}, toAccountId={}",
                senderUsername,
                recipient.getUsername(),
                amount,
                fromAccount.getId(),
                toAccount.getId());
        return new TransferResponse(
                AccountResponse.from(fromAccount),
                AccountResponse.from(toAccount),
                List.of(OperationResponse.from(outgoing), OperationResponse.from(incoming)));
    }

    private Map<Long, BankAccount> lockAccountsOrdered(Long firstAccountId, Long secondAccountId) {
        List<Long> sortedIds = Stream.of(firstAccountId, secondAccountId)
                .distinct()
                .sorted()
                .toList();
        return accountRepository.findAllByIdInOrderByIdAscForUpdate(sortedIds).stream()
                .collect(Collectors.toMap(BankAccount::getId, Function.identity()));
    }

    private BankAccount requireLockedOwnedAccount(
            Map<Long, BankAccount> lockedAccounts,
            Long accountId,
            Long expectedUserId) {
        BankAccount account = lockedAccounts.get(accountId);
        if (account == null || !account.getUser().getId().equals(expectedUserId)) {
            throw new NotFoundException("Счет не найден");
        }
        return account;
    }

    static BigDecimal normalizeAmount(BigDecimal rawAmount) {
        if (rawAmount == null || rawAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Сумма перевода должна быть больше нуля");
        }
        if (rawAmount.stripTrailingZeros().scale() > 2) {
            throw new BadRequestException("Сумма перевода должна содержать не более 2 знаков после запятой");
        }
        BigDecimal normalized = rawAmount.setScale(2, RoundingMode.UNNECESSARY);
        if (normalized.compareTo(MAX_TRANSFER_AMOUNT) > 0) {
            throw new BadRequestException("Сумма перевода превышает допустимый предел");
        }
        return normalized;
    }

    private BankUser resolveRecipient(String raw) {
        if (looksLikePhoneQuery(raw)) {
            String digits = raw.replaceAll("\\D", "");
            List<BankUser> matches = userRepository.findAllByPhoneDigits(digits);
            if (matches.isEmpty()) {
                throw new NotFoundException("Клиент с таким телефоном не найден");
            }
            if (matches.size() > 1) {
                throw new ConflictException("Найдено несколько клиентов с таким номером телефона — уточните получателя");
            }
            return matches.getFirst();
        }
        return userRepository
                .findByUsernameIgnoreCase(raw)
                .orElseThrow(() -> new NotFoundException("Клиент с логином «" + raw + "» не найден"));
    }

    private static boolean looksLikePhoneQuery(String raw) {
        boolean hasLetter = raw.chars().anyMatch(Character::isLetter);
        if (hasLetter) {
            return false;
        }
        String digits = raw.replaceAll("\\D", "");
        return digits.length() >= 10;
    }

    private Long resolveRecipientCreditAccountId(BankUser recipient, String fromCurrency, String toAccountNumber) {
        String fromCur = normalizeCurrency(fromCurrency);
        if (toAccountNumber != null && !toAccountNumber.isBlank()) {
            String normalizedNumber = toAccountNumber.trim();
            AccountTargetProjection explicit = accountRepository
                    .findTargetProjectionByAccountNumber(normalizedNumber)
                    .orElseThrow(() -> new NotFoundException("Счёт с номером «" + normalizedNumber + "» не найден"));
            if (!explicit.userId().equals(recipient.getId())) {
                throw new BadRequestException("Указанный счёт не принадлежит выбранному получателю");
            }
            if (explicit.status() != AccountStatus.ACTIVE) {
                throw new BadRequestException("Счёт зачисления получателя должен быть активным");
            }
            if (!currenciesMatch(explicit.currency(), fromCur)) {
                throw new BadRequestException("Валюта счёта по номеру ("
                        + normalizeCurrency(explicit.currency())
                        + ") должна совпадать со счётом списания ("
                        + fromCur
                        + ")");
            }
            return explicit.id();
        }
        return accountRepository.findActiveIdsByUserAndCurrencyOrderByIdAsc(recipient, fromCur).stream()
                .findFirst()
                .orElseThrow(() -> new BadRequestException(
                        "У получателя нет активного счёта в валюте "
                                + fromCur
                                + " для зачисления. Попросите открыть такой счёт или укажите номер конкретного счёта получателя в поле toAccountNumber."));
    }

    private static String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            return "";
        }
        return currency.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean currenciesMatch(String a, String b) {
        return normalizeCurrency(a).equals(normalizeCurrency(b));
    }

    private String normalizePeerDescription(String description, String recipientUsername) {
        if (description == null || description.isBlank()) {
            return "Перевод клиенту " + recipientUsername;
        }
        return description.trim();
    }

    private BankAccount requireUserAccount(String username, Long accountId) {
        BankUser user = userService.requireByUsername(username);
        return accountRepository.findByIdAndUser(accountId, user)
                .orElseThrow(() -> new NotFoundException("Счет не найден"));
    }

    private void requireActive(BankAccount account, String message) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new BadRequestException(message);
        }
    }

    private String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return "Перевод между своими счетами";
        }
        return description.trim();
    }
}

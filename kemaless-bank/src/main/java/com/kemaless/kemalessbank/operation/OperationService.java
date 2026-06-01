package com.kemaless.kemalessbank.operation;

import com.kemaless.kemalessbank.account.AccountResponse;
import com.kemaless.kemalessbank.account.AccountStatus;
import com.kemaless.kemalessbank.account.BankAccount;
import com.kemaless.kemalessbank.account.BankAccountRepository;
import com.kemaless.kemalessbank.common.BadRequestException;
import com.kemaless.kemalessbank.common.NotFoundException;
import com.kemaless.kemalessbank.common.ConflictException;
import com.kemaless.kemalessbank.user.BankUser;
import com.kemaless.kemalessbank.user.BankUserRepository;
import com.kemaless.kemalessbank.user.UserService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class OperationService {

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
        if (request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Сумма перевода должна быть больше нуля");
        }

        BankAccount fromAccount = requireUserAccount(username, request.fromAccountId());
        BankAccount toAccount = requireUserAccount(username, request.toAccountId());
        requireActive(fromAccount, "Счет списания должен быть активным");
        requireActive(toAccount, "Счет зачисления должен быть активным");
        if (!currenciesMatch(fromAccount.getCurrency(), toAccount.getCurrency())) {
            throw new BadRequestException("Переводы между счетами в разных валютах недоступны (списание: "
                    + normalizeCurrency(fromAccount.getCurrency())
                    + ", зачисление: "
                    + normalizeCurrency(toAccount.getCurrency())
                    + ")");
        }
        if (fromAccount.getBalance().compareTo(request.amount()) < 0) {
            throw new BadRequestException("Недостаточно средств на счете");
        }

        fromAccount.debit(request.amount());
        toAccount.credit(request.amount());
        String description = normalizeDescription(request.description());
        BankOperation outgoing = operationRepository.save(new BankOperation(
                fromAccount.getUser(),
                fromAccount,
                toAccount,
                OperationType.TRANSFER_OUT,
                request.amount(),
                fromAccount.getCurrency(),
                description));
        BankOperation incoming = operationRepository.save(new BankOperation(
                toAccount.getUser(),
                toAccount,
                fromAccount,
                OperationType.TRANSFER_IN,
                request.amount(),
                toAccount.getCurrency(),
                description));
        log.info(
                "Internal transfer completed: username={}, fromAccountId={}, toAccountId={}, amount={}",
                username,
                fromAccount.getId(),
                toAccount.getId(),
                request.amount());
        return new TransferResponse(
                AccountResponse.from(fromAccount),
                AccountResponse.from(toAccount),
                List.of(OperationResponse.from(outgoing), OperationResponse.from(incoming)));
    }

    @Transactional
    public TransferResponse transferToAnotherUser(String senderUsername, PeerTransferRequest request) {
        if (request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Сумма перевода должна быть больше нуля");
        }
        BankUser sender = userService.requireByUsername(senderUsername);
        BankAccount fromAccount = requireUserAccount(senderUsername, request.fromAccountId());
        requireActive(fromAccount, "Счет списания должен быть активным");
        if (fromAccount.getBalance().compareTo(request.amount()) < 0) {
            throw new BadRequestException("Недостаточно средств на счете");
        }

        BankUser recipient = resolveRecipient(request.recipient().trim());
        if (recipient.getId().equals(sender.getId())) {
            throw new BadRequestException("Нельзя перевести средства самому себе");
        }
        BankAccount toAccount = resolveRecipientCreditAccount(recipient, fromAccount, request.toAccountNumber());

        fromAccount.debit(request.amount());
        toAccount.credit(request.amount());
        String description = normalizePeerDescription(request.description(), recipient.getUsername());
        BankOperation outgoing = operationRepository.save(new BankOperation(
                sender,
                fromAccount,
                toAccount,
                OperationType.TRANSFER_OUT,
                request.amount(),
                fromAccount.getCurrency(),
                description));
        BankOperation incoming = operationRepository.save(new BankOperation(
                recipient,
                toAccount,
                fromAccount,
                OperationType.TRANSFER_IN,
                request.amount(),
                toAccount.getCurrency(),
                description));
        log.info(
                "Peer transfer: from={}, toUser={}, amount={}, fromAccountId={}, toAccountId={}",
                senderUsername,
                recipient.getUsername(),
                request.amount(),
                fromAccount.getId(),
                toAccount.getId());
        return new TransferResponse(
                AccountResponse.from(fromAccount),
                AccountResponse.from(toAccount),
                List.of(OperationResponse.from(outgoing), OperationResponse.from(incoming)));
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

    private BankAccount resolveRecipientCreditAccount(BankUser recipient, BankAccount fromAccount, String toAccountNumber) {
        String fromCur = normalizeCurrency(fromAccount.getCurrency());
        if (toAccountNumber != null && !toAccountNumber.isBlank()) {
            String normalizedNumber = toAccountNumber.trim();
            BankAccount explicit = accountRepository
                    .findByAccountNumber(normalizedNumber)
                    .orElseThrow(() -> new NotFoundException("Счёт с номером «" + normalizedNumber + "» не найден"));
            if (!explicit.getUser().getId().equals(recipient.getId())) {
                throw new BadRequestException("Указанный счёт не принадлежит выбранному получателю");
            }
            requireActive(explicit, "Счёт зачисления получателя должен быть активным");
            if (!currenciesMatch(explicit.getCurrency(), fromCur)) {
                throw new BadRequestException("Валюта счёта по номеру ("
                        + normalizeCurrency(explicit.getCurrency())
                        + ") должна совпадать со счётом списания ("
                        + fromCur
                        + ")");
            }
            return explicit;
        }
        return accountRepository.findAllByUserOrderByIdAsc(recipient).stream()
                .filter(a -> a.getStatus() == AccountStatus.ACTIVE)
                .filter(a -> currenciesMatch(a.getCurrency(), fromCur))
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

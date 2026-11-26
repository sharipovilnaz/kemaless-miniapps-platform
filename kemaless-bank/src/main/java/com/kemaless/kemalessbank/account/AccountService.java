package com.kemaless.kemalessbank.account;

import com.kemaless.kemalessbank.common.BadRequestException;
import com.kemaless.kemalessbank.common.NotFoundException;
import com.kemaless.kemalessbank.user.BankUser;
import com.kemaless.kemalessbank.user.UserService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class AccountService {

    private final UserService userService;
    private final BankAccountRepository accountRepository;

    public AccountService(UserService userService, BankAccountRepository accountRepository) {
        this.userService = userService;
        this.accountRepository = accountRepository;
    }

    @Transactional(readOnly = true)
    public List<BankAccount> findUserAccounts(String username) {
        return accountRepository.findAllVisibleByUser(userService.requireByUsername(username));
    }

    @Transactional(readOnly = true)
    public BankAccount requireUserAccount(String username, Long accountId) {
        BankUser user = userService.requireByUsername(username);
        return accountRepository.findByIdAndUser(accountId, user)
                .orElseThrow(() -> new NotFoundException("Счет не найден"));
    }

    @Transactional
    public BankAccount openAccount(String username, OpenAccountRequest request) {
        BankUser user = userService.requireByUsername(username);
        BankAccount account = accountRepository.save(new BankAccount(
                user,
                nextAccountNumber(),
                request.title(),
                request.currency().toUpperCase(Locale.ROOT),
                BigDecimal.ZERO));
        log.info("Account opened: username={}, accountId={}, currency={}", username, account.getId(), account.getCurrency());
        return account;
    }

    @Transactional
    public BankAccount closeAccount(String username, Long accountId) {
        BankUser user = userService.requireByUsername(username);
        // Блокируем все активные счета пользователя в порядке id, чтобы не закрыть последний при гонке.
        List<BankAccount> activeAccounts =
                accountRepository.findAllByUserAndStatusOrderByIdAscForUpdate(user, AccountStatus.ACTIVE);
        BankAccount account = activeAccounts.stream()
                .filter(candidate -> candidate.getId().equals(accountId))
                .findFirst()
                .orElseGet(() -> {
                    BankAccount existing = requireUserAccountForUpdate(user, accountId);
                    if (existing.getStatus() != AccountStatus.ACTIVE) {
                        throw new BadRequestException("Закрыть можно только активный счет");
                    }
                    return existing;
                });
        if (activeAccounts.size() <= 1) {
            throw new BadRequestException("Нельзя закрыть последний активный счет");
        }
        if (account.getBalance().compareTo(BigDecimal.ZERO) != 0) {
            throw new BadRequestException("Закрыть можно только счет с нулевым балансом");
        }
        account.close();
        log.info("Account closed: username={}, accountId={}", username, accountId);
        return account;
    }

    @Transactional
    public BankAccount blockAccount(String username, Long accountId) {
        BankAccount account = requireUserAccountForUpdate(userService.requireByUsername(username), accountId);
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new BadRequestException("Заблокировать можно только активный счет");
        }
        account.block();
        log.info("Account blocked: username={}, accountId={}", username, accountId);
        return account;
    }

    @Transactional
    public BankAccount unblockAccount(String username, Long accountId) {
        BankAccount account = requireUserAccountForUpdate(userService.requireByUsername(username), accountId);
        if (account.getStatus() != AccountStatus.BLOCKED) {
            throw new BadRequestException("Разблокировать можно только заблокированный счет");
        }
        account.unblock();
        log.info("Account unblocked: username={}, accountId={}", username, accountId);
        return account;
    }

    private BankAccount requireUserAccountForUpdate(BankUser user, Long accountId) {
        return accountRepository.findByIdAndUserForUpdate(accountId, user)
                .orElseThrow(() -> new NotFoundException("Счет не найден"));
    }

    private String nextAccountNumber() {
        String accountNumber;
        do {
            accountNumber = "KML-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
        } while (accountRepository.existsByAccountNumber(accountNumber));
        return accountNumber;
    }
}

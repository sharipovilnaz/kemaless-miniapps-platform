package com.kemaless.kemalessbank.account;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record AccountResponse(
        Long id,
        String accountNumber,
        String title,
        String currency,
        BigDecimal balance,
        AccountStatus status,
        OffsetDateTime createdAt) {

    public static AccountResponse from(BankAccount account) {
        return new AccountResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getTitle(),
                account.getCurrency(),
                account.getBalance(),
                account.getStatus(),
                account.getCreatedAt());
    }
}

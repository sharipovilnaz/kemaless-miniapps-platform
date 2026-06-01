package com.kemaless.kemalessbank.operation;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record OperationResponse(
        UUID id,
        Long accountId,
        String accountNumber,
        Long relatedAccountId,
        String relatedAccountNumber,
        OperationType type,
        BigDecimal amount,
        String currency,
        String description,
        OffsetDateTime createdAt) {

    public static OperationResponse from(BankOperation operation) {
        return new OperationResponse(
                operation.getId(),
                operation.getAccount().getId(),
                operation.getAccount().getAccountNumber(),
                operation.getRelatedAccount() == null ? null : operation.getRelatedAccount().getId(),
                operation.getRelatedAccount() == null ? null : operation.getRelatedAccount().getAccountNumber(),
                operation.getType(),
                operation.getAmount(),
                operation.getCurrency(),
                operation.getDescription(),
                operation.getCreatedAt());
    }
}

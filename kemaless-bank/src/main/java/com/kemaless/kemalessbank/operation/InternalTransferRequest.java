package com.kemaless.kemalessbank.operation;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record InternalTransferRequest(
        @NotNull Long fromAccountId,
        @NotNull Long toAccountId,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
        @Size(max = 500) String description) {
}

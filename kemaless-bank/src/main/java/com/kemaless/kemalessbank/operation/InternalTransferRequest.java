package com.kemaless.kemalessbank.operation;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record InternalTransferRequest(
        @NotNull Long fromAccountId,
        @NotNull Long toAccountId,
        @NotNull
                @DecimalMin(value = "0.01")
                @DecimalMax(value = "999999999999999.99")
                @Digits(integer = 17, fraction = 2)
                BigDecimal amount,
        @Size(max = 500) String description) {
}

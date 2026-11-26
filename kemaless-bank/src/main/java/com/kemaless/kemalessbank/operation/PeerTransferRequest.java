package com.kemaless.kemalessbank.operation;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record PeerTransferRequest(
        @NotNull Long fromAccountId,
        @NotBlank @Size(min = 2, max = 64) String recipient,
        @NotNull
                @DecimalMin(value = "0.01")
                @DecimalMax(value = "999999999999999.99")
                @Digits(integer = 17, fraction = 2)
                BigDecimal amount,
        @Size(max = 500) String description,
        @Size(max = 34) String toAccountNumber) {

    public PeerTransferRequest {
        toAccountNumber = toAccountNumber == null || toAccountNumber.isBlank() ? null : toAccountNumber.trim();
    }
}

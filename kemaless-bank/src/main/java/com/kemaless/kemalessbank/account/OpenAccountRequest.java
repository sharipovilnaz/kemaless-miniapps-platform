package com.kemaless.kemalessbank.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record OpenAccountRequest(
        @NotBlank @Size(max = 120) String title,
        @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency) {
}

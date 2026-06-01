package com.kemaless.kemalessbank.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(min = 3, max = 60) @Pattern(regexp = "^[a-zA-Z0-9_.-]+$") String username,
        @NotBlank @Size(min = 8, max = 128) String password,
        @NotBlank @Size(max = 255) String fullName,
        @NotBlank @Email @Size(max = 255) String email,
        @Pattern(regexp = "^$|^[+0-9()\\-\\s]{7,32}$") String phone) {
}

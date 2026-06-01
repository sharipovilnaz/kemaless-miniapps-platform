package com.kemaless.core.dto.request;

import com.kemaless.core.auth.enums.GlobalRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateUserRequest(
        @NotBlank String username,
        @Email String email,
        @NotBlank String password,
        @NotNull GlobalRole globalRole,
        String realmCode
) {}

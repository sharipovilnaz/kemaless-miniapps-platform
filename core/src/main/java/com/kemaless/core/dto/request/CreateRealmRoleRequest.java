package com.kemaless.core.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateRealmRoleRequest(
        @NotBlank
        @Pattern(regexp = "^[A-Z0-9_]+$", message = "Code must contain only uppercase latin letters, numbers, and underscores")
        String code,
        @NotBlank String name,
        String description
) {}

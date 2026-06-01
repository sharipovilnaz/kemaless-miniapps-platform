package com.kemaless.core.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateRealmRequest(
        @NotBlank @Pattern(regexp = "^[a-z0-9-]+$") String code,
        @NotBlank String name,
        String description
) {}

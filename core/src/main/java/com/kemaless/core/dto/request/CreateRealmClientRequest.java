package com.kemaless.core.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CreateRealmClientRequest(
        @NotBlank(message = "clientId is required")
        String clientId
) {}

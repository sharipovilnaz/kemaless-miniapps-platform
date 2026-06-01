package com.kemaless.core.auth.dto.api;

import jakarta.validation.constraints.NotBlank;

public record RealmClientTokenRequest(
        @NotBlank String clientId,
        @NotBlank String clientSecret
) {
}

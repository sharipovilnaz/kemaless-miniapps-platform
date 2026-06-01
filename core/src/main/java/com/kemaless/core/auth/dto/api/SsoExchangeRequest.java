package com.kemaless.core.auth.dto.api;

import jakarta.validation.constraints.NotBlank;

public record SsoExchangeRequest(
        @NotBlank String ticket,
        @NotBlank String miniAppCode
) {
}

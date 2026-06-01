package com.kemaless.core.auth.dto.api;

import jakarta.validation.constraints.NotBlank;

public record HostSessionLogoutRequest(
        @NotBlank String externalUserId
) {
}

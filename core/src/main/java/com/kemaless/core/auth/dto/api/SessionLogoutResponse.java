package com.kemaless.core.auth.dto.api;

public record SessionLogoutResponse(
        int revokedSessions
) {
}

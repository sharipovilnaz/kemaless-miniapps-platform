package com.kemaless.core.auth.dto.api;

import java.time.Instant;

public record LaunchTicketResponse(
        String ticket,
        Instant expiresAt
) {
}

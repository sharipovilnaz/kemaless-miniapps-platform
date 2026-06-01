package com.kemaless.core.auth.dto;

import java.util.UUID;
import java.util.List;

public record MiniAppJwtPrincipal(
        UUID sessionId,
        String realmCode,
        String miniAppCode,
        String externalUserId,
        List<String> realmRoles,
        long expiresAt
) {
}

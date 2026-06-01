package com.kemaless.core.auth.dto;

import com.kemaless.core.auth.enums.GlobalRole;

import java.util.UUID;

public record AdminJwtPrincipal(
        UUID userId,
        String username,
        GlobalRole globalRole,
        String realmCode
) {
}

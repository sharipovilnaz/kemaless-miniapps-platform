package com.kemaless.core.auth.dto.api;

import com.kemaless.core.auth.enums.GlobalRole;

import java.util.UUID;

public record AdminUserResponse(
        UUID id,
        String username,
        GlobalRole globalRole,
        String realmCode
) {
}

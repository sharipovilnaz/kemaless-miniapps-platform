package com.kemaless.core.dto.response;

import com.kemaless.core.enums.RoleSource;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record RoleMappingResponse(
        UUID id,
        String externalRole,
        String realmRoleCode,
        RoleSource source,
        OffsetDateTime createdAt
) {}

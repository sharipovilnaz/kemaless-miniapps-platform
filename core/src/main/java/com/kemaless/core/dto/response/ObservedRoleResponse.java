package com.kemaless.core.dto.response;

import com.kemaless.core.enums.ObservedRoleStatus;
import com.kemaless.core.enums.RoleSource;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record ObservedRoleResponse(
        UUID id,
        String externalRole,
        RoleSource source,
        ObservedRoleStatus status,
        OffsetDateTime firstSeenAt,
        OffsetDateTime lastSeenAt,
        long seenCount
) {}

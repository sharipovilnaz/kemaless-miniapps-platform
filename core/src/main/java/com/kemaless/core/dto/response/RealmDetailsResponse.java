package com.kemaless.core.dto.response;

import com.kemaless.core.enums.RealmStatus;
import lombok.Builder;

import java.time.OffsetDateTime;

@Builder
public record RealmDetailsResponse(
        String code,
        String name,
        String description,
        RealmStatus status,
        long miniAppsCount,
        long clientsCount,
        long rolesCount,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}

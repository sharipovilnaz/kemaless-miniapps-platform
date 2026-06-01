package com.kemaless.core.dto.response;

import com.kemaless.core.enums.RealmStatus;
import lombok.Builder;

import java.time.OffsetDateTime;

@Builder
public record RealmListItemResponse(
        String code,
        String name,
        RealmStatus status,
        long miniAppsCount,
        long clientsCount,
        OffsetDateTime createdAt
) {}

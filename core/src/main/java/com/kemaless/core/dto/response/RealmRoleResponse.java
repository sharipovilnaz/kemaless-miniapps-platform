package com.kemaless.core.dto.response;

import lombok.Builder;

import java.time.OffsetDateTime;

@Builder
public record RealmRoleResponse(
        String code,
        String name,
        String description,
        OffsetDateTime createdAt
) {}

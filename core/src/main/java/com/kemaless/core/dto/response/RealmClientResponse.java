package com.kemaless.core.dto.response;

import com.kemaless.core.enums.ClientStatus;
import lombok.Builder;

import java.time.OffsetDateTime;

@Builder
public record RealmClientResponse(
        String clientId,
        ClientStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}

package com.kemaless.core.ai.dto.tool;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditDto(
        UUID id,
        Instant timestamp,
        String actorType,
        String actorId,
        String realmCode,
        String action,
        String resourceType,
        String resourceId,
        String status,
        String ip,
        String userAgent,
        Map<String, Object> metadata
) {
}

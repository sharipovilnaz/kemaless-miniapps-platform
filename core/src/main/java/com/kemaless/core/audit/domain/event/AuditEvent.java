package com.kemaless.core.audit.domain.event;

import lombok.Builder;

import java.util.Map;

@Builder
public record AuditEvent(
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
) {}

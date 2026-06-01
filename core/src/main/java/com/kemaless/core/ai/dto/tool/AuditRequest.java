package com.kemaless.core.ai.dto.tool;

public record AuditRequest(
        String realmCode,
        Integer lookbackMinutes,
        Integer limit,
        String action,
        String status
) {
}

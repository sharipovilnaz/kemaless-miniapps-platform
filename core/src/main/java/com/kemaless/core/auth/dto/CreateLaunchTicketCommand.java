package com.kemaless.core.auth.dto;

import java.util.Map;
import java.util.UUID;

public record CreateLaunchTicketCommand(
        String realmCode,
        String clientId,
        UUID realmMiniAppId,
        String miniAppCode,
        AccessContext accessContext,
        Map<String, Object> launchContext
) {
}

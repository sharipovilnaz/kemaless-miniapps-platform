package com.kemaless.core.ai.dto.api;

import java.util.UUID;

public record AdminCopilotChatResponse(
        UUID sessionId,
        String text
) {
}

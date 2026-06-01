package com.kemaless.core.ai.dto.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record AdminCopilotChatRequest(
        @NotNull UUID sessionId,
        @NotBlank String text,
        List<AdminCopilotAttachmentRequest> attachments
) {
}

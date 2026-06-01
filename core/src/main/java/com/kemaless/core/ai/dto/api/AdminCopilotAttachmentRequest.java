package com.kemaless.core.ai.dto.api;

public record AdminCopilotAttachmentRequest(
        String objectKey,
        String url,
        String fileName,
        String mimeType,
        String fileType
) {
}

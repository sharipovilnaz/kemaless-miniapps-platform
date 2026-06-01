package com.kemaless.core.ai.dto.api;

public record AdminCopilotMediaUploadResponse(
        String objectKey,
        String url,
        String fileName,
        String mimeType,
        String fileType
) {
}

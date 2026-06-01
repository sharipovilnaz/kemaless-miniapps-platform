package com.kemaless.core.dto.response;

import com.kemaless.core.enums.AppStatus;
import lombok.Builder;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Builder
public record MiniAppResponse(
        UUID id,
        String code,
        String name,
        String description,
        String iconUrl,
        String defaultEntryUrl,
        String defaultBackendUrl,
        Map<String, Object> metadata,
        OffsetDateTime createdAt
) {}

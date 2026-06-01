package com.kemaless.core.dto.response;

import lombok.Builder;

import java.util.Map;

@Builder
public record RuntimeMiniAppResponse(
        String code,
        String name,
        String description,
        String iconUrl,
        String entryUrl,
        Map<String, Object> metadata
) {}

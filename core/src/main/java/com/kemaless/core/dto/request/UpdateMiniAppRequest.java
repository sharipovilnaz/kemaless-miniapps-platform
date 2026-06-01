package com.kemaless.core.dto.request;

import org.hibernate.validator.constraints.URL;

import java.util.Map;

public record UpdateMiniAppRequest(
        String name,
        String description,
        @URL String iconUrl,
        @URL String defaultEntryUrl,
        @URL String defaultBackendUrl,
        Map<String, Object> metadata
) {}

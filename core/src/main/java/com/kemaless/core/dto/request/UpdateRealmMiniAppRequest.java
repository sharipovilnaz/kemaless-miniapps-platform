package com.kemaless.core.dto.request;

import jakarta.validation.Valid;

import java.util.Map;

public record UpdateRealmMiniAppRequest(
        String entryUrl,
        String backendUrl,
        Map<String, Object> settings,
        @Valid AccessPolicyRequest accessPolicy
) {}

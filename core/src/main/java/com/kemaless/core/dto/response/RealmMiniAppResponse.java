package com.kemaless.core.dto.response;

import com.kemaless.core.enums.RealmMiniAppStatus;
import lombok.Builder;

import java.util.Map;

@Builder
public record RealmMiniAppResponse(
        String code,
        String name,
        RealmMiniAppStatus status,
        String entryUrl,
        String backendUrl,
        Map<String, Object> settings,
        AccessPolicyResponse accessPolicy
) {}

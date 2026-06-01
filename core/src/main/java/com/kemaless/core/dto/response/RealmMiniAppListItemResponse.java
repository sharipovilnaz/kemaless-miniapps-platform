package com.kemaless.core.dto.response;

import com.kemaless.core.enums.AccessType;
import com.kemaless.core.enums.RealmMiniAppStatus;
import lombok.Builder;

@Builder
public record RealmMiniAppListItemResponse(
        String code,
        String name,
        RealmMiniAppStatus status,
        String entryUrl,
        String backendUrl,
        AccessType accessType
) {}

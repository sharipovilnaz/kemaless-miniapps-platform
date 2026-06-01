package com.kemaless.core.dto.response;

import com.kemaless.core.enums.RealmMiniAppStatus;
import lombok.Builder;

@Builder
public record RealmMiniAppMetricsResponse(
        String realmCode,
        String miniAppCode,
        String miniAppName,
        RealmMiniAppStatus currentStatus,
        long launchesSuccess,
        long launchesDenied,
        long ssoSuccess,
        long ssoFailed,
        long totalRequests,
        long lastWindowSeconds,
        long cachedAtEpochSecond
) {
}

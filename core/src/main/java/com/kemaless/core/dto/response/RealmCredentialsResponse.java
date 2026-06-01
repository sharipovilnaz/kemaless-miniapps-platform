package com.kemaless.core.dto.response;

import lombok.Builder;

@Builder
public record RealmCredentialsResponse(
        String clientId,
        String clientSecret
) {
}

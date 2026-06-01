package com.kemaless.core.auth.dto;

public record RealmClientJwtPrincipal(
        String clientId,
        String realmCode
) {
}

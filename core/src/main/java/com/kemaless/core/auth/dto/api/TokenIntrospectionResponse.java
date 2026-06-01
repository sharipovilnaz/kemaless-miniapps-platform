package com.kemaless.core.auth.dto.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TokenIntrospectionResponse(
        boolean active,
        String type,
        String realmCode,
        String miniAppCode,
        String externalUserId,
        List<String> realmRoles,
        Instant expiresAt
) {
    public static TokenIntrospectionResponse inactive() {
        return new TokenIntrospectionResponse(false, null, null, null, null, null, null);
    }

    public static TokenIntrospectionResponse active(
            String realmCode,
            String miniAppCode,
            String externalUserId,
            List<String> realmRoles,
            Instant expiresAt
    ) {
        return new TokenIntrospectionResponse(
                true,
                "MINIAPP_USER_TOKEN",
                realmCode,
                miniAppCode,
                externalUserId,
                realmRoles,
                expiresAt
        );
    }
}

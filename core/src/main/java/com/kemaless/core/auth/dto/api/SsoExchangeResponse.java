package com.kemaless.core.auth.dto.api;

import java.util.Map;
import java.util.List;

public record SsoExchangeResponse(
        String accessToken,
        String refreshToken,
        long expiresIn,
        String tokenType,
        User user,
        Realm realm,
        MiniApp miniApp,
        Map<String, Object> context
) {
    public static SsoExchangeResponse bearer(
            String accessToken,
            String refreshToken,
            long expiresIn,
            User user,
            Realm realm,
            MiniApp miniApp,
            Map<String, Object> context
    ) {
        return new SsoExchangeResponse(accessToken, refreshToken, expiresIn, "Bearer", user, realm, miniApp, context);
    }

    public record User(
            String externalUserId,
            String email,
            List<String> realmRoles,
            Map<String, Object> attributes
    ) {
    }

    public record Realm(String code) {
    }

    public record MiniApp(String code) {
    }
}

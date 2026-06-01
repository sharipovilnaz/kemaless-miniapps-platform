package com.kemaless.core.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "platform.auth")
public record PlatformAuthProperties(
        boolean bootstrapEnabled,
        String adminUsername,
        String adminPassword,
        String backendSecretKey,
        long accessTokenTtlSeconds,
        long refreshTokenTtlSeconds,
        long launchTicketTtlSeconds,
        long miniappRefreshTokenTtlSeconds
) {

    public void validateBootstrapProperties() {
        requireNotBlank(adminUsername, "PLATFORM_ADMIN_USERNAME is required");
        requireNotBlank(adminPassword, "PLATFORM_ADMIN_PASSWORD is required");
    }

    private static void requireNotBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(message);
        }
    }
}

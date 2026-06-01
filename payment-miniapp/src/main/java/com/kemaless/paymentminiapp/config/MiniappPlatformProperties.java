package com.kemaless.paymentminiapp.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "miniapp")
public record MiniappPlatformProperties(
        String realmCode,
        String defaultMiniAppCode,
        Platform platform,
        Sso sso) {

    public MiniappPlatformProperties {
        realmCode = requireNonBlank(realmCode, "MINIAPP_REALM_CODE");
        defaultMiniAppCode = requireNonBlank(defaultMiniAppCode, "MINIAPP_CODE");
        if (platform == null) {
            platform = new Platform("");
        }
        if (sso == null) {
            sso = new Sso(Duration.ofSeconds(5), Duration.ofSeconds(30));
        }
    }

    public record Platform(String baseUrl) {
        public Platform {
            if (baseUrl == null || baseUrl.isBlank()) {
                throw new IllegalStateException("PLATFORM_BASE_URL must be configured for MiniApp SSO");
            }
            baseUrl = baseUrl.replaceAll("/$", "");
        }
    }

    public record Sso(Duration connectTimeout, Duration readTimeout) {
        public Sso {
            connectTimeout = connectTimeout == null ? Duration.ofSeconds(5) : connectTimeout;
            readTimeout = readTimeout == null ? Duration.ofSeconds(30) : readTimeout;
        }
    }

    private static String requireNonBlank(String value, String envName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(envName + " must be configured");
        }
        return value.trim();
    }
}

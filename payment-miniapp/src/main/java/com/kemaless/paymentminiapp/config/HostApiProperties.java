package com.kemaless.paymentminiapp.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "miniapp.host-api")
public record HostApiProperties(String baseUrl, Duration connectTimeout, Duration readTimeout) {

    public HostApiProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("MINIAPP_HOST_API_BASE_URL must be configured from the Realm MiniApp backendUrl");
        }
        baseUrl = baseUrl.trim().replaceAll("/$", "");
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(5);
        }
        if (readTimeout == null) {
            readTimeout = Duration.ofSeconds(30);
        }
    }
}

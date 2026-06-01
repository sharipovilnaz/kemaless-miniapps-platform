package com.kemaless.paymentminiapp.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "miniapp.web-security")
public record MiniappWebSecurityProperties(List<String> allowedOrigins, List<String> frameAncestors) {

    public MiniappWebSecurityProperties {
        allowedOrigins = allowedOrigins == null || allowedOrigins.isEmpty()
                ? List.of()
                : allowedOrigins.stream().filter(v -> v != null && !v.isBlank()).toList();
        frameAncestors = frameAncestors == null || frameAncestors.isEmpty()
                ? List.of("'self'")
                : frameAncestors.stream().filter(v -> v != null && !v.isBlank()).toList();
    }
}

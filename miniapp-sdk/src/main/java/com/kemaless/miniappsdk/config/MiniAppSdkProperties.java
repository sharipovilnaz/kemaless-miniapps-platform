package com.kemaless.miniappsdk.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

@Data
@ConfigurationProperties(prefix = "miniapp.platform")
public class MiniAppSdkProperties {
    private String url;
    private String realmCode;
    private String clientId;
    private String clientSecret;
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration readTimeout = Duration.ofSeconds(15);
    private int catalogSafeRetryMaxAttempts = 3;
}

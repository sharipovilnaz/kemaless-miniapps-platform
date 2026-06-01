package com.kemaless.miniappsdk.config;

import com.kemaless.miniappsdk.api.MiniAppPlatformClient;
import com.kemaless.miniappsdk.impl.MiniAppPlatformClientImpl;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(MiniAppSdkProperties.class)
@ConditionalOnProperty(prefix = "miniapp.platform", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MiniappAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MiniAppPlatformClient miniAppPlatformClient(MiniAppSdkProperties properties) {

        if (properties.getUrl() == null
                || properties.getRealmCode() == null
                || properties.getClientId() == null) {
            throw new IllegalStateException(
                    "Не заполнены обязательные настройки miniapp.platform.url, realm-code, client-id");
        }

        return MiniAppPlatformClientImpl.builder()
                .platformUrl(properties.getUrl())
                .realmCode(properties.getRealmCode())
                .clientId(properties.getClientId())
                .clientSecret(properties.getClientSecret())
                .connectTimeout(properties.getConnectTimeout())
                .readTimeout(properties.getReadTimeout())
                .catalogSafeRetryMaxAttempts(properties.getCatalogSafeRetryMaxAttempts())
                .build();
    }
}

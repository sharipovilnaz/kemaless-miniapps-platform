package com.kemaless.paymentminiapp.sso;

import com.kemaless.paymentminiapp.common.BadRequestException;
import com.kemaless.paymentminiapp.config.MiniappPlatformProperties;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class SsoExchangeService {

    private static final Logger log = LoggerFactory.getLogger(SsoExchangeService.class);

    private final MiniappPlatformProperties properties;
    private final RestClient.Builder restClientBuilder;

    public SsoExchangeService(
            MiniappPlatformProperties properties,
            RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClientBuilder = restClientBuilder;
    }

    public SsoSessionResponse exchange(String ticket, String realmCode, String miniAppCode) {
        if (ticket == null || ticket.isBlank()) {
            throw new BadRequestException("ticket обязателен");
        }
        String realm = realmCode != null && !realmCode.isBlank()
                ? realmCode
                : properties.realmCode();
        String code = miniAppCode != null && !miniAppCode.isBlank()
                ? miniAppCode
                : properties.defaultMiniAppCode();
        log.info("Exchanging payment service launch ticket");
        SsoSessionResponse body = restClientBuilder
                .baseUrl(properties.platform().baseUrl())
                .build()
                .post()
                .uri("/api/realms/{realmCode}/sso/exchange", realm)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("ticket", ticket, "miniAppCode", code))
                .retrieve()
                .body(SsoSessionResponse.class);
        if (body == null) {
            throw new BadRequestException("Пустой ответ SSO от платформы");
        }
        return body;
    }
}

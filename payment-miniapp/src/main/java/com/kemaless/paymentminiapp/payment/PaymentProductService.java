package com.kemaless.paymentminiapp.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kemaless.paymentminiapp.common.BadRequestException;
import com.kemaless.paymentminiapp.common.UnauthorizedException;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;

@Service
public class PaymentProductService {

    private static final Logger log = LoggerFactory.getLogger(PaymentProductService.class);

    private final RestClient hostApi;
    private final ObjectMapper objectMapper;

    public PaymentProductService(
            @Qualifier("hostApiRestClient") RestClient hostApi,
            ObjectMapper objectMapper) {
        this.hostApi = hostApi;
        this.objectMapper = objectMapper;
    }

    public JsonNode currentUser(String authorization) {
        return get("/api/users/me", authorization, objectMapper.createObjectNode());
    }

    public JsonNode accounts(String authorization) {
        return get("/api/accounts", authorization, objectMapper.createArrayNode());
    }

    public JsonNode operations(String authorization) {
        return get("/api/operations", authorization, objectMapper.createArrayNode());
    }

    public JsonNode peerTransfer(String authorization, PeerTransferCommand cmd) {
        requirePositiveAmount(cmd.amount());
        String recipient = requireRecipient(cmd.recipient());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("fromAccountId", cmd.fromAccountId());
        body.put("recipient", recipient);
        body.put("amount", cmd.amount());
        if (cmd.description() != null && !cmd.description().isBlank()) {
            body.put("description", cmd.description().trim());
        }
        if (cmd.toAccountNumber() != null && !cmd.toAccountNumber().isBlank()) {
            body.put("toAccountNumber", cmd.toAccountNumber());
        }
        return postJson("/api/transfers/peer", authorization, body);
    }

    private static void requirePositiveAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(new BigDecimal("0.01")) < 0) {
            throw new BadRequestException("Сумма должна быть не меньше 0.01");
        }
    }

    private static String requireRecipient(String recipient) {
        if (recipient == null || recipient.isBlank()) {
            throw new BadRequestException("Укажите получателя (логин или телефон)");
        }
        String trimmed = recipient.trim();
        if (trimmed.length() < 2) {
            throw new BadRequestException("Получатель слишком короткий");
        }
        return trimmed;
    }

    private JsonNode get(String path, String authorization, JsonNode fallback) {
        requireBearer(authorization);
        try {
            String body = hostApi.get()
                    .uri(path)
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .retrieve()
                    .body(String.class);
            return parseJson(body);
        } catch (ResourceAccessException e) {
            log.warn("Host API is unavailable, miniapp backend request ignored: method=GET path={} reason={}", path, e.getMessage());
            return fallback;
        }
    }

    private JsonNode postJson(String path, String authorization, Object body) {
        requireBearer(authorization);
        try {
            String raw = hostApi.post()
                    .uri(path)
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return parseJson(raw);
        } catch (ResourceAccessException e) {
            log.warn("Host API is unavailable, miniapp backend request ignored: method=POST path={} reason={}", path, e.getMessage());
            return objectMapper.createObjectNode().put("ignored", true);
        }
    }

    private JsonNode parseJson(String body) {
        if (body == null || body.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            throw new BadRequestException("Не удалось разобрать ответ host API");
        }
    }

    private static void requireBearer(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            throw new UnauthorizedException(
                    "Нет miniapp-сессии. Откройте сервис из host-приложения, чтобы пройти SSO.");
        }
        if (!authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw new UnauthorizedException("Ожидается заголовок Authorization: Bearer <miniapp_access_token>");
        }
    }
}

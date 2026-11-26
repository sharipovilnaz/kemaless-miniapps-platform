package com.kemaless.core.auth.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kemaless.core.auth.config.PlatformAuthProperties;
import com.kemaless.core.auth.dto.MiniAppJwtPrincipal;
import com.kemaless.core.auth.entity.MiniAppSession;
import com.kemaless.core.auth.exception.AuthException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MiniAppJwtService {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String TOKEN_TYPE = "MINIAPP_USER_TOKEN";

    private final PlatformAuthProperties properties;
    private final ObjectMapper objectMapper;

    public String createAccessToken(MiniAppSession session) {
        long issuedAt = Instant.now().getEpochSecond();
        long expiresAt = issuedAt + properties.accessTokenTtlSeconds();
        Map<String, Object> header = Map.of(
                "alg", "HS256",
                "typ", "JWT"
        );
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", session.getExternalUserId());
        payload.put("type", TOKEN_TYPE);
        payload.put("sessionId", session.getId().toString());
        payload.put("realmCode", session.getRealm().getCode());
        payload.put("miniAppCode", session.getMiniAppCode());
        payload.put("externalUserId", session.getExternalUserId());
        payload.put("realmRoles", readRealmRoles(session.getRealmRoles()));
        payload.put("iat", issuedAt);
        payload.put("exp", expiresAt);
        String unsignedToken = base64Json(header) + "." + base64Json(payload);
        return unsignedToken + "." + sign(unsignedToken);
    }

    public MiniAppJwtPrincipal validateAccessToken(String token) {
        if (token == null || token.isBlank()) {
            throw new AuthException("Invalid miniapp access token");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new AuthException("Invalid miniapp access token");
        }
        Map<String, Object> header = parseJsonObject(parts[0]);
        if (!"HS256".equals(header.get("alg"))) {
            throw new AuthException("Invalid miniapp access token");
        }
        String unsignedToken = parts[0] + "." + parts[1];
        if (!MessageDigest.isEqual(sign(unsignedToken).getBytes(StandardCharsets.UTF_8), parts[2].getBytes(StandardCharsets.UTF_8))) {
            throw new AuthException("Invalid miniapp access token");
        }
        Map<String, Object> payload = parseJsonObject(parts[1]);
        if (!TOKEN_TYPE.equals(payload.get("type"))) {
            throw new AuthException("Invalid miniapp access token");
        }
        long expiresAt = extractExpiresAt(payload);
        if (expiresAt <= Instant.now().getEpochSecond()) {
            throw new AuthException("Miniapp access token expired");
        }
        UUID sessionId;
        try {
            sessionId = UUID.fromString(extractRequiredString(payload, "sessionId"));
        } catch (IllegalArgumentException exception) {
            throw new AuthException("Invalid miniapp access token");
        }
        return new MiniAppJwtPrincipal(
                sessionId,
                extractRequiredString(payload, "realmCode"),
                extractRequiredString(payload, "miniAppCode"),
                extractRequiredString(payload, "externalUserId"),
                extractStringList(payload.get("realmRoles")),
                expiresAt
        );
    }

    private List<String> readRealmRoles(String realmRoles) {
        try {
            return objectMapper.readValue(realmRoles, new TypeReference<>() {
            });
        } catch (Exception exception) {
            throw new IllegalStateException("Could not read miniapp realm roles", exception);
        }
    }

    private String base64Json(Map<String, Object> value) {
        try {
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(objectMapper.writeValueAsBytes(value));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not create JWT payload", exception);
        }
    }

    private String sign(String unsignedToken) {
        byte[] keyBytes = requireSecretKeyBytes();
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(keyBytes, HMAC_SHA256));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(unsignedToken.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not sign JWT", exception);
        }
    }

    private byte[] requireSecretKeyBytes() {
        String secret = properties.backendSecretKey();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("PLATFORM_BACKEND_SECRET_KEY must be configured");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("PLATFORM_BACKEND_SECRET_KEY must be at least 32 bytes for HS256");
        }
        return bytes;
    }

    private Map<String, Object> parseJsonObject(String encoded) {
        try {
            byte[] json = Base64.getUrlDecoder().decode(encoded);
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception exception) {
            throw new AuthException("Invalid miniapp access token");
        }
    }

    private static long extractExpiresAt(Map<String, Object> payload) {
        Object exp = payload.get("exp");
        if (!(exp instanceof Number number)) {
            throw new AuthException("Invalid miniapp access token");
        }
        return number.longValue();
    }

    private static String extractRequiredString(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (!(value instanceof String str) || str.isBlank()) {
            throw new AuthException("Invalid miniapp access token");
        }
        return str;
    }

    private static List<String> extractStringList(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof List<?> list)) {
            throw new AuthException("Invalid miniapp access token");
        }
        return list.stream()
                .map(item -> {
                    if (!(item instanceof String str)) {
                        throw new AuthException("Invalid miniapp access token");
                    }
                    return str;
                })
                .toList();
    }
}

package com.kemaless.core.auth.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kemaless.core.auth.config.PlatformAuthProperties;
import com.kemaless.core.auth.dto.AdminJwtPrincipal;
import com.kemaless.core.auth.entity.PlatformUser;
import com.kemaless.core.auth.enums.GlobalRole;
import com.kemaless.core.auth.exception.AuthException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminJwtService {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private final PlatformAuthProperties properties;
    private final ObjectMapper objectMapper;

    public String createAccessToken(PlatformUser user, String realmCode) {
        long issuedAt = Instant.now().getEpochSecond();
        long expiresAt = issuedAt + properties.accessTokenTtlSeconds();
        Map<String, Object> header = Map.of(
                "alg", "HS256",
                "typ", "JWT"
        );
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", user.getId().toString());
        payload.put("username", user.getUsername());
        payload.put("type", "PLATFORM_USER");
        payload.put("globalRole", user.getGlobalRole());
        payload.put("realmCode", realmCode);
        payload.put("iat", issuedAt);
        payload.put("exp", expiresAt);
        String unsignedToken = base64Json(header) + "." + base64Json(payload);
        return unsignedToken + "." + sign(unsignedToken);
    }

    public AdminJwtPrincipal validateAccessToken(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new AuthException("Invalid access token");
        }
        String unsignedToken = parts[0] + "." + parts[1];
        if (!MessageDigest.isEqual(sign(unsignedToken).getBytes(StandardCharsets.UTF_8), parts[2].getBytes(StandardCharsets.UTF_8))) {
            throw new AuthException("Invalid access token");
        }
        Map<String, Object> payload = parsePayload(parts[1]);
        if (!"PLATFORM_USER".equals(payload.get("type"))) {
            throw new AuthException("Invalid access token");
        }
        long expiresAt = ((Number) payload.get("exp")).longValue();
        if (expiresAt <= Instant.now().getEpochSecond()) {
            throw new AuthException("Access token expired");
        }
        GlobalRole role;
        try {
            role = GlobalRole.valueOf((String) payload.get("globalRole"));
        } catch (IllegalArgumentException e) {
            throw new AuthException("Invalid global role in token");
        }
        return new AdminJwtPrincipal(
                UUID.fromString((String) payload.get("sub")),
                (String) payload.get("username"),
                role,
                (String) payload.get("realmCode")
        );
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
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(properties.backendSecretKey().getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(unsignedToken.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not sign JWT", exception);
        }
    }

    private Map<String, Object> parsePayload(String encodedPayload) {
        try {
            byte[] json = Base64.getUrlDecoder().decode(encodedPayload);
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception exception) {
            throw new AuthException("Invalid access token");
        }
    }
}

package com.kemaless.core.auth.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kemaless.core.auth.config.PlatformAuthProperties;
import com.kemaless.core.auth.dto.RealmClientJwtPrincipal;
import com.kemaless.core.auth.exception.AuthException;
import com.kemaless.core.model.RealmClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HostServiceJwtService {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String TOKEN_TYPE = "REALM_CLIENT_TOKEN";

    private final PlatformAuthProperties properties;
    private final ObjectMapper objectMapper;

    public String createAccessToken(RealmClient client) {
        long issuedAt = Instant.now().getEpochSecond();
        long expiresAt = issuedAt + properties.accessTokenTtlSeconds();
        Map<String, Object> header = Map.of(
                "alg", "HS256",
                "typ", "JWT"
        );
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", client.getClientId());
        payload.put("type", TOKEN_TYPE);
        payload.put("realmCode", client.getRealm().getCode());
        payload.put("clientId", client.getClientId());
        payload.put("iat", issuedAt);
        payload.put("exp", expiresAt);
        String unsignedToken = base64Json(header) + "." + base64Json(payload);
        return unsignedToken + "." + sign(unsignedToken);
    }

    public RealmClientJwtPrincipal validateAccessToken(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new AuthException("Invalid host service token");
        }
        String unsignedToken = parts[0] + "." + parts[1];
        if (!MessageDigest.isEqual(sign(unsignedToken).getBytes(StandardCharsets.UTF_8), parts[2].getBytes(StandardCharsets.UTF_8))) {
            throw new AuthException("Invalid host service token");
        }
        Map<String, Object> payload = parsePayload(parts[1]);
        if (!TOKEN_TYPE.equals(payload.get("type"))) {
            throw new AuthException("Invalid host service token");
        }
        long expiresAt = ((Number) payload.get("exp")).longValue();
        if (expiresAt <= Instant.now().getEpochSecond()) {
            throw new AuthException("Host service token expired");
        }
        return new RealmClientJwtPrincipal(
                (String) payload.get("clientId"),
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
            throw new AuthException("Invalid host service token");
        }
    }
}

package com.kemaless.core.auth.service;

import com.kemaless.core.auth.config.PlatformAuthProperties;
import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TokenHashService {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private final PlatformAuthProperties properties;

    public String hash(String rawToken) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(properties.backendSecretKey().getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            return hex(mac.doFinal(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not hash token", exception);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }
}

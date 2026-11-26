package com.kemaless.core.auth.service;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RealmClientSecretService {

    private static final int SECRET_BYTES = 64;

    private final SecureRandom secureRandom;
    private final TokenHashService tokenHashService;

    public String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hashSecret(String rawSecret) {
        return tokenHashService.hash(rawSecret);
    }

    public boolean matches(String rawSecret, String secretHash) {
        if (rawSecret == null || rawSecret.isBlank() || secretHash == null || secretHash.isBlank()) {
            return false;
        }
        String rawSecretHash = hashSecret(rawSecret);
        return MessageDigest.isEqual(
                rawSecretHash.getBytes(StandardCharsets.UTF_8),
                secretHash.getBytes(StandardCharsets.UTF_8)
        );
    }
}

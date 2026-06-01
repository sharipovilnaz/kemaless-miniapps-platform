package com.kemaless.core.auth.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

import com.kemaless.core.auth.entity.AdminRefreshToken;
import com.kemaless.core.auth.entity.PlatformUser;
import com.kemaless.core.auth.enums.RefreshTokenStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final SecureRandom secureRandom;

    public String generate() {
        byte[] bytes = new byte[64];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public AdminRefreshToken createActiveToken(PlatformUser user, String tokenHash, Instant expiresAt) {
        AdminRefreshToken token = new AdminRefreshToken();
        token.setUser(user);
        token.setTokenHash(tokenHash);
        token.setStatus(RefreshTokenStatus.ACTIVE);
        token.setExpiresAt(expiresAt);
        return token;
    }

    public void revokeToken(AdminRefreshToken token, String replacementHash) {
        token.setStatus(RefreshTokenStatus.REVOKED);
        token.setRevokedAt(Instant.now());
        token.setReplacedByTokenHash(replacementHash);
    }

    public void expireToken(AdminRefreshToken token) {
        token.setStatus(RefreshTokenStatus.EXPIRED);
    }
}

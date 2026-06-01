package com.kemaless.core.auth.service;

import com.kemaless.core.auth.config.PlatformAuthProperties;
import com.kemaless.core.auth.dto.api.AdminAuthResponse;
import com.kemaless.core.auth.dto.api.AdminLoginRequest;
import com.kemaless.core.auth.dto.api.AdminLogoutRequest;
import com.kemaless.core.auth.dto.api.AdminUserResponse;
import com.kemaless.core.auth.dto.api.RefreshTokenRequest;
import com.kemaless.core.auth.dto.api.TokenResponse;
import com.kemaless.core.auth.entity.AdminRefreshToken;
import com.kemaless.core.auth.entity.PlatformUser;
import com.kemaless.core.auth.enums.GlobalRole;
import com.kemaless.core.auth.enums.PlatformUserStatus;
import com.kemaless.core.auth.enums.RefreshTokenStatus;
import com.kemaless.core.auth.exception.AuthException;
import com.kemaless.core.auth.repository.AdminRefreshTokenRepository;
import com.kemaless.core.auth.repository.PlatformUserRepository;
import java.time.Instant;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminAuthService{

    private static final String INVALID_CREDENTIALS_MESSAGE = "Invalid username or password";
    private static final String INVALID_REFRESH_TOKEN_MESSAGE = "Invalid refresh token";

    private final PlatformAuthProperties properties;
    private final PlatformUserRepository platformUserRepository;
    private final AdminRefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final TokenHashService tokenHashService;
    private final AdminJwtService adminJwtService;

    public PlatformUser root(String username, String passwordHash, PlatformUserStatus status, GlobalRole role) {
        PlatformUser user = new PlatformUser();
        user.setId(UUID.randomUUID());
        user.setUsername(username);
        user.setPasswordHash(passwordHash);
        user.setStatus(status);
        user.setGlobalRole(role);
        return user;
    }

    @Transactional
    public AdminAuthResponse login(AdminLoginRequest request) {
        PlatformUser user = platformUserRepository.findByUsername(request.username())
                .orElse(null);
        if (user == null || cannotLogin(user) || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new AuthException(INVALID_CREDENTIALS_MESSAGE);
        }
        if (user.getGlobalRole() == null) {
            throw new AuthException(INVALID_CREDENTIALS_MESSAGE);
        }
        String realmCode = getRealmCode(user);
        String accessToken = adminJwtService.createAccessToken(user, realmCode);
        String refreshToken = issueRefreshToken(user);
        return new AdminAuthResponse(
                accessToken,
                refreshToken,
                properties.accessTokenTtlSeconds(),
                "Bearer",
                toUserResponse(user, realmCode)
        );
    }

    @Transactional
    public TokenResponse refresh(RefreshTokenRequest request) {
        String oldTokenHash = tokenHashService.hash(request.refreshToken());
        AdminRefreshToken oldToken = refreshTokenRepository.findByTokenHash(oldTokenHash)
                .orElseThrow(() -> new AuthException(INVALID_REFRESH_TOKEN_MESSAGE));
        if (!isUsable(oldToken)) {
            markExpiredIfNeeded(oldToken);
            throw new AuthException(INVALID_REFRESH_TOKEN_MESSAGE);
        }
        PlatformUser user = oldToken.getUser();
        if (cannotLogin(user)) {
            throw new AuthException(INVALID_REFRESH_TOKEN_MESSAGE);
        }
        if (user.getGlobalRole() == null) {
            throw new AuthException(INVALID_REFRESH_TOKEN_MESSAGE);
        }
        String newRefreshToken = refreshTokenService.generate();
        String newRefreshTokenHash = tokenHashService.hash(newRefreshToken);
        AdminRefreshToken newStoredToken = refreshTokenService.createActiveToken(
                user,
                newRefreshTokenHash,
                Instant.now().plusSeconds(properties.refreshTokenTtlSeconds())
        );
        refreshTokenService.revokeToken(oldToken, newRefreshTokenHash);
        refreshTokenRepository.save(newStoredToken);
        String accessToken = adminJwtService.createAccessToken(user, getRealmCode(user));
        return TokenResponse.bearer(accessToken, newRefreshToken, properties.accessTokenTtlSeconds());
    }

    @Transactional
    public void logout(UUID userId, AdminLogoutRequest request) {
        String tokenHash = tokenHashService.hash(request.refreshToken());
        AdminRefreshToken refreshToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new AuthException(INVALID_REFRESH_TOKEN_MESSAGE));
        if (!refreshToken.getUser().getId().equals(userId)) {
            throw new AuthException(INVALID_REFRESH_TOKEN_MESSAGE);
        }
        if (refreshToken.getStatus() == RefreshTokenStatus.ACTIVE) {
            refreshTokenService.revokeToken(refreshToken, null);
        }
    }

    private String issueRefreshToken(PlatformUser user) {
        String refreshToken = refreshTokenService.generate();
        String refreshTokenHash = tokenHashService.hash(refreshToken);
        refreshTokenRepository.save(refreshTokenService.createActiveToken(
                user,
                refreshTokenHash,
                Instant.now().plusSeconds(properties.refreshTokenTtlSeconds())
        ));
        return refreshToken;
    }

    private boolean cannotLogin(PlatformUser user) {
        return user.getStatus() != PlatformUserStatus.ACTIVE;
    }

    private boolean isUsable(AdminRefreshToken token) {
        return token.getStatus() == RefreshTokenStatus.ACTIVE && token.getExpiresAt().isAfter(Instant.now());
    }

    private void markExpiredIfNeeded(AdminRefreshToken token) {
        if (token.getStatus() == RefreshTokenStatus.ACTIVE && !token.getExpiresAt().isAfter(Instant.now())) {
            refreshTokenService.expireToken(token);
        }
    }

    private String getRealmCode(PlatformUser user) {
        return user.getRealm() == null ? null : user.getRealm().getCode();
    }

    private AdminUserResponse toUserResponse(PlatformUser user, String realmCode) {
        return new AdminUserResponse(
                user.getId(),
                user.getUsername(),
                user.getGlobalRole(),
                realmCode
        );
    }
}

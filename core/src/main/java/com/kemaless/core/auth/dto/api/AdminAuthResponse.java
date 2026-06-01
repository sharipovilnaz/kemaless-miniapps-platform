package com.kemaless.core.auth.dto.api;

public record AdminAuthResponse(
        String accessToken,
        String refreshToken,
        long expiresIn,
        String tokenType,
        AdminUserResponse user
) {
}

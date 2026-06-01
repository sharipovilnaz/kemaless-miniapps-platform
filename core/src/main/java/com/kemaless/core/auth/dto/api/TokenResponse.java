package com.kemaless.core.auth.dto.api;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        long expiresIn,
        String tokenType
) {
    public static TokenResponse bearer(String accessToken, String refreshToken, long expiresIn) {
        return new TokenResponse(accessToken, refreshToken, expiresIn, "Bearer");
    }
}

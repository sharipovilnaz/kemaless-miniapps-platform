package com.kemaless.core.auth.dto.api;

public record HostServiceTokenResponse(
        String accessToken,
        long expiresIn,
        String tokenType
) {
    public static HostServiceTokenResponse bearer(String accessToken, long expiresIn) {
        return new HostServiceTokenResponse(accessToken, expiresIn, "Bearer");
    }
}

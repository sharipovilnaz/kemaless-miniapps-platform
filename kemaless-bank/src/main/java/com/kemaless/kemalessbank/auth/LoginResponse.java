package com.kemaless.kemalessbank.auth;

import com.kemaless.kemalessbank.user.UserDto;
import java.time.Instant;

public record LoginResponse(
        String accessToken,
        Instant expiresAt,
        String refreshToken,
        Instant refreshExpiresAt,
        String tokenType,
        UserDto user) {
}

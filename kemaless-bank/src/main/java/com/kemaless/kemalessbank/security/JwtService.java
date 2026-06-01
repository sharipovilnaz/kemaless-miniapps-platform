package com.kemaless.kemalessbank.security;

import com.kemaless.kemalessbank.config.BankSecurityProperties;
import com.kemaless.kemalessbank.user.BankUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final BankSecurityProperties.Jwt properties;
    private final SecretKey signingKey;

    public JwtService(BankSecurityProperties properties) {
        this.properties = properties.jwt();
        this.signingKey = Keys.hmacShaKeyFor(this.properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public TokenResponse createAccessToken(BankUser user) {
        return createUserToken(user, "access", properties.accessTokenTtl());
    }

    public TokenResponse createRefreshToken(BankUser user) {
        return createUserToken(user, "refresh", properties.refreshTokenTtl());
    }

    private TokenResponse createUserToken(BankUser user, String tokenType, java.time.Duration ttl) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(ttl);
        String token = Jwts.builder()
                .issuer(properties.issuer())
                .subject(user.getUsername())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .claim("typ", tokenType)
                .claim("roles", user.roles())
                .signWith(signingKey)
                .compact();
        return new TokenResponse(token, expiresAt);
    }

    public Claims validate(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(properties.issuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public record TokenResponse(String token, Instant expiresAt) {
    }
}

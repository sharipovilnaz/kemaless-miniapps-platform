package com.kemaless.kemalessbank.config;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bank")
public record BankSecurityProperties(Jwt jwt, Cors cors, Csp csp, MiniappApi miniappApi, String publicUiUrl) {

    public BankSecurityProperties {
        publicUiUrl = publicUiUrl == null || publicUiUrl.isBlank() ? "http://localhost:5173" : publicUiUrl.trim();
        if (jwt == null || jwt.secret() == null || jwt.secret().isBlank()) {
            throw new IllegalStateException("BANK_JWT_SECRET must be configured for Kemaless Bank user sessions");
        }
    }

    public record Jwt(String issuer, String secret, Duration accessTokenTtl, Duration refreshTokenTtl) {
    }

    public record Cors(List<String> allowedOrigins, List<String> allowedOriginPatterns) {

        public Cors {
            allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
            allowedOriginPatterns =
                    allowedOriginPatterns == null ? List.of() : List.copyOf(allowedOriginPatterns);
        }
    }

    public record Csp(String frameSrc) {
        public Csp {
            frameSrc = frameSrc == null || frameSrc.isBlank() ? "'self'" : frameSrc.trim();
        }

        public String directive() {
            return Arrays.stream(frameSrc.split(","))
                    .map(String::trim)
                    .filter(v -> !v.isBlank())
                    .reduce((left, right) -> left + " " + right)
                    .orElse("'self'");
        }
    }

    public record MiniappApi(List<String> allowedCodes) {
        public MiniappApi {
            allowedCodes = allowedCodes == null
                    ? List.of()
                    : allowedCodes.stream()
                            .filter(code -> code != null && !code.isBlank())
                            .map(String::trim)
                            .toList();
        }

        public boolean allows(String miniAppCode) {
            return miniAppCode != null && (allowedCodes.isEmpty() || allowedCodes.contains(miniAppCode));
        }
    }
}

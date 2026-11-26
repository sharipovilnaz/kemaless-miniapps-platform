package com.kemaless.core.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kemaless.core.auth.config.PlatformAuthProperties;
import com.kemaless.core.auth.dto.AdminJwtPrincipal;
import com.kemaless.core.auth.dto.MiniAppJwtPrincipal;
import com.kemaless.core.auth.dto.RealmClientJwtPrincipal;
import com.kemaless.core.auth.entity.MiniAppSession;
import com.kemaless.core.auth.entity.PlatformUser;
import com.kemaless.core.auth.enums.GlobalRole;
import com.kemaless.core.auth.enums.MiniAppSessionStatus;
import com.kemaless.core.auth.enums.PlatformUserStatus;
import com.kemaless.core.auth.exception.AuthException;
import com.kemaless.core.enums.ClientStatus;
import com.kemaless.core.model.Realm;
import com.kemaless.core.model.RealmClient;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServicesSecurityTest {

    private static final String VALID_SECRET = "test-platform-secret-key-with-at-least-32-characters";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void adminJwtServiceIssuesAndValidatesToken() {
        AdminJwtService service = new AdminJwtService(props(VALID_SECRET, 900), objectMapper);
        PlatformUser user = PlatformUser.builder()
                .id(UUID.randomUUID())
                .username("root_admin")
                .passwordHash("hash")
                .status(PlatformUserStatus.ACTIVE)
                .globalRole(GlobalRole.ROOT)
                .build();

        String token = service.createAccessToken(user, null);
        AdminJwtPrincipal principal = service.validateAccessToken(token);

        assertThat(principal.userId()).isEqualTo(user.getId());
        assertThat(principal.username()).isEqualTo("root_admin");
        assertThat(principal.globalRole()).isEqualTo(GlobalRole.ROOT);
        assertThat(principal.realmCode()).isNull();
    }

    @Test
    void jwtRejectsTamperedSignatureAndExpiredTokenAndCrossTypeSubstitution() {
        AdminJwtService adminJwtService = new AdminJwtService(props(VALID_SECRET, 900), objectMapper);
        MiniAppJwtService miniAppJwtService = new MiniAppJwtService(props(VALID_SECRET, 900), objectMapper);
        HostServiceJwtService hostJwtService = new HostServiceJwtService(props(VALID_SECRET, 900), objectMapper);

        Realm realm = Realm.builder().code("bank").name("Bank").build();
        RealmClient client = RealmClient.builder().realm(realm).clientId("default-bank").status(ClientStatus.ACTIVE).build();
        MiniAppSession session = new MiniAppSession();
        session.setId(UUID.randomUUID());
        session.setRealm(realm);
        session.setMiniAppCode("payment-service");
        session.setExternalUserId("user-1");
        session.setRealmRoles("[\"USER\"]");
        session.setStatus(MiniAppSessionStatus.ACTIVE);

        String miniAppToken = miniAppJwtService.createAccessToken(session);
        String hostToken = hostJwtService.createAccessToken(client);

        MiniAppJwtPrincipal miniAppPrincipal = miniAppJwtService.validateAccessToken(miniAppToken);
        assertThat(miniAppPrincipal.realmCode()).isEqualTo("bank");
        assertThat(miniAppPrincipal.realmRoles()).containsExactly("USER");

        RealmClientJwtPrincipal hostPrincipal = hostJwtService.validateAccessToken(hostToken);
        assertThat(hostPrincipal.clientId()).isEqualTo("default-bank");
        assertThat(hostPrincipal.realmCode()).isEqualTo("bank");

        // Защита от подмены типов токенов (cross-type token confusion)
        assertThatThrownBy(() -> adminJwtService.validateAccessToken(miniAppToken))
                .isInstanceOf(AuthException.class);
        assertThatThrownBy(() -> hostJwtService.validateAccessToken(miniAppToken))
                .isInstanceOf(AuthException.class);
        assertThatThrownBy(() -> miniAppJwtService.validateAccessToken(hostToken))
                .isInstanceOf(AuthException.class);

        // Подделка подписи
        String tampered = miniAppToken.substring(0, miniAppToken.length() - 2) + "aa";
        assertThatThrownBy(() -> miniAppJwtService.validateAccessToken(tampered))
                .isInstanceOf(AuthException.class);

        // Защита от alg=none без подписи
        String algNoneHeader = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = miniAppToken.split("\\.")[1];
        assertThatThrownBy(() -> miniAppJwtService.validateAccessToken(algNoneHeader + "." + payload + "."))
                .isInstanceOf(AuthException.class);

        // Истёкший токен (TTL = -10 секунд)
        MiniAppJwtService expiredService = new MiniAppJwtService(props(VALID_SECRET, -10), objectMapper);
        String expiredToken = expiredService.createAccessToken(session);
        assertThatThrownBy(() -> miniAppJwtService.validateAccessToken(expiredToken))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void jwtRejectsMalformedInputsWithAuthExceptionAndShortSecretWithIllegalState() {
        AdminJwtService adminJwtService = new AdminJwtService(props(VALID_SECRET, 900), objectMapper);
        assertThatThrownBy(() -> adminJwtService.validateAccessToken(null))
                .isInstanceOf(AuthException.class);
        assertThatThrownBy(() -> adminJwtService.validateAccessToken("not-a-jwt"))
                .isInstanceOf(AuthException.class);

        TokenHashService weakHashService = new TokenHashService(props("short-secret", 900));
        assertThatThrownBy(() -> weakHashService.hash("raw-token"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }

    @Test
    void hostServiceTokenWithMissingClaimsIsRejectedAsAuthException() {
        HostServiceJwtService hostJwtService = new HostServiceJwtService(props(VALID_SECRET, 900), objectMapper);
        AdminJwtService adminJwtService = new AdminJwtService(props(VALID_SECRET, 900), objectMapper);
        PlatformUser user = PlatformUser.builder()
                .id(UUID.randomUUID())
                .username("admin")
                .passwordHash("hash")
                .status(PlatformUserStatus.ACTIVE)
                .globalRole(GlobalRole.PLATFORM_ADMIN)
                .build();
        String adminToken = adminJwtService.createAccessToken(user, "bank");

        assertThatThrownBy(() -> hostJwtService.validateAccessToken(adminToken))
                .isInstanceOf(AuthException.class);
        assertThat(List.of(adminToken)).hasSize(1);
    }

    private static PlatformAuthProperties props(String secret, long accessTtlSeconds) {
        return new PlatformAuthProperties(
                false,
                "admin",
                "password",
                secret,
                accessTtlSeconds,
                3600,
                60,
                3600
        );
    }
}

package com.kemaless.core.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kemaless.core.audit.domain.event.AuditEvent;
import com.kemaless.core.audit.service.AuditService;
import com.kemaless.core.auth.config.PlatformAuthProperties;
import com.kemaless.core.auth.dto.api.MiniAppRefreshRequest;
import com.kemaless.core.auth.dto.api.SsoExchangeRequest;
import com.kemaless.core.auth.dto.api.SsoExchangeResponse;
import com.kemaless.core.auth.dto.api.TokenResponse;
import com.kemaless.core.auth.entity.LaunchTicket;
import com.kemaless.core.auth.entity.MiniAppSession;
import com.kemaless.core.auth.enums.MiniAppSessionStatus;
import com.kemaless.core.auth.exception.AuthException;
import com.kemaless.core.auth.repository.LaunchTicketRepository;
import com.kemaless.core.auth.repository.MiniAppSessionRepository;
import com.kemaless.core.auth.repository.RealmMiniAppAuthRepository;
import com.kemaless.core.enums.RealmMiniAppStatus;
import com.kemaless.core.enums.RealmStatus;
import com.kemaless.core.model.Realm;
import com.kemaless.core.model.RealmMiniApp;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SsoExchangeServiceTest {

    private static final String SECRET = "test-platform-secret-key-with-at-least-32-characters";

    @Mock
    private LaunchTicketRepository launchTicketRepository;
    @Mock
    private MiniAppSessionRepository miniAppSessionRepository;
    @Mock
    private RealmMiniAppAuthRepository realmMiniAppRepository;
    @Mock
    private AuditService auditService;

    private TokenHashService tokenHashService;
    private SsoExchangeService ssoExchangeService;

    @BeforeEach
    void setUp() {
        PlatformAuthProperties properties = new PlatformAuthProperties(
                false, "admin", "password", SECRET, 900, 3600, 60, 3600);
        ObjectMapper objectMapper = new ObjectMapper();
        tokenHashService = new TokenHashService(properties);
        MiniAppJwtService miniAppJwtService = new MiniAppJwtService(properties, objectMapper);
        ssoExchangeService = new SsoExchangeService(
                properties,
                new SecureRandom(),
                objectMapper,
                tokenHashService,
                miniAppJwtService,
                launchTicketRepository,
                miniAppSessionRepository,
                realmMiniAppRepository,
                auditService
        );
    }

    @Test
    void exchangesValidTicketOnceAndMarksItUsed() {
        LaunchTicket ticket = validTicket();
        RealmMiniApp published = publishedMiniApp(ticket.getRealm());
        String rawTicket = "raw-one-time-ticket";
        when(launchTicketRepository.findByTicketHash(tokenHashService.hash(rawTicket)))
                .thenReturn(Optional.of(ticket));
        when(realmMiniAppRepository.findByRealmCodeAndMiniAppCode("bank", "payment-service"))
                .thenReturn(Optional.of(published));
        when(miniAppSessionRepository.save(any(MiniAppSession.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        SsoExchangeResponse response = ssoExchangeService.exchange(
                "bank",
                new SsoExchangeRequest(rawTicket, "payment-service")
        );

        assertThat(ticket.getUsedAt()).isNotNull();
        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.user().externalUserId()).isEqualTo("user-1");
        assertThat(response.user().realmRoles()).containsExactly("USER");
        assertThat(response.context()).containsEntry("theme", "dark");
        verify(miniAppSessionRepository).save(any(MiniAppSession.class));
        verify(auditService).write(any(AuditEvent.class));

        // Повторный обмен того же тикета должен быть отклонён как одноразовый
        assertThatThrownBy(() -> ssoExchangeService.exchange(
                "bank",
                new SsoExchangeRequest(rawTicket, "payment-service")))
                .isInstanceOf(AuthException.class)
                .hasMessage("Invalid launch ticket");
    }

    @Test
    void rejectsExpiredTicketAndMismatchedRealmOrMiniApp() {
        LaunchTicket expired = validTicket();
        expired.setExpiresAt(Instant.now().minusSeconds(5));
        when(launchTicketRepository.findByTicketHash(tokenHashService.hash("expired-ticket")))
                .thenReturn(Optional.of(expired));
        when(realmMiniAppRepository.findByRealmCodeAndMiniAppCode("bank", "payment-service"))
                .thenReturn(Optional.of(publishedMiniApp(expired.getRealm())));

        assertThatThrownBy(() -> ssoExchangeService.exchange(
                "bank",
                new SsoExchangeRequest("expired-ticket", "payment-service")))
                .isInstanceOf(AuthException.class)
                .hasMessage("Invalid launch ticket");
        verify(miniAppSessionRepository, never()).save(any());

        LaunchTicket otherRealmTicket = validTicket();
        when(launchTicketRepository.findByTicketHash(tokenHashService.hash("cross-realm")))
                .thenReturn(Optional.of(otherRealmTicket));
        assertThatThrownBy(() -> ssoExchangeService.exchange(
                "other-realm",
                new SsoExchangeRequest("cross-realm", "payment-service")))
                .isInstanceOf(AuthException.class)
                .hasMessage("Invalid launch ticket");
    }

    @Test
    void rotatesValidRefreshTokenAndMarksExpiredSessionAsExpired() {
        Realm activeRealm = Realm.builder().code("bank").status(RealmStatus.ACTIVE).build();
        MiniAppSession activeSession = new MiniAppSession();
        activeSession.setId(UUID.randomUUID());
        activeSession.setRealm(activeRealm);
        activeSession.setRealmMiniAppId(UUID.randomUUID());
        activeSession.setMiniAppCode("payment-service");
        activeSession.setExternalUserId("user-1");
        activeSession.setRealmRoles("[\"USER\"]");
        activeSession.setRefreshTokenHash(tokenHashService.hash("rt-1"));
        activeSession.setStatus(MiniAppSessionStatus.ACTIVE);
        activeSession.setExpiresAt(Instant.now().plusSeconds(300));

        when(miniAppSessionRepository.findByRefreshTokenHash(tokenHashService.hash("rt-1")))
                .thenReturn(Optional.of(activeSession));
        when(realmMiniAppRepository.findByRealmCodeAndMiniAppCode("bank", "payment-service"))
                .thenReturn(Optional.of(publishedMiniApp(activeRealm)));

        TokenResponse refreshed = ssoExchangeService.refresh("bank", new MiniAppRefreshRequest("rt-1"));
        assertThat(refreshed.accessToken()).isNotBlank();
        assertThat(refreshed.refreshToken()).isNotBlank().isNotEqualTo("rt-1");
        assertThat(activeSession.getRefreshTokenHash()).isEqualTo(tokenHashService.hash(refreshed.refreshToken()));

        activeSession.setExpiresAt(Instant.now().minusSeconds(1));
        when(miniAppSessionRepository.findByRefreshTokenHash(tokenHashService.hash("rt-expired")))
                .thenReturn(Optional.of(activeSession));

        assertThatThrownBy(() -> ssoExchangeService.refresh("bank", new MiniAppRefreshRequest("rt-expired")))
                .isInstanceOf(AuthException.class)
                .hasMessage("Invalid refresh token");
        assertThat(activeSession.getStatus()).isEqualTo(MiniAppSessionStatus.EXPIRED);
    }

    private static LaunchTicket validTicket() {
        Realm realm = Realm.builder().code("bank").status(RealmStatus.ACTIVE).build();
        LaunchTicket ticket = new LaunchTicket();
        ticket.setId(UUID.randomUUID());
        ticket.setRealm(realm);
        ticket.setRealmMiniAppId(UUID.randomUUID());
        ticket.setMiniAppCode("payment-service");
        ticket.setExternalUserId("user-1");
        ticket.setEmail("user@example.com");
        ticket.setRealmRoles("[\"USER\"]");
        ticket.setUserContext("{\"externalUserId\":\"user-1\",\"email\":\"user@example.com\",\"realmRoles\":[\"USER\"],\"attributes\":{\"segment\":\"vip\"}}");
        ticket.setLaunchContext("{\"context\":{\"theme\":\"dark\"}}");
        ticket.setExpiresAt(Instant.now().plusSeconds(60));
        return ticket;
    }

    private static RealmMiniApp publishedMiniApp(Realm realm) {
        return RealmMiniApp.builder()
                .id(UUID.randomUUID())
                .realm(realm)
                .status(RealmMiniAppStatus.PUBLISHED)
                .entryUrl("http://localhost:8091/start")
                .build();
    }
}

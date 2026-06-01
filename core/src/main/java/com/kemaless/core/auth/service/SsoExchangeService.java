package com.kemaless.core.auth.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kemaless.core.audit.domain.AuditAction;
import com.kemaless.core.audit.domain.event.AuditEvent;
import com.kemaless.core.audit.service.AuditService;
import com.kemaless.core.auth.config.PlatformAuthProperties;
import com.kemaless.core.auth.dto.AccessContext;
import com.kemaless.core.auth.dto.api.MiniAppRefreshRequest;
import com.kemaless.core.auth.dto.api.SsoExchangeRequest;
import com.kemaless.core.auth.dto.api.SsoExchangeResponse;
import com.kemaless.core.auth.dto.api.TokenResponse;
import com.kemaless.core.auth.entity.LaunchTicket;
import com.kemaless.core.auth.entity.MiniAppSession;
import com.kemaless.core.auth.enums.MiniAppSessionStatus;
import com.kemaless.core.enums.RealmStatus;
import com.kemaless.core.auth.exception.AuthException;
import com.kemaless.core.auth.repository.LaunchTicketRepository;
import com.kemaless.core.auth.repository.MiniAppSessionRepository;
import com.kemaless.core.auth.repository.RealmMiniAppAuthRepository;
import com.kemaless.core.enums.RealmMiniAppStatus;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SsoExchangeService {

    private static final int REFRESH_TOKEN_BYTES = 64;
    private static final String INVALID_TICKET_MESSAGE = "Invalid launch ticket";
    private static final String INVALID_REFRESH_TOKEN_MESSAGE = "Invalid refresh token";

    private final PlatformAuthProperties properties;
    private final SecureRandom secureRandom;
    private final ObjectMapper objectMapper;
    private final TokenHashService tokenHashService;
    private final MiniAppJwtService miniAppJwtService;
    private final LaunchTicketRepository launchTicketRepository;
    private final MiniAppSessionRepository miniAppSessionRepository;
    private final RealmMiniAppAuthRepository realmMiniAppRepository;
    private final AuditService auditService;

    @Transactional
    public SsoExchangeResponse exchange(String realmCode, SsoExchangeRequest request) {
        LaunchTicket ticket = null;
        try {
            ticket = launchTicketRepository.findByTicketHash(tokenHashService.hash(request.ticket()))
                    .orElseThrow(() -> new AuthException(INVALID_TICKET_MESSAGE));
            if (!ticket.getRealm().getCode().equals(realmCode)) {
                throw new AuthException(INVALID_TICKET_MESSAGE);
            }
            if (ticket.getRealm().getStatus() != RealmStatus.ACTIVE) {
                throw new AuthException(INVALID_TICKET_MESSAGE);
            }
            if (!ticket.getMiniAppCode().equals(request.miniAppCode())) {
                throw new AuthException(INVALID_TICKET_MESSAGE);
            }
            if (!isMiniAppPublished(realmCode, request.miniAppCode())) {
                throw new AuthException(INVALID_TICKET_MESSAGE);
            }
            if (ticket.getUsedAt() != null || !ticket.getExpiresAt().isAfter(Instant.now())) {
                throw new AuthException(INVALID_TICKET_MESSAGE);
            }

            ticket.setUsedAt(Instant.now());
            String refreshToken = generateRefreshToken();
            MiniAppSession session = new MiniAppSession();
            session.setRealm(ticket.getRealm());
            session.setRealmMiniAppId(ticket.getRealmMiniAppId());
            session.setMiniAppCode(ticket.getMiniAppCode());
            session.setExternalUserId(ticket.getExternalUserId());
            session.setEmail(ticket.getEmail());
            session.setRealmRoles(ticket.getRealmRoles());
            session.setRefreshTokenHash(tokenHashService.hash(refreshToken));
            session.setStatus(MiniAppSessionStatus.ACTIVE);
            session.setExpiresAt(Instant.now().plusSeconds(properties.miniappRefreshTokenTtlSeconds()));
            miniAppSessionRepository.save(session);

            String accessToken = miniAppJwtService.createAccessToken(session);
            return auditSuccessfulExchange(realmCode, ticket, accessToken, refreshToken);
        } catch (AuthException exception) {
            auditFailedExchange(realmCode, request.miniAppCode(), ticket, exception.getMessage());
            throw exception;
        }
    }

    @Transactional
    public TokenResponse refresh(String realmCode, MiniAppRefreshRequest request) {
        MiniAppSession session = miniAppSessionRepository.findByRefreshTokenHash(tokenHashService.hash(request.refreshToken()))
                .orElseThrow(() -> new AuthException(INVALID_REFRESH_TOKEN_MESSAGE));
        if (!session.getRealm().getCode().equals(realmCode)) {
            throw new AuthException(INVALID_REFRESH_TOKEN_MESSAGE);
        }
        if (session.getRealm().getStatus() != RealmStatus.ACTIVE) {
            throw new AuthException(INVALID_REFRESH_TOKEN_MESSAGE);
        }
        if (!isMiniAppPublished(realmCode, session.getMiniAppCode())) {
            throw new AuthException(INVALID_REFRESH_TOKEN_MESSAGE);
        }
        if (session.getStatus() != MiniAppSessionStatus.ACTIVE || !session.getExpiresAt().isAfter(Instant.now())) {
            expireIfNeeded(session);
            throw new AuthException(INVALID_REFRESH_TOKEN_MESSAGE);
        }
        String refreshToken = generateRefreshToken();
        session.setRefreshTokenHash(tokenHashService.hash(refreshToken));
        session.setExpiresAt(Instant.now().plusSeconds(properties.miniappRefreshTokenTtlSeconds()));
        String accessToken = miniAppJwtService.createAccessToken(session);
        return TokenResponse.bearer(accessToken, refreshToken, properties.accessTokenTtlSeconds());
    }

    private AccessContext readUserContext(String userContext) {
        try {
            return objectMapper.readValue(userContext, AccessContext.class);
        } catch (Exception exception) {
            throw new AuthException(INVALID_TICKET_MESSAGE);
        }
    }

    private Map<String, Object> readLaunchContext(String launchContext) {
        try {
            return objectMapper.readValue(launchContext, new TypeReference<>() {
            });
        } catch (Exception exception) {
            throw new AuthException(INVALID_TICKET_MESSAGE);
        }
    }

    private List<String> readRealmRoles(String realmRoles) {
        try {
            return objectMapper.readValue(realmRoles, new TypeReference<>() {
            });
        } catch (Exception exception) {
            throw new AuthException(INVALID_TICKET_MESSAGE);
        }
    }

    private Map<String, Object> readMiniAppContext(String launchContext) {
        Object context = readLaunchContext(launchContext).get("context");
        return context instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private boolean isMiniAppPublished(String realmCode, String miniAppCode) {
        return realmMiniAppRepository.findByRealmCodeAndMiniAppCode(realmCode, miniAppCode)
                .filter(miniApp -> miniApp.getStatus() == RealmMiniAppStatus.PUBLISHED)
                .isPresent();
    }

    @Transactional
    protected SsoExchangeResponse auditSuccessfulExchange(
            String realmCode,
            LaunchTicket ticket,
            String accessToken,
            String refreshToken
    ) {
        auditService.write(AuditEvent.builder()
                .actorType("MINIAPP")
                .actorId(ticket.getMiniAppCode())
                .realmCode(realmCode)
                .action(AuditAction.SSO_EXCHANGE_SUCCESS.name())
                .resourceType("REALM_MINIAPP")
                .resourceId(ticket.getMiniAppCode())
                .status("SUCCESS")
                .metadata(Map.of(
                        "miniAppCode", ticket.getMiniAppCode(),
                        "externalUserId", ticket.getExternalUserId() == null ? "" : ticket.getExternalUserId()
                ))
                .build());
        AccessContext accessContext = readUserContext(ticket.getUserContext());
        return SsoExchangeResponse.bearer(
                accessToken,
                refreshToken,
                properties.accessTokenTtlSeconds(),
                new SsoExchangeResponse.User(
                        ticket.getExternalUserId(),
                        ticket.getEmail(),
                        readRealmRoles(ticket.getRealmRoles()),
                        accessContext.attributes() == null ? Map.of() : accessContext.attributes()
                ),
                new SsoExchangeResponse.Realm(ticket.getRealm().getCode()),
                new SsoExchangeResponse.MiniApp(ticket.getMiniAppCode()),
                readMiniAppContext(ticket.getLaunchContext())
        );
    }

    private void auditFailedExchange(String realmCode, String miniAppCode, LaunchTicket ticket, String reason) {
        String resolvedMiniAppCode = ticket != null ? ticket.getMiniAppCode() : miniAppCode;
        auditService.write(AuditEvent.builder()
                .actorType("MINIAPP")
                .actorId(resolvedMiniAppCode)
                .realmCode(realmCode)
                .action(AuditAction.SSO_EXCHANGE_FAILED.name())
                .resourceType("REALM_MINIAPP")
                .resourceId(resolvedMiniAppCode)
                .status(reason)
                .metadata(Map.of(
                        "miniAppCode", resolvedMiniAppCode == null ? "" : resolvedMiniAppCode,
                        "reason", reason == null ? INVALID_TICKET_MESSAGE : reason
                ))
                .build());
    }

    private void expireIfNeeded(MiniAppSession session) {
        if (session.getStatus() == MiniAppSessionStatus.ACTIVE && !session.getExpiresAt().isAfter(Instant.now())) {
            session.setStatus(MiniAppSessionStatus.EXPIRED);
        }
    }

    private String generateRefreshToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

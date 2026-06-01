package com.kemaless.core.auth.service;

import com.kemaless.core.auth.dto.MiniAppJwtPrincipal;
import com.kemaless.core.auth.dto.api.TokenIntrospectionResponse;
import com.kemaless.core.auth.entity.MiniAppSession;
import com.kemaless.core.auth.enums.MiniAppSessionStatus;
import com.kemaless.core.enums.RealmStatus;
import com.kemaless.core.auth.repository.MiniAppSessionRepository;
import com.kemaless.core.auth.repository.RealmMiniAppAuthRepository;
import com.kemaless.core.enums.RealmMiniAppStatus;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TokenIntrospectionService {

    private final MiniAppJwtService miniAppJwtService;
    private final MiniAppSessionRepository miniAppSessionRepository;
    private final RealmMiniAppAuthRepository realmMiniAppRepository;

    @Transactional
    public TokenIntrospectionResponse introspect(String realmCode, String token) {
        MiniAppJwtPrincipal principal;
        try {
            principal = miniAppJwtService.validateAccessToken(token);
        } catch (RuntimeException exception) {
            return TokenIntrospectionResponse.inactive();
        }
        if (!principal.realmCode().equals(realmCode)) {
            return TokenIntrospectionResponse.inactive();
        }
        MiniAppSession session = miniAppSessionRepository.findById(principal.sessionId()).orElse(null);
        if (session == null || session.getStatus() != MiniAppSessionStatus.ACTIVE) {
            return TokenIntrospectionResponse.inactive();
        }
        if (session.getRealm().getStatus() != RealmStatus.ACTIVE) {
            return TokenIntrospectionResponse.inactive();
        }
        if (!isMiniAppPublished(principal.realmCode(), principal.miniAppCode())) {
            return TokenIntrospectionResponse.inactive();
        }
        if (!session.getExpiresAt().isAfter(Instant.now())) {
            session.setStatus(MiniAppSessionStatus.EXPIRED);
            return TokenIntrospectionResponse.inactive();
        }
        return TokenIntrospectionResponse.active(
                principal.realmCode(),
                principal.miniAppCode(),
                principal.externalUserId(),
                principal.realmRoles(),
                Instant.ofEpochSecond(principal.expiresAt())
        );
    }

    private boolean isMiniAppPublished(String realmCode, String miniAppCode) {
        return realmMiniAppRepository.findByRealmCodeAndMiniAppCode(realmCode, miniAppCode)
                .filter(miniApp -> miniApp.getStatus() == RealmMiniAppStatus.PUBLISHED)
                .isPresent();
    }
}

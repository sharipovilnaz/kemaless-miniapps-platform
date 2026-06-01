package com.kemaless.core.auth.service;

import com.kemaless.core.auth.dto.api.HostSessionLogoutRequest;
import com.kemaless.core.auth.dto.api.SessionLogoutResponse;
import com.kemaless.core.auth.entity.MiniAppSession;
import com.kemaless.core.model.Realm;
import com.kemaless.core.auth.enums.MiniAppSessionStatus;
import com.kemaless.core.auth.exception.AuthException;
import com.kemaless.core.auth.repository.MiniAppSessionRepository;
import com.kemaless.core.repository.RealmRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MiniAppSessionService {

    private final RealmRepository realmRepository;
    private final MiniAppSessionRepository miniAppSessionRepository;

    @Transactional
    public SessionLogoutResponse logoutByHost(String realmCode, HostSessionLogoutRequest request) {
        Realm realm = realmRepository.findByCode(realmCode)
                .orElseThrow(() -> new AuthException("Realm not found"));
        List<MiniAppSession> sessions = miniAppSessionRepository.findByRealmAndExternalUserIdAndStatus(
                realm,
                request.externalUserId(),
                MiniAppSessionStatus.ACTIVE
        );
        Instant revokedAt = Instant.now();
        for (MiniAppSession session : sessions) {
            session.setStatus(MiniAppSessionStatus.REVOKED);
            session.setRevokedAt(revokedAt);
        }
        return new SessionLogoutResponse(sessions.size());
    }
}

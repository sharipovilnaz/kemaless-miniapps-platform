package com.kemaless.core.auth.repository;

import com.kemaless.core.auth.entity.MiniAppSession;
import com.kemaless.core.model.Realm;
import com.kemaless.core.auth.enums.MiniAppSessionStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface MiniAppSessionRepository extends JpaRepository<MiniAppSession, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<MiniAppSession> findByRefreshTokenHash(String refreshTokenHash);

    List<MiniAppSession> findByRealmAndExternalUserIdAndStatus(
            Realm realm,
            String externalUserId,
            MiniAppSessionStatus status
    );
}

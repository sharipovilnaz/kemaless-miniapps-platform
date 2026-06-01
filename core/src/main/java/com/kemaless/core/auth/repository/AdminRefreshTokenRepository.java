package com.kemaless.core.auth.repository;

import com.kemaless.core.auth.entity.AdminRefreshToken;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface AdminRefreshTokenRepository extends JpaRepository<AdminRefreshToken, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AdminRefreshToken> findByTokenHash(String tokenHash);
}

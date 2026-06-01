package com.kemaless.core.ai.repository;

import com.kemaless.core.model.AiChatSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AiChatSessionRepository extends JpaRepository<AiChatSessionEntity, UUID> {

    Optional<AiChatSessionEntity> findByIdAndUserId(UUID id, UUID userId);
}

package com.kemaless.core.ai.repository;

import com.kemaless.core.model.AiChatMessageEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AiChatMessageRepository extends JpaRepository<AiChatMessageEntity, UUID> {

    List<AiChatMessageEntity> findBySession_IdOrderByCreatedAtDesc(UUID sessionId, Pageable pageable);
}

package com.kemaless.core.ai.memory;

import com.kemaless.core.ai.config.PlatformAiProperties;
import com.kemaless.core.ai.repository.AiChatMessageRepository;
import com.kemaless.core.ai.repository.AiChatSessionRepository;
import com.kemaless.core.auth.dto.AdminJwtPrincipal;
import com.kemaless.core.model.AiChatMessageEntity;
import com.kemaless.core.model.AiChatSessionEntity;
import com.kemaless.core.model.AiRole;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class DatabaseChatMemory implements ChatMemory {

    private static final int TITLE_MAX_LENGTH = 120;

    private final AiChatSessionRepository sessionRepository;
    private final AiChatMessageRepository messageRepository;
    private final int maxHistoryMessages;

    public DatabaseChatMemory(
            AiChatSessionRepository sessionRepository,
            AiChatMessageRepository messageRepository,
            PlatformAiProperties platformAiProperties
    ) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.maxHistoryMessages = platformAiProperties.getMemory().getMaxHistoryMessages();
    }

    @Override
    @Transactional
    public void add(String conversationId, List<Message> messages) {
        UUID sessionId = parseConversationId(conversationId);
        AiChatSessionEntity session = getAccessibleSession(sessionId)
                .orElseGet(() -> createSession(sessionId, messages));

        List<AiChatMessageEntity> entities = messages.stream()
                .map(message -> toEntity(session, message))
                .toList();

        if (!entities.isEmpty()) {
            messageRepository.saveAll(entities);
        }

        session.setUpdatedAt(Instant.now());
        sessionRepository.save(session);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Message> get(String conversationId) {
        UUID sessionId = parseConversationId(conversationId);
        if (getAccessibleSession(sessionId).isEmpty()) {
            return List.of();
        }

        List<AiChatMessageEntity> storedMessages = new ArrayList<>(
                messageRepository.findBySession_IdOrderByCreatedAtDesc(
                        sessionId,
                        PageRequest.of(0, maxHistoryMessages)
                )
        );
        Collections.reverse(storedMessages);

        return storedMessages.stream()
                .map(this::toMessage)
                .toList();
    }

    @Override
    @Transactional
    public void clear(String conversationId) {
        UUID sessionId = parseConversationId(conversationId);
        getAccessibleSession(sessionId).ifPresent(sessionRepository::delete);
    }

    private AiChatSessionEntity createSession(UUID sessionId, List<Message> messages) {
        UUID currentUserId = currentUserId()
                .orElseThrow(() -> new AccessDeniedException("Требуется аутентифицированный администратор для создания AI-сессии"));

        AiChatSessionEntity session = AiChatSessionEntity.builder()
                .id(sessionId)
                .userId(currentUserId)
                .title(extractTitle(messages))
                .status("ACTIVE")
                .build();

        return sessionRepository.save(session);
    }

    private Optional<AiChatSessionEntity> getAccessibleSession(UUID sessionId) {
        Optional<AiChatSessionEntity> session = sessionRepository.findById(sessionId);
        if (session.isEmpty()) {
            return Optional.empty();
        }

        Optional<UUID> currentUserId = currentUserId();
        if (currentUserId.isPresent() && !currentUserId.get().equals(session.get().getUserId())) {
            throw new AccessDeniedException("Нельзя использовать чужую AI-сессию");
        }

        return session;
    }

    private String extractTitle(List<Message> messages) {
        return messages.stream()
                .filter(message -> message.getMessageType() == MessageType.USER)
                .map(Message::getText)
                .filter(text -> text != null && !text.isBlank())
                .findFirst()
                .map(this::truncateTitle)
                .orElse("Новая AI-сессия");
    }

    private String truncateTitle(String text) {
        String normalized = text.trim().replaceAll("\\s+", " ");
        if (normalized.length() <= TITLE_MAX_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, TITLE_MAX_LENGTH - 3) + "...";
    }

    private AiChatMessageEntity toEntity(AiChatSessionEntity session, Message message) {
        AiRole role = mapRole(message.getMessageType());
        int estimatedTokens = estimateTokens(message.getText());

        return AiChatMessageEntity.builder()
                .session(session)
                .role(role)
                .content(message.getText())
                .promptTokens(role == AiRole.ASSISTANT ? 0 : estimatedTokens)
                .completionTokens(role == AiRole.ASSISTANT ? estimatedTokens : 0)
                .build();
    }

    private Message toMessage(AiChatMessageEntity entity) {
        String content = entity.getContent() == null ? "" : entity.getContent();
        return switch (entity.getRole()) {
            case USER -> new UserMessage(content);
            case ASSISTANT -> new AssistantMessage(content);
            case TOOL, SYSTEM -> new SystemMessage(content);
        };
    }

    private AiRole mapRole(MessageType messageType) {
        return switch (messageType) {
            case USER -> AiRole.USER;
            case ASSISTANT -> AiRole.ASSISTANT;
            case SYSTEM -> AiRole.SYSTEM;
            case TOOL -> AiRole.TOOL;
        };
    }

    private int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(text.length() / 4.0d));
    }

    private UUID parseConversationId(String conversationId) {
        try {
            return UUID.fromString(conversationId);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("sessionId должен быть валидным UUID", exception);
        }
    }

    private Optional<UUID> currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AdminJwtPrincipal principal)) {
            return Optional.empty();
        }
        return Optional.ofNullable(principal.userId());
    }
}

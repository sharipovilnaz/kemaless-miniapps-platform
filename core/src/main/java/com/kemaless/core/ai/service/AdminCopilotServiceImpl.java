package com.kemaless.core.ai.service;

import com.kemaless.core.ai.dto.api.AdminCopilotAttachmentRequest;
import com.kemaless.core.ai.exception.AiUnavailableException;
import com.kemaless.core.ai.repository.AiChatMessageRepository;
import com.kemaless.core.auth.dto.AdminJwtPrincipal;
import com.kemaless.core.model.AiRole;
import com.openai.errors.PermissionDeniedException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AdminCopilotServiceImpl {

    private final ObjectProvider<ChatClient> chatClient;
    private final AiChatMessageRepository aiChatMessageRepository;
    private final AdminCopilotMediaService adminCopilotMediaService;

    public AdminCopilotServiceImpl(
            ObjectProvider<ChatClient> chatClient,
            AiChatMessageRepository aiChatMessageRepository,
            AdminCopilotMediaService adminCopilotMediaService
    ) {
        this.chatClient = chatClient;
        this.aiChatMessageRepository = aiChatMessageRepository;
        this.adminCopilotMediaService = adminCopilotMediaService;
    }

    public String askCopilot(
            AdminJwtPrincipal principal,
            UUID sessionId,
            String userMessage,
            List<AdminCopilotAttachmentRequest> attachments
    ) {
        ChatClient client = chatClient.getIfAvailable();
        if (client == null) {
            throw new AiUnavailableException("Copilot недоступен: ChatClient не создан (проверьте platform.ai.copilot.enabled и LLM).");
        }

        UserMessage message = UserMessage.builder()
                .text(userMessage)
                .media(adminCopilotMediaService.toMedia(principal, attachments))
                .build();

        ChatResponse response;
        try {
            response = client.prompt()
                    .messages(message)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId.toString()))
                    .call()
                    .chatResponse();
        } catch (PermissionDeniedException exception) {
            throw new AiUnavailableException(
                    "OpenAI недоступен для текущего региона или сетевого окружения. " +
                    "Нужен поддерживаемый endpoint/base-url либо совместимый провайдер вроде Ollama/OpenAI-compatible.");
        }

        updateStoredUsage(sessionId, response.getMetadata().getUsage());
        return response.getResult().getOutput().getText();
    }

    @Transactional
    void updateStoredUsage(UUID sessionId, Usage usage) {
        if (usage == null) {
            return;
        }

        List<com.kemaless.core.model.AiChatMessageEntity> recentMessages =
                aiChatMessageRepository.findBySession_IdOrderByCreatedAtDesc(sessionId, PageRequest.of(0, 4));

        recentMessages.stream()
                .filter(message -> message.getRole() == AiRole.ASSISTANT)
                .findFirst()
                .ifPresent(message -> message.setCompletionTokens(orZero(usage.getCompletionTokens())));

        recentMessages.stream()
                .filter(message -> message.getRole() == AiRole.USER)
                .findFirst()
                .ifPresent(message -> message.setPromptTokens(orZero(usage.getPromptTokens())));

        aiChatMessageRepository.saveAll(recentMessages);
    }

    private int orZero(Integer value) {
        return value == null ? 0 : value;
    }

}

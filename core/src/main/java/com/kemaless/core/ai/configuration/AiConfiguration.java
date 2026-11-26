package com.kemaless.core.ai.configuration;

import com.kemaless.core.ai.dto.tool.AuditDto;
import com.kemaless.core.ai.dto.tool.AuditRequest;
import com.kemaless.core.audit.repository.AuditEventRepository;
import com.kemaless.core.security.AdminPermissionEvaluator;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@Configuration
@ConditionalOnProperty(prefix = "platform.ai", name = "copilot.enabled", havingValue = "true")
public class AiConfiguration {

    @Bean
    public ChatClient chatClient(
            ChatModel chatModel,
            ChatMemory chatMemory,
            ObjectProvider<QuestionAnswerAdvisor> questionAnswerAdvisor) {
        var builder = ChatClient.builder(chatModel)
                .defaultSystem("Ты AI-ассистент администратора корпоративной платформы миниаппов. " +
                        "Твоя задача — анализировать логи, инциденты и настройки доступов. " +
                        "Если просят построить граф, возвращай ответ СТРОГО в формате Mermaid.js " +
                        "внутри блока кода ```mermaid ... ```. " +
                        "Не придумывай логи и не утверждай факты, которых нет в истории диалога или контексте. " +
                        "Если в запросе есть контекст из векторного поиска по миниаппам, опирайся на него.")
                ;

        List<org.springframework.ai.chat.client.advisor.api.Advisor> advisors = new ArrayList<>();
        advisors.add(MessageChatMemoryAdvisor.builder(chatMemory).build());
        questionAnswerAdvisor.ifAvailable(advisors::add);
        builder.defaultAdvisors(advisors.toArray(new org.springframework.ai.chat.client.advisor.api.Advisor[0]));

        return builder.build();
    }

    @Bean
    @Description("""
            Возвращает свежие audit-логи платформы по realmCode.
            Используй этот инструмент, когда нужно понять, почему миниапп не запустился, почему был отказ в доступе,
            почему произошёл SSO/launch failure или что случилось в конкретном realm за последние минуты.
            Всегда передавай realmCode. lookbackMinutes задаёт окно поиска в минутах назад от текущего момента,
            limit ограничивает количество событий (если не задан, будет использовано значение по умолчанию).
            Дополнительно можно сузить выборку по action и status.
            """)
    public Function<AuditRequest, List<AuditDto>> auditLogFetcherFunction(
            AuditEventRepository auditEventRepository,
            AdminPermissionEvaluator adminPermissionEvaluator) {
        return request -> {
            if (request == null || request.realmCode() == null || request.realmCode().isBlank()) {
                throw new IllegalArgumentException("realmCode is required");
            }
            String realmCode = request.realmCode().trim();
            // Ограничиваем чтение audit-логов правами текущего пользователя, чтобы не допускать меж-реалмовых утечек через LLM tool.
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (!adminPermissionEvaluator.canViewAudit(authentication, realmCode)) {
                throw new AccessDeniedException("Недостаточно прав для чтения аудита realm " + realmCode);
            }

            int lookbackMinutes = request.lookbackMinutes() == null ? 15 : Math.max(1, request.lookbackMinutes());
            int limit = request.limit() == null ? 20 : Math.min(Math.max(1, request.limit()), 50);
            Instant fromTime = Instant.now().minusSeconds((long) lookbackMinutes * 60);

            return auditEventRepository.findRecentEvents(
                            realmCode,
                            fromTime,
                            normalize(request.action()),
                            normalize(request.status()),
                            PageRequest.of(0, limit)
                    ).stream()
                    .map(event -> new AuditDto(
                            event.getId(),
                            event.getTimestamp(),
                            event.getActorType(),
                            event.getActorId(),
                            event.getRealmCode(),
                            event.getAction(),
                            event.getResourceType(),
                            event.getResourceId(),
                            event.getStatus(),
                            event.getIp(),
                            event.getUserAgent(),
                            event.getMetadata()
                    ))
                    .toList();
        };
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}

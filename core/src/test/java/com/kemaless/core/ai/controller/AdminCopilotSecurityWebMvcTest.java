package com.kemaless.core.ai.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kemaless.core.ai.config.AiAvailabilityRegistry;
import com.kemaless.core.ai.config.PlatformAiProperties;
import com.kemaless.core.ai.repository.AiChatMessageRepository;
import com.kemaless.core.ai.service.AdminCopilotMediaService;
import com.kemaless.core.ai.service.AdminCopilotServiceImpl;
import com.kemaless.core.auth.config.SecurityConfig;
import com.kemaless.core.auth.controller.handler.GlobalExceptionHandler;
import com.kemaless.core.auth.entity.PlatformUser;
import com.kemaless.core.auth.enums.GlobalRole;
import com.kemaless.core.auth.enums.PlatformUserStatus;
import com.kemaless.core.auth.repository.PlatformUserRepository;
import com.kemaless.core.auth.security.AdminJwtAuthenticationFilter;
import com.kemaless.core.auth.security.RealmClientJwtAuthenticationFilter;
import com.kemaless.core.auth.service.AdminJwtService;
import com.kemaless.core.auth.service.HostServiceJwtService;
import com.kemaless.core.auth.service.RealmClientTokenValidationService;
import com.kemaless.core.security.AdminPermissionEvaluator;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import io.minio.PutObjectArgs;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import okhttp3.Headers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminCopilotController.class)
@Import({
        SecurityConfig.class,
        AdminJwtAuthenticationFilter.class,
        RealmClientJwtAuthenticationFilter.class,
        AdminJwtService.class,
        HostServiceJwtService.class,
        AdminPermissionEvaluator.class,
        GlobalExceptionHandler.class,
        AdminCopilotMediaService.class,
        AdminCopilotServiceImpl.class,
        AdminCopilotSecurityWebMvcTest.CopilotTestConfig.class
})
@EnableConfigurationProperties(PlatformAiProperties.class)
@TestPropertySource(properties = {
        "platform.ai.media.minio.public-base-url=http://localhost:9000",
        "platform.ai.media.minio.bucket=ai-media-bucket"
})
class AdminCopilotSecurityWebMvcTest {

    @TestConfiguration
    static class CopilotTestConfig {
        @Bean
        AiAvailabilityRegistry aiAvailabilityRegistry() {
            AiAvailabilityRegistry registry = new AiAvailabilityRegistry();
            registry.setCopilot(true, "Copilot enabled for WebMvc test");
            registry.setMedia(true, "Media enabled for WebMvc test");
            return registry;
        }
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private AdminJwtService adminJwtService;

    @MockitoBean
    private RealmClientTokenValidationService realmClientTokenValidationService;
    @MockitoBean
    private PlatformUserRepository platformUserRepository;
    @MockitoBean
    private AiChatMessageRepository aiChatMessageRepository;
    @MockitoBean
    private MinioClient minioClient;
    @MockitoBean
    private ChatClient chatClient;

    private ChatClient.CallResponseSpec callResponseSpec;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        callResponseSpec = mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.messages(any(Message.class))).thenReturn(requestSpec);
        when(requestSpec.advisors(any(Consumer.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.chatResponse())
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("ok")))));
        when(minioClient.putObject(any(PutObjectArgs.class))).thenReturn(mock(ObjectWriteResponse.class));
        when(aiChatMessageRepository.findBySession_IdOrderByCreatedAtDesc(any(), any())).thenReturn(List.of());
    }

    @Test
    void copilotBlocksCrossUserAndCrossRealmAttachmentSubstitutionWhilePreservingOwnerFlow() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID sameRealmOtherId = UUID.randomUUID();
        UUID otherRealmAdminId = UUID.randomUUID();

        String ownerToken = adminJwtService.createAccessToken(
                PlatformUser.builder()
                        .id(ownerId)
                        .username("admin_bank_1")
                        .passwordHash("h")
                        .status(PlatformUserStatus.ACTIVE)
                        .globalRole(GlobalRole.PLATFORM_ADMIN)
                        .build(),
                "bank");
        String sameRealmOtherToken = adminJwtService.createAccessToken(
                PlatformUser.builder()
                        .id(sameRealmOtherId)
                        .username("admin_bank_2")
                        .passwordHash("h")
                        .status(PlatformUserStatus.ACTIVE)
                        .globalRole(GlobalRole.PLATFORM_AUDITOR)
                        .build(),
                "bank");
        String otherRealmToken = adminJwtService.createAccessToken(
                PlatformUser.builder()
                        .id(otherRealmAdminId)
                        .username("admin_other")
                        .passwordHash("h")
                        .status(PlatformUserStatus.ACTIVE)
                        .globalRole(GlobalRole.PLATFORM_ADMIN)
                        .build(),
                "fintech");

        // 1. Владелец загружает вложение через /api/admin/copilot/media (контракт frontend)
        String uploadJson = mockMvc.perform(multipart("/api/admin/copilot/media")
                        .file(new MockMultipartFile("file", "chart.png", "image/png", "png".getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode uploaded = objectMapper.readTree(uploadJson);
        String objectKey = uploaded.get("objectKey").asText();
        String url = uploaded.get("url").asText();
        assertThat(objectKey).startsWith("admin-copilot/realm-bank/" + ownerId + "/");

        // 2. Чужой администратор в том же или другом realm не может прочитать чужое вложение по objectKey или url
        String crossUserKeyPayload = objectMapper.writeValueAsString(Map.of(
                "sessionId", UUID.randomUUID(),
                "text", "analyze",
                "attachments", List.of(Map.of(
                        "objectKey", objectKey,
                        "fileName", "chart.png",
                        "mimeType", "image/png",
                        "fileType", "image"))));
        String crossUserUrlPayload = objectMapper.writeValueAsString(Map.of(
                "sessionId", UUID.randomUUID(),
                "text", "analyze",
                "attachments", List.of(Map.of(
                        "url", url,
                        "fileName", "chart.png",
                        "mimeType", "image/png",
                        "fileType", "image"))));

        mockMvc.perform(post("/api/admin/copilot/chat")
                        .header("Authorization", "Bearer " + sameRealmOtherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(crossUserKeyPayload))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("AccessDenied"));

        mockMvc.perform(post("/api/admin/copilot/chat")
                        .header("Authorization", "Bearer " + otherRealmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(crossUserUrlPayload))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("AccessDenied"));

        // Подмена url в паре с другим objectKey также отклоняется до обращения к S3/LLM
        String mismatchedPayload = objectMapper.writeValueAsString(Map.of(
                "sessionId", UUID.randomUUID(),
                "text", "analyze",
                "attachments", List.of(Map.of(
                        "objectKey", "admin-copilot/realm-fintech/" + otherRealmAdminId + "/" + UUID.randomUUID() + "/x.png",
                        "url", url,
                        "fileName", "chart.png",
                        "mimeType", "image/png",
                        "fileType", "image"))));
        mockMvc.perform(post("/api/admin/copilot/chat")
                        .header("Authorization", "Bearer " + otherRealmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mismatchedPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BadRequest"));

        verify(minioClient, never()).getObject(any(GetObjectArgs.class));
        verifyNoInteractions(chatClient);

        // 3. Исходный владелец с payload как во frontend (objectKey + url) успешно отправляет запрос
        when(minioClient.getObject(any(GetObjectArgs.class)))
                .thenReturn(new GetObjectResponse(
                        new Headers.Builder().build(),
                        "ai-media-bucket",
                        null,
                        objectKey,
                        new ByteArrayInputStream("png".getBytes(StandardCharsets.UTF_8))));

        String ownerPayload = objectMapper.writeValueAsString(Map.of(
                "sessionId", UUID.randomUUID(),
                "text", "analyze",
                "attachments", List.of(Map.of(
                        "objectKey", objectKey,
                        "url", url,
                        "fileName", "chart.png",
                        "mimeType", "image/png",
                        "fileType", "image"))));

        mockMvc.perform(post("/api/admin/copilot/chat")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ownerPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("ok"));

        verify(minioClient).getObject(any(GetObjectArgs.class));
        verify(callResponseSpec).chatResponse();
        assertThat(ChatMemory.CONVERSATION_ID).isNotBlank();
    }
}

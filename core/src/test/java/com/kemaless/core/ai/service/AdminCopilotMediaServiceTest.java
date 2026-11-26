package com.kemaless.core.ai.service;

import com.kemaless.core.ai.config.PlatformAiProperties;
import com.kemaless.core.ai.dto.api.AdminCopilotAttachmentRequest;
import com.kemaless.core.ai.dto.api.AdminCopilotMediaUploadResponse;
import com.kemaless.core.auth.dto.AdminJwtPrincipal;
import com.kemaless.core.auth.enums.GlobalRole;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import okhttp3.Headers;
import org.junit.jupiter.api.Test;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdminCopilotMediaServiceTest {

    @SuppressWarnings("unchecked")
    private final ObjectProvider<MinioClient> minioProvider = mock(ObjectProvider.class);

    @Test
    void rejectsExternalUrlsIpv6BypassesSpecialRangesAndPathTraversal() {
        PlatformAiProperties properties = configuredProperties("http://localhost:9000", "ai-media-bucket");
        AdminCopilotMediaService service = new AdminCopilotMediaService(minioProvider, properties);
        AdminJwtPrincipal owner = principal(UUID.randomUUID(), GlobalRole.PLATFORM_ADMIN, "bank");

        List<String> forbiddenUrls = List.of(
                "file:///etc/passwd",
                "http://127.0.0.1:8080/actuator/env",
                "http://169.254.169.254/latest/meta-data",
                "http://[::1]:9000/ai-media-bucket/admin-copilot/realm-bank/u/id/a.png",
                "http://[::ffff:127.0.0.1]:9000/ai-media-bucket/admin-copilot/realm-bank/u/id/a.png",
                "http://[::ffff:169.254.169.254]/latest/meta-data",
                "http://[fc00::1]/secret.png",
                "http://[fd12:3456::1]/ai-media-bucket/admin-copilot/realm-bank/u/id/a.png",
                "http://[fe80::1]/ai-media-bucket/admin-copilot/realm-bank/u/id/a.png",
                "http://0.0.0.1/ai-media-bucket/admin-copilot/realm-bank/u/id/a.png",
                "http://100.64.0.1/ai-media-bucket/admin-copilot/realm-bank/u/id/a.png",
                "http://198.18.0.1/ai-media-bucket/admin-copilot/realm-bank/u/id/a.png",
                "https://evil.example.com/redirect-to-internal",
                "http://localhost:9000@evil.com/ai-media-bucket/admin-copilot/realm-bank/u/id/a.png",
                "http://localhost:9000/other-bucket/admin-copilot/realm-bank/u/id/a.png",
                "http://localhost:9000/ai-media-bucket/../other-bucket/admin-copilot/realm-bank/u/id/a.png",
                "http://localhost:9000/ai-media-bucket/admin-copilot/%2e%2e/secret.png",
                "http://localhost:9000/ai-media-bucket/admin-copilot/realm-bank/u/id/a.png?redirect=http://127.0.0.1"
        );

        for (String url : forbiddenUrls) {
            assertThatThrownBy(() -> service.toMedia(owner, List.of(
                    new AdminCopilotAttachmentRequest(null, url, "file.png", "image/png", "image"))))
                    .as("Expected URL to be rejected: %s", url)
                    .isInstanceOf(IllegalArgumentException.class);
        }

        List<String> invalidObjectKeys = List.of(
                "../secret/file.txt",
                "admin-copilot/",
                "admin-copilot/../other/secret.txt",
                "admin-copilot/realm-bank/u1//id/file.txt",
                "admin-copilot/realm-bank/u1/id/file\\name.txt",
                "other-prefix/realm-bank/u1/id/file.txt"
        );
        for (String key : invalidObjectKeys) {
            assertThatThrownBy(() -> service.toMedia(owner, List.of(
                    new AdminCopilotAttachmentRequest(key, null, "file.txt", "text/plain", "file"))))
                    .as("Expected objectKey to be rejected: %s", key)
                    .isInstanceOf(IllegalArgumentException.class);
        }

        verifyNoInteractions(minioProvider);
    }

    @Test
    void uploadScopesObjectKeyByRealmAndUserAndReadsBackForOwner() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID rootId = UUID.randomUUID();
        AdminJwtPrincipal owner = principal(ownerId, GlobalRole.PLATFORM_ADMIN, "bank");
        AdminJwtPrincipal root = principal(rootId, GlobalRole.ROOT, null);

        MinioClient minioClient = mock(MinioClient.class);
        when(minioProvider.getIfAvailable()).thenReturn(minioClient);

        PlatformAiProperties properties = configuredProperties("http://localhost:9000", "ai-media-bucket");
        AdminCopilotMediaService service = new AdminCopilotMediaService(minioProvider, properties);

        AdminCopilotMediaUploadResponse uploaded = service.upload(
                owner,
                new MockMultipartFile("file", "photo.png", "image/png", "png-bytes".getBytes(StandardCharsets.UTF_8)));

        assertThat(uploaded.objectKey()).startsWith("admin-copilot/realm-bank/" + ownerId + "/");
        assertThat(uploaded.objectKey()).endsWith("/photo.png");
        assertThat(uploaded.url()).isEqualTo("http://localhost:9000/ai-media-bucket/" + uploaded.objectKey());
        verify(minioClient).putObject(any(PutObjectArgs.class));

        AdminCopilotMediaUploadResponse rootUploaded = service.upload(
                root,
                new MockMultipartFile("file", "..", "image/png", "png-bytes".getBytes(StandardCharsets.UTF_8)));
        assertThat(rootUploaded.objectKey()).startsWith("admin-copilot/global/" + rootId + "/");
        assertThat(rootUploaded.objectKey()).endsWith("/upload.bin");

        when(minioClient.getObject(any(GetObjectArgs.class)))
                .thenAnswer(invocation -> new GetObjectResponse(
                        new Headers.Builder().build(),
                        "ai-media-bucket",
                        null,
                        uploaded.objectKey(),
                        new ByteArrayInputStream("png-bytes".getBytes(StandardCharsets.UTF_8))));

        // Frontend отправляет одновременно objectKey и url, а также поддерживаются оба поля по отдельности
        List<Media> bothFields = service.toMedia(owner, List.of(
                new AdminCopilotAttachmentRequest(
                        uploaded.objectKey(), uploaded.url(), uploaded.fileName(), uploaded.mimeType(), uploaded.fileType())));
        List<Media> urlOnly = service.toMedia(owner, List.of(
                new AdminCopilotAttachmentRequest(null, uploaded.url(), "photo.png", "image/png", "image")));
        List<Media> keyOnly = service.toMedia(owner, List.of(
                new AdminCopilotAttachmentRequest(uploaded.objectKey(), null, "photo.png", "image/png", "image")));

        assertThat(bothFields).hasSize(1);
        assertThat(urlOnly).hasSize(1);
        assertThat(keyOnly).hasSize(1);
        assertThat(bothFields.getFirst().getDataAsByteArray()).isEqualTo("png-bytes".getBytes(StandardCharsets.UTF_8));
        verify(minioClient, times(3)).getObject(any(GetObjectArgs.class));
    }

    @Test
    void rejectsCrossUserCrossRealmMismatchedAndMalformedAttachmentOwnership() {
        UUID ownerId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        UUID uploadId = UUID.randomUUID();
        AdminJwtPrincipal sameRealmOtherUser = principal(otherUserId, GlobalRole.PLATFORM_ADMIN, "bank");
        AdminJwtPrincipal sameRealmAuditor = principal(otherUserId, GlobalRole.PLATFORM_AUDITOR, "bank");
        AdminJwtPrincipal otherRealmAdmin = principal(otherUserId, GlobalRole.PLATFORM_ADMIN, "fintech");
        AdminJwtPrincipal sameUserDifferentRealm = principal(ownerId, GlobalRole.PLATFORM_ADMIN, "fintech");
        AdminJwtPrincipal otherRoot = principal(UUID.randomUUID(), GlobalRole.ROOT, null);

        PlatformAiProperties properties = configuredProperties("http://localhost:9000", "ai-media-bucket");
        AdminCopilotMediaService service = new AdminCopilotMediaService(minioProvider, properties);

        String victimKey = "admin-copilot/realm-bank/" + ownerId + "/" + uploadId + "/secret.png";
        String victimUrl = "http://localhost:9000/ai-media-bucket/" + victimKey;
        String ownKeyForOtherRealm = "admin-copilot/realm-fintech/" + otherUserId + "/" + UUID.randomUUID() + "/own.png";

        for (AdminJwtPrincipal attacker : List.of(
                sameRealmOtherUser, sameRealmAuditor, otherRealmAdmin, sameUserDifferentRealm, otherRoot)) {
            assertThatThrownBy(() -> service.toMedia(attacker, List.of(
                    new AdminCopilotAttachmentRequest(victimKey, null, "secret.png", "image/png", "image"))))
                    .isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> service.toMedia(attacker, List.of(
                    new AdminCopilotAttachmentRequest(null, victimUrl, "secret.png", "image/png", "image"))))
                    .isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> service.toMedia(attacker, List.of(
                    new AdminCopilotAttachmentRequest(victimKey, victimUrl, "secret.png", "image/png", "image"))))
                    .isInstanceOf(AccessDeniedException.class);
        }

        // Попытка подмены url при собственном objectKey (или наоборот) должна отклоняться
        assertThatThrownBy(() -> service.toMedia(otherRealmAdmin, List.of(
                new AdminCopilotAttachmentRequest(ownKeyForOtherRealm, victimUrl, "secret.png", "image/png", "image"))))
                .isInstanceOf(IllegalArgumentException.class);

        // Укороченный ключ без realm-сегмента тоже не должен проходить проверку владельца
        AdminJwtPrincipal owner = principal(ownerId, GlobalRole.PLATFORM_ADMIN, "bank");
        assertThatThrownBy(() -> service.toMedia(owner, List.of(
                new AdminCopilotAttachmentRequest(
                        "admin-copilot/" + ownerId + "/" + uploadId + "/legacy.png",
                        null,
                        "legacy.png",
                        "image/png",
                        "image"))))
                .isInstanceOf(AccessDeniedException.class);

        // Отсутствующий principal должен отклоняться fail-closed
        assertThatThrownBy(() -> service.toMedia(null, List.of(
                new AdminCopilotAttachmentRequest(victimKey, victimUrl, "secret.png", "image/png", "image"))))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.upload(
                null,
                new MockMultipartFile("file", "photo.png", "image/png", "x".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(minioProvider);
    }

    private AdminJwtPrincipal principal(UUID userId, GlobalRole role, String realmCode) {
        return new AdminJwtPrincipal(userId, "admin-" + userId.toString().substring(0, 6), role, realmCode);
    }

    private PlatformAiProperties configuredProperties(String publicBaseUrl, String bucket) {
        PlatformAiProperties properties = new PlatformAiProperties();
        properties.getMedia().getMinio().setPublicBaseUrl(publicBaseUrl);
        properties.getMedia().getMinio().setBucket(bucket);
        return properties;
    }
}

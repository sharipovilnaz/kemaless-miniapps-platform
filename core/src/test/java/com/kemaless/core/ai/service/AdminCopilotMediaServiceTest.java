package com.kemaless.core.ai.service;

import com.kemaless.core.ai.config.PlatformAiProperties;
import com.kemaless.core.ai.dto.api.AdminCopilotAttachmentRequest;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import okhttp3.Headers;
import org.junit.jupiter.api.Test;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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

        List<String> forbiddenUrls = List.of(
                "file:///etc/passwd",
                "http://127.0.0.1:8080/actuator/env",
                "http://169.254.169.254/latest/meta-data",
                "http://[::1]:9000/ai-media-bucket/admin-copilot/u/id/a.png",
                "http://[::ffff:127.0.0.1]:9000/ai-media-bucket/admin-copilot/u/id/a.png",
                "http://[::ffff:169.254.169.254]/latest/meta-data",
                "http://[fc00::1]/secret.png",
                "http://[fd12:3456::1]/ai-media-bucket/admin-copilot/u/id/a.png",
                "http://[fe80::1]/ai-media-bucket/admin-copilot/u/id/a.png",
                "http://0.0.0.1/ai-media-bucket/admin-copilot/u/id/a.png",
                "http://100.64.0.1/ai-media-bucket/admin-copilot/u/id/a.png",
                "http://198.18.0.1/ai-media-bucket/admin-copilot/u/id/a.png",
                "https://evil.example.com/redirect-to-internal",
                "http://localhost:9000@evil.com/ai-media-bucket/admin-copilot/u/id/a.png",
                "http://localhost:9000/other-bucket/admin-copilot/u/id/a.png",
                "http://localhost:9000/ai-media-bucket/../other-bucket/admin-copilot/u/id/a.png",
                "http://localhost:9000/ai-media-bucket/admin-copilot/%2e%2e/secret.png",
                "http://localhost:9000/ai-media-bucket/admin-copilot/u/id/a.png?redirect=http://127.0.0.1"
        );

        for (String url : forbiddenUrls) {
            assertThatThrownBy(() -> service.toMedia(List.of(
                    new AdminCopilotAttachmentRequest(null, url, "file.png", "image/png", "image"))))
                    .as("Expected URL to be rejected: %s", url)
                    .isInstanceOf(IllegalArgumentException.class);
        }

        List<String> invalidObjectKeys = List.of(
                "../secret/file.txt",
                "admin-copilot/",
                "admin-copilot/../other/secret.txt",
                "admin-copilot/u1//id/file.txt",
                "admin-copilot/u1/id/file\\name.txt",
                "other-prefix/u1/id/file.txt"
        );
        for (String key : invalidObjectKeys) {
            assertThatThrownBy(() -> service.toMedia(List.of(
                    new AdminCopilotAttachmentRequest(key, null, "file.txt", "text/plain", "file"))))
                    .as("Expected objectKey to be rejected: %s", key)
                    .isInstanceOf(IllegalArgumentException.class);
        }

        verifyNoInteractions(minioProvider);
    }

    @Test
    void resolvesTrustedStorageUrlAndObjectKeyStrictlyViaMinioClient() throws Exception {
        MinioClient minioClient = mock(MinioClient.class);
        when(minioProvider.getIfAvailable()).thenReturn(minioClient);
        when(minioClient.getObject(any(GetObjectArgs.class)))
                .thenReturn(new GetObjectResponse(
                        new Headers.Builder().build(),
                        "ai-media-bucket",
                        null,
                        "admin-copilot/u1/id1/photo.png",
                        new ByteArrayInputStream("png-bytes".getBytes(StandardCharsets.UTF_8))));

        PlatformAiProperties properties = configuredProperties("http://localhost:9000", "ai-media-bucket");
        AdminCopilotMediaService service = new AdminCopilotMediaService(minioProvider, properties);

        List<Media> mediaList = service.toMedia(List.of(
                new AdminCopilotAttachmentRequest(
                        null,
                        "http://localhost:9000/ai-media-bucket/admin-copilot/u1/id1/photo.png",
                        "photo.png",
                        "image/png",
                        "image")));

        assertThat(mediaList).hasSize(1);
        assertThat(mediaList.getFirst().getDataAsByteArray()).isEqualTo("png-bytes".getBytes(StandardCharsets.UTF_8));
        verify(minioClient).getObject(any(GetObjectArgs.class));
    }

    private PlatformAiProperties configuredProperties(String publicBaseUrl, String bucket) {
        PlatformAiProperties properties = new PlatformAiProperties();
        properties.getMedia().getMinio().setPublicBaseUrl(publicBaseUrl);
        properties.getMedia().getMinio().setBucket(bucket);
        return properties;
    }
}

package com.kemaless.core.ai.service;

import com.kemaless.core.ai.config.PlatformAiProperties;
import com.kemaless.core.ai.dto.api.AdminCopilotAttachmentRequest;
import io.minio.MinioClient;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class AdminCopilotMediaServiceTest {

    @SuppressWarnings("unchecked")
    private final ObjectProvider<MinioClient> minioProvider = mock(ObjectProvider.class);

    @Test
    void rejectsLocalFilesPrivateIpsAndPathTraversalInAttachments() {
        PlatformAiProperties properties = new PlatformAiProperties();
        properties.getMedia().getMinio().setPublicBaseUrl("http://localhost:9000");
        AdminCopilotMediaService service = new AdminCopilotMediaService(minioProvider, properties);

        assertThatThrownBy(() -> service.toMedia(List.of(
                new AdminCopilotAttachmentRequest(null, "file:///etc/passwd", "passwd", "text/plain", "file"))))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> service.toMedia(List.of(
                new AdminCopilotAttachmentRequest(null, "http://127.0.0.1:8080/actuator/env", "env", "application/json", "file"))))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> service.toMedia(List.of(
                new AdminCopilotAttachmentRequest(null, "http://169.254.169.254/latest/meta-data", "meta", "text/plain", "file"))))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> service.toMedia(List.of(
                new AdminCopilotAttachmentRequest("../secret/file.txt", null, "file.txt", "text/plain", "file"))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

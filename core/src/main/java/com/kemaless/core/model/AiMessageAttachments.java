package com.kemaless.core.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_message_attachments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiMessageAttachments {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id", nullable = false)
    private AiChatMessageEntity message;

    @Column(name = "file_type", nullable = false, length = 50)
    private String fileType;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "minio_url", nullable = false, columnDefinition = "TEXT")
    private String minioUrl;

    @Column(name = "mime_type", length = 100)
    private String mimeType;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}

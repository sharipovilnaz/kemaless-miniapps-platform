package com.kemaless.core.model;

import com.kemaless.core.enums.RealmMiniAppStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "realm_mini_apps")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RealmMiniApp {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "realm_id", nullable = false)
    private Realm realm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mini_app_id", nullable = false)
    private MiniApp miniApp;

    @Column(name = "entry_url", nullable = false, columnDefinition = "TEXT")
    private String entryUrl;

    @Column(name = "backend_url", columnDefinition = "TEXT")
    private String backendUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private RealmMiniAppStatus status;

    @Embedded
    private AccessPolicy accessPolicy;

    @Column(nullable = false)
    @Builder.Default
    private Long launches = 0L;

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> settings;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}

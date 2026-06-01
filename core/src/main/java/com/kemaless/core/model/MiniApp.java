package com.kemaless.core.model;

import com.kemaless.core.enums.AppStatus;
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
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "mini_apps")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MiniApp {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "base_url", nullable = false)
    private String baseUrl;

    @Column(name = "default_entry_url")
    private String defaultEntryUrl;

    @Column(name = "default_backend_url")
    private String defaultBackendUrl;

    @Column(name = "entry_path", nullable = false)
    private String entryPath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppStatus status;

    @Column(name = "icon_url")
    private String iconUrl;

    @Column(name = "theme_color")
    private String themeColor;

    @ElementCollection
    @CollectionTable(name = "app_roles", joinColumns = @JoinColumn(name = "app_id"))
    @Column(name = "role_name")
    private Set<String> allowedRoles;

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> metadata;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}

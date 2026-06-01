package com.kemaless.core.model;

import com.kemaless.core.enums.AccessType;
import com.kemaless.core.enums.RoleMatchMode;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "miniapp_access_policies")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MiniAppAccessPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "realm_mini_app_id", nullable = false)
    private RealmMiniApp realmMiniApp;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_type", nullable = false, length = 50)
    private AccessType accessType;

    @Enumerated(EnumType.STRING)
    @Column(name = "role_match_mode", length = 20)
    private RoleMatchMode roleMatchMode;

    @Column(name = "required_roles", columnDefinition = "jsonb")
    private List<String> requiredRoles;

    @Column(name = "required_groups", columnDefinition = "jsonb")
    private List<String> requiredGroups;

    @Column(name = "required_attributes", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> requiredAttributes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}

package com.kemaless.core.model;

import com.kemaless.core.enums.RoleSource;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;


import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "external_role_mappings", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"realm_id", "external_role"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExternalRoleMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "realm_id", nullable = false)
    private Realm realm;

    @Column(name = "external_role", nullable = false)
    private String externalRole;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "realm_role_id", nullable = false)
    private RealmRole realmRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private RoleSource source;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}

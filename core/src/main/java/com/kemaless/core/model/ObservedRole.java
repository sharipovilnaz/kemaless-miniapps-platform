package com.kemaless.core.model;

import com.kemaless.core.enums.ObservedRoleStatus;
import com.kemaless.core.enums.RoleSource;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "observed_external_roles",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_observed_external_role_realm_source",
                        columnNames = {"realm_id", "external_role", "source"}
                )
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ObservedRole {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "realm_id", nullable = false)
    private Realm realm;

    @Column(name = "external_role", nullable = false)
    private String externalRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private RoleSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ObservedRoleStatus status;

    @CreationTimestamp
    @Column(name = "first_seen_at", nullable = false, updatable = false)
    private OffsetDateTime firstSeenAt;

    @UpdateTimestamp
    @Column(name = "last_seen_at", nullable = false)
    private OffsetDateTime lastSeenAt;

    @Column(name = "seen_count", nullable = false)
    @Builder.Default
    private long seenCount = 1L;
}

package com.kemaless.core.auth.entity;

import com.kemaless.core.auth.enums.MiniAppSessionStatus;
import com.kemaless.core.model.Realm;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "miniapp_sessions")
@Getter
@Setter
@NoArgsConstructor
public class MiniAppSession {

    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "realm_id", nullable = false)
    private Realm realm;

    @Column(name = "realm_mini_app_id", nullable = false)
    private UUID realmMiniAppId;

    @Column(name = "mini_app_code", nullable = false, length = 100)
    private String miniAppCode;

    @Column(name = "external_user_id", nullable = false)
    private String externalUserId;

    @Column(name = "email")
    private String email;

    @Column(name = "realm_roles", length = 1_000_000)
    private String realmRoles;

    @Column(name = "refresh_token_hash", nullable = false, unique = true, length = 128)
    private String refreshTokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private MiniAppSessionStatus status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}

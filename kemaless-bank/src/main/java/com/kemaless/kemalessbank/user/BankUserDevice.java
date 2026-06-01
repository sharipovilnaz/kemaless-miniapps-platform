package com.kemaless.kemalessbank.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "bank_user_device",
        uniqueConstraints =
                @UniqueConstraint(name = "uk_bank_user_device_user_fingerprint", columnNames = {"user_id", "fingerprint"}))
public class BankUserDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private BankUser user;

    @Column(nullable = false, length = 64)
    private String fingerprint;

    @Column(name = "user_agent", nullable = false, columnDefinition = "TEXT")
    private String userAgent;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "first_seen_at", nullable = false)
    private OffsetDateTime firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private OffsetDateTime lastSeenAt;

    public BankUserDevice(BankUser user, String fingerprint, String userAgent, String ipAddress, OffsetDateTime now) {
        this.user = user;
        this.fingerprint = fingerprint;
        this.userAgent = userAgent;
        this.ipAddress = ipAddress;
        this.firstSeenAt = now;
        this.lastSeenAt = now;
    }

    public void touch(String ipAddress, String userAgent, OffsetDateTime at) {
        this.lastSeenAt = at;
        if (ipAddress != null && !ipAddress.isBlank()) {
            this.ipAddress = ipAddress;
        }
        if (userAgent != null && !userAgent.isBlank()) {
            this.userAgent = userAgent;
        }
    }
}

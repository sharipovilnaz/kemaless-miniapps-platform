package com.kemaless.kemalessbank.user;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BankUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String fullName;

    @Column(unique = true)
    private String email;

    @Column(length = 32)
    private String phone;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private Set<BankUserRole> roleLinks = new HashSet<>();

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    public BankUser(String username, String passwordHash, String fullName, Set<BankRole> roles) {
        this(username, passwordHash, fullName, null, null, roles);
    }

    public BankUser(
            String username,
            String passwordHash,
            String fullName,
            String email,
            String phone,
            Set<BankRole> roles) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        for (BankRole role : roles) {
            this.roleLinks.add(new BankUserRole(this, role));
        }
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
        touch();
    }

    public void updateProfile(String fullName, String email, String phone) {
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        touch();
    }

    public List<String> roles() {
        return roleLinks.stream().map(link -> link.getRole().getCode()).sorted().toList();
    }

    private void touch() {
        this.updatedAt = OffsetDateTime.now();
    }
}

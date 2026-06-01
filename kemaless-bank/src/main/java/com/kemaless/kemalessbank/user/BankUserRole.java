package com.kemaless.kemalessbank.user;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "bank_user_role")
@IdClass(BankUserRoleId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BankUserRole {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private BankUser user;

    @Id
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private BankRole role;

    public BankUserRole(BankUser user, BankRole role) {
        this.user = user;
        this.role = role;
    }
}

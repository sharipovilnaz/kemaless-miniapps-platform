package com.kemaless.kemalessbank.account;

import com.kemaless.kemalessbank.user.BankUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "bank_account")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BankAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private BankUser user;

    @Column(nullable = false, unique = true, length = 34)
    private String accountNumber;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AccountStatus status;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    public BankAccount(BankUser user, String accountNumber, String title, String currency, BigDecimal balance) {
        this.user = user;
        this.accountNumber = accountNumber;
        this.title = title;
        this.currency = currency;
        this.balance = balance;
        this.status = AccountStatus.ACTIVE;
        this.createdAt = OffsetDateTime.now();
    }

    public void close() {
        this.status = AccountStatus.CLOSED;
    }

    public void block() {
        this.status = AccountStatus.BLOCKED;
    }

    public void unblock() {
        this.status = AccountStatus.ACTIVE;
    }

    public void debit(BigDecimal amount) {
        this.balance = this.balance.subtract(amount);
    }

    public void credit(BigDecimal amount) {
        this.balance = this.balance.add(amount);
    }
}

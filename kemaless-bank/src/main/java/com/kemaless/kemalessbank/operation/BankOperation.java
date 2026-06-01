package com.kemaless.kemalessbank.operation;

import com.kemaless.kemalessbank.account.BankAccount;
import com.kemaless.kemalessbank.user.BankUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "bank_operation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BankOperation {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private BankUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private BankAccount account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_account_id")
    private BankAccount relatedAccount;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false, length = 32)
    private OperationType type;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    public BankOperation(
            BankUser user,
            BankAccount account,
            BankAccount relatedAccount,
            OperationType type,
            BigDecimal amount,
            String currency,
            String description) {
        this.id = UUID.randomUUID();
        this.user = user;
        this.account = account;
        this.relatedAccount = relatedAccount;
        this.type = type;
        this.amount = amount;
        this.currency = currency;
        this.description = description;
        this.createdAt = OffsetDateTime.now();
    }
}

package com.google.payments.diple.domain;

import com.google.payments.diple.common.domain.AccountStatus;
import com.google.payments.diple.common.domain.AccountType;
import com.google.payments.diple.common.domain.Money;
import com.google.payments.diple.common.exception.AccountFrozenException;
import com.google.payments.diple.common.exception.InsufficientFundsException;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "accounts", indexes = {
        @Index(name = "idx_accounts_owner", columnList = "owner_id, status"),
        @Index(name = "idx_accounts_bucket", columnList = "id, partition_bucket")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {

    @Id
    @Column(name = "id", length = 64, nullable = false, updatable = false)
    private String id;

    @Column(name = "owner_id", length = 64, nullable = false)
    private String ownerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", length = 20, nullable = false)
    private AccountType accountType;

    @Column(name = "currency", length = 3, nullable = false)
    private String currency;

    @Column(name = "posted_balance", nullable = false)
    @Builder.Default
    private long postedBalance = 0L;

    @Column(name = "pending_debits", nullable = false)
    @Builder.Default
    private long pendingDebits = 0L;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private AccountStatus status = AccountStatus.ACTIVE;

    @Column(name = "partition_bucket", nullable = false)
    @Builder.Default
    private int partitionBucket = 0;

    @Version
    @Column(name = "version")
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public long getAvailableBalance() {
        return this.postedBalance - this.pendingDebits;
    }

    public Money getAvailableMoney() {
        return Money.of(getAvailableBalance(), this.currency);
    }

    public Money getPostedMoney() {
        return Money.of(this.postedBalance, this.currency);
    }

    public Money getPendingDebitsMoney() {
        return Money.of(this.pendingDebits, this.currency);
    }

    public void validateActive() {
        if (this.status != AccountStatus.ACTIVE) {
            throw new AccountFrozenException("Account " + this.id + " is not ACTIVE. Current status: " + this.status);
        }
    }

    public void debitPostedBalance(long minorUnits) {
        validateActive();
        if (minorUnits <= 0) {
            throw new IllegalArgumentException("Debit amount must be positive");
        }
        if (getAvailableBalance() < minorUnits) {
            throw new InsufficientFundsException(
                    "Insufficient available balance in account " + this.id +
                            ". Available: " + getAvailableBalance() + ", Requested: " + minorUnits);
        }
        this.postedBalance -= minorUnits;
    }

    public void creditPostedBalance(long minorUnits) {
        validateActive();
        if (minorUnits <= 0) {
            throw new IllegalArgumentException("Credit amount must be positive");
        }
        this.postedBalance += minorUnits;
    }

    public void reserveFunds(long minorUnits) {
        validateActive();
        if (minorUnits <= 0) {
            throw new IllegalArgumentException("Reserve amount must be positive");
        }
        if (getAvailableBalance() < minorUnits) {
            throw new InsufficientFundsException(
                    "Insufficient funds to reserve in account " + this.id +
                            ". Available: " + getAvailableBalance() + ", Requested: " + minorUnits);
        }
        this.pendingDebits += minorUnits;
    }

    public void releaseReservedFunds(long minorUnits) {
        if (minorUnits <= 0) {
            throw new IllegalArgumentException("Release amount must be positive");
        }
        this.pendingDebits = Math.max(0L, this.pendingDebits - minorUnits);
    }

    public void settleHold(long minorUnits) {
        validateActive();
        if (minorUnits <= 0) {
            throw new IllegalArgumentException("Settle amount must be positive");
        }
        this.pendingDebits = Math.max(0L, this.pendingDebits - minorUnits);
        this.postedBalance -= minorUnits;
    }
}

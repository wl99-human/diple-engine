package com.google.payments.diple.domain;

import com.google.payments.diple.common.domain.HoldStatus;
import com.google.payments.diple.common.domain.Money;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "payment_holds", indexes = {
        @Index(name = "idx_holds_account_status", columnList = "account_id, status"),
        @Index(name = "idx_holds_expiry", columnList = "expires_at, status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentHold {

    @Id
    @Column(name = "id", length = 64, nullable = false, updatable = false)
    private String id;

    @Column(name = "idempotency_key", length = 128, nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "account_id", length = 64, nullable = false)
    private String accountId;

    @Column(name = "amount", nullable = false)
    private long amount; // Minor units

    @Column(name = "currency", length = 3, nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private HoldStatus status = HoldStatus.HELD;

    @Column(name = "reference_id", length = 128)
    private String referenceId;

    @Column(name = "description", length = 512)
    private String description;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Money getMoney() {
        return Money.of(this.amount, this.currency);
    }
}

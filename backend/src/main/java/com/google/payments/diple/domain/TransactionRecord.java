package com.google.payments.diple.domain;

import com.google.payments.diple.common.domain.TransactionStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "transactions", indexes = {
        @Index(name = "idx_tx_idempotency", columnList = "idempotency_key", unique = true),
        @Index(name = "idx_tx_posted_at", columnList = "posted_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionRecord {

    @Id
    @Column(name = "id", length = 64, nullable = false, updatable = false)
    private String id;

    @Column(name = "idempotency_key", length = 128, nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "reference_id", length = 128)
    private String referenceId;

    @Column(name = "description", length = 512)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private TransactionStatus status = TransactionStatus.POSTED;

    @Column(name = "posted_at", nullable = false)
    private Instant postedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}

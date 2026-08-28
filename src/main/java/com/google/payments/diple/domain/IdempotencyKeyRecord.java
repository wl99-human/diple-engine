package com.google.payments.diple.domain;

import com.google.payments.diple.common.domain.IdempotencyStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "idempotency_keys", indexes = {
        @Index(name = "idx_idempotency_client", columnList = "client_id, created_at"),
        @Index(name = "idx_idempotency_expiry", columnList = "expires_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdempotencyKeyRecord {

    @Id
    @Column(name = "idempotency_key", length = 128, nullable = false)
    private String idempotencyKey;

    @Column(name = "request_hash", length = 64, nullable = false)
    private String requestHash;

    @Column(name = "client_id", length = 64, nullable = false)
    private String clientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private IdempotencyStatus status;

    @Column(name = "response_code")
    private Integer responseCode;

    @Lob
    @Column(name = "response_body")
    private String responseBody;

    @Column(name = "lock_owner", length = 64)
    private String lockOwner;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "lock_acquired_at")
    private Instant lockAcquiredAt;
}

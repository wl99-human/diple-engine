package com.google.payments.diple.domain;

import com.google.payments.diple.common.domain.PostingDirection;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "journal_postings", indexes = {
        @Index(name = "idx_postings_tx", columnList = "transaction_id"),
        @Index(name = "idx_postings_account_created", columnList = "account_id, created_at")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uq_tx_sequence", columnNames = {"transaction_id", "entry_sequence"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JournalPosting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id", length = 64, nullable = false)
    private String transactionId;

    @Column(name = "account_id", length = 64, nullable = false)
    private String accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", length = 10, nullable = false)
    private PostingDirection direction;

    @Column(name = "amount", nullable = false)
    private long amount; // Minor units

    @Column(name = "currency", length = 3, nullable = false)
    private String currency;

    @Column(name = "entry_sequence", nullable = false)
    private int entrySequence;

    @Column(name = "account_balance_after", nullable = false)
    private long accountBalanceAfter; // Snapshot of balance for audit

    @Column(name = "entry_hash", length = 64, nullable = false)
    private String entryHash; // SHA-256 Merkle chain link

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static String computeHash(String prevHash, String txId, String accountId, PostingDirection direction, long amount, long balanceAfter) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            String raw = prevHash + ":" + txId + ":" + accountId + ":" + direction.name() + ":" + amount + ":" + balanceAfter;
            byte[] hashBytes = digest.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hashBytes);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 calculation failed", e);
        }
    }
}

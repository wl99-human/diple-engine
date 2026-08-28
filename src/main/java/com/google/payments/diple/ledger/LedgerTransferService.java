package com.google.payments.diple.ledger;

import com.google.payments.diple.common.domain.Money;
import com.google.payments.diple.common.domain.PostingDirection;
import com.google.payments.diple.common.domain.TransactionStatus;
import com.google.payments.diple.common.exception.AccountNotFoundException;
import com.google.payments.diple.common.exception.CurrencyMismatchException;
import com.google.payments.diple.common.exception.InsufficientFundsException;
import com.google.payments.diple.common.exception.InvalidTransferException;
import com.google.payments.diple.domain.Account;
import com.google.payments.diple.domain.JournalPosting;
import com.google.payments.diple.domain.TransactionRecord;
import com.google.payments.diple.outbox.OutboxEventService;
import com.google.payments.diple.repository.AccountRepository;
import com.google.payments.diple.repository.JournalPostingRepository;
import com.google.payments.diple.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class LedgerTransferService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final JournalPostingRepository journalPostingRepository;
    private final OutboxEventService outboxEventService;

    public record TransferResult(
            String transactionId,
            String idempotencyKey,
            String senderAccountId,
            String receiverAccountId,
            long amountMinorUnits,
            String currency,
            long senderBalanceAfter,
            long receiverBalanceAfter,
            Instant postedAt
    ) {}

    /**
     * Executes an atomic transfer between two accounts with strict double-entry balance conservation
     * and deterministic lock ordering to prevent distributed deadlocks under concurrent transfers.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED, timeout = 5)
    public TransferResult executeTransfer(
            String idempotencyKey,
            String senderAccountId,
            String receiverAccountId,
            Money amount,
            String referenceId,
            String description) {

        if (senderAccountId.equals(receiverAccountId)) {
            throw new InvalidTransferException("Cannot execute transfer to the same account: " + senderAccountId);
        }

        if (!amount.isPositive()) {
            throw new InvalidTransferException("Transfer amount must be positive. Provided: " + amount);
        }

        // 1. Deterministic Lock Ordering: Sort IDs lexicographically to prevent deadlocks
        String firstLockId = senderAccountId.compareTo(receiverAccountId) < 0 ? senderAccountId : receiverAccountId;
        String secondLockId = senderAccountId.compareTo(receiverAccountId) < 0 ? receiverAccountId : senderAccountId;

        Account firstAccount = accountRepository.findByIdForUpdate(firstLockId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + firstLockId));
        Account secondAccount = accountRepository.findByIdForUpdate(secondLockId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + secondLockId));

        Account sender = senderAccountId.equals(firstLockId) ? firstAccount : secondAccount;
        Account receiver = receiverAccountId.equals(firstLockId) ? firstAccount : secondAccount;

        // 2. Strict Currency Matching
        if (!sender.getCurrency().equalsIgnoreCase(amount.currencyCode()) ||
                !receiver.getCurrency().equalsIgnoreCase(amount.currencyCode())) {
            throw new CurrencyMismatchException(
                    "Transfer currency '" + amount.currencyCode() + "' does not match accounts (" +
                            sender.getCurrency() + " / " + receiver.getCurrency() + ")");
        }

        // 3. Balance Invariant Verification: Available = posted_balance - pending_debits
        if (sender.getAvailableBalance() < amount.minorUnits()) {
            throw new InsufficientFundsException(
                    "Insufficient available funds in sender account " + senderAccountId +
                            ". Available: " + sender.getAvailableBalance() + ", Required: " + amount.minorUnits());
        }

        // 4. Atomic Balance Mutation
        sender.debitPostedBalance(amount.minorUnits());
        receiver.creditPostedBalance(amount.minorUnits());

        accountRepository.save(sender);
        accountRepository.save(receiver);

        // 5. Create Transaction Header
        String txId = "tx_" + UUID.randomUUID();
        Instant now = Instant.now();
        TransactionRecord tx = TransactionRecord.builder()
                .id(txId)
                .idempotencyKey(idempotencyKey)
                .referenceId(referenceId)
                .description(description)
                .status(TransactionStatus.POSTED)
                .postedAt(now)
                .build();
        transactionRepository.save(tx);

        // 6. Double-Entry Postings with Cryptographic SHA-256 Merkle Chaining
        String prevSenderHash = journalPostingRepository.findLatestPostingForAccount(sender.getId())
                .map(JournalPosting::getEntryHash)
                .orElse("GENESIS_HASH_" + sender.getId());
        String senderEntryHash = JournalPosting.computeHash(prevSenderHash, txId, sender.getId(), PostingDirection.DEBIT, amount.minorUnits(), sender.getPostedBalance());

        JournalPosting debitPosting = JournalPosting.builder()
                .transactionId(txId)
                .accountId(sender.getId())
                .direction(PostingDirection.DEBIT)
                .amount(amount.minorUnits())
                .currency(amount.currencyCode())
                .entrySequence(1)
                .accountBalanceAfter(sender.getPostedBalance())
                .entryHash(senderEntryHash)
                .build();

        String prevReceiverHash = journalPostingRepository.findLatestPostingForAccount(receiver.getId())
                .map(JournalPosting::getEntryHash)
                .orElse("GENESIS_HASH_" + receiver.getId());
        String receiverEntryHash = JournalPosting.computeHash(prevReceiverHash, txId, receiver.getId(), PostingDirection.CREDIT, amount.minorUnits(), receiver.getPostedBalance());

        JournalPosting creditPosting = JournalPosting.builder()
                .transactionId(txId)
                .accountId(receiver.getId())
                .direction(PostingDirection.CREDIT)
                .amount(amount.minorUnits())
                .currency(amount.currencyCode())
                .entrySequence(2)
                .accountBalanceAfter(receiver.getPostedBalance())
                .entryHash(receiverEntryHash)
                .build();

        journalPostingRepository.saveAll(List.of(debitPosting, creditPosting));

        // 7. Atomic Outbox Event Staging
        Map<String, Object> eventPayload = Map.of(
                "transactionId", txId,
                "idempotencyKey", idempotencyKey,
                "senderAccountId", sender.getId(),
                "receiverAccountId", receiver.getId(),
                "amountMinorUnits", amount.minorUnits(),
                "currency", amount.currencyCode(),
                "postedAt", now.toString()
        );

        outboxEventService.stageEvent(
                "PAYMENT_TRANSFER",
                sender.getId(), // Partition Key for FIFO ordering per account
                "PaymentSettledEvent",
                eventPayload
        );

        log.info("Ledger transfer committed successfully: txId={}, sender={}, receiver={}, amount={}{}",
                txId, sender.getId(), receiver.getId(), amount.minorUnits(), amount.currencyCode());

        return new TransferResult(
                txId,
                idempotencyKey,
                sender.getId(),
                receiver.getId(),
                amount.minorUnits(),
                amount.currencyCode(),
                sender.getPostedBalance(),
                receiver.getPostedBalance(),
                now
        );
    }

    private String computeSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 unavailable", e);
        }
    }
}

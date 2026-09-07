package com.google.payments.diple.ledger;

import com.google.payments.diple.common.domain.HoldStatus;
import com.google.payments.diple.common.domain.Money;
import com.google.payments.diple.common.domain.PostingDirection;
import com.google.payments.diple.common.domain.TransactionStatus;
import com.google.payments.diple.common.exception.AccountNotFoundException;
import com.google.payments.diple.common.exception.CurrencyMismatchException;
import com.google.payments.diple.common.exception.InvalidHoldException;
import com.google.payments.diple.domain.Account;
import com.google.payments.diple.domain.JournalPosting;
import com.google.payments.diple.domain.PaymentHold;
import com.google.payments.diple.domain.TransactionRecord;
import com.google.payments.diple.outbox.OutboxEventService;
import com.google.payments.diple.repository.AccountRepository;
import com.google.payments.diple.repository.JournalPostingRepository;
import com.google.payments.diple.repository.PaymentHoldRepository;
import com.google.payments.diple.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentHoldService {

    private final AccountRepository accountRepository;
    private final PaymentHoldRepository paymentHoldRepository;
    private final TransactionRepository transactionRepository;
    private final JournalPostingRepository journalPostingRepository;
    private final OutboxEventService outboxEventService;

    @Transactional
    public PaymentHold authorizeHold(
            String idempotencyKey,
            String accountId,
            Money amount,
            String referenceId,
            String description,
            Duration holdDuration) {

        Account account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));

        if (!account.getCurrency().equalsIgnoreCase(amount.currencyCode())) {
            throw new CurrencyMismatchException(
                    "Hold currency " + amount.currencyCode() + " does not match account currency " + account.getCurrency());
        }

        // Reserve available balance
        account.reserveFunds(amount.minorUnits());
        accountRepository.save(account);

        String holdId = "hold_" + UUID.randomUUID();
        Instant expiresAt = Instant.now().plus(holdDuration != null ? holdDuration : Duration.ofMinutes(15));

        PaymentHold hold = PaymentHold.builder()
                .id(holdId)
                .idempotencyKey(idempotencyKey)
                .accountId(accountId)
                .amount(amount.minorUnits())
                .currency(amount.currencyCode())
                .status(HoldStatus.HELD)
                .referenceId(referenceId)
                .description(description)
                .expiresAt(expiresAt)
                .build();

        paymentHoldRepository.save(hold);

        outboxEventService.stageEvent(
                "PAYMENT_HOLD",
                accountId,
                "HoldAuthorizedEvent",
                Map.of("holdId", holdId, "accountId", accountId, "amount", amount.minorUnits(), "expiresAt", expiresAt.toString())
        );

        log.info("Payment hold authorized: holdId={}, accountId={}, amount={}{}",
                holdId, accountId, amount.minorUnits(), amount.currencyCode());

        return hold;
    }

    @Transactional
    public TransactionRecord captureHold(
            String idempotencyKey,
            String holdId,
            String destinationAccountId,
            String description) {

        PaymentHold hold = paymentHoldRepository.findById(holdId)
                .orElseThrow(() -> new InvalidHoldException("Hold not found: " + holdId));

        if (hold.getStatus() != HoldStatus.HELD) {
            throw new InvalidHoldException("Hold " + holdId + " is not in HELD state. Current: " + hold.getStatus());
        }

        if (Instant.now().isAfter(hold.getExpiresAt())) {
            hold.setStatus(HoldStatus.EXPIRED);
            paymentHoldRepository.save(hold);
            throw new InvalidHoldException("Hold " + holdId + " has expired.");
        }

        String senderId = hold.getAccountId();
        String firstLockId = senderId.compareTo(destinationAccountId) < 0 ? senderId : destinationAccountId;
        String secondLockId = senderId.compareTo(destinationAccountId) < 0 ? destinationAccountId : senderId;

        Account first = accountRepository.findByIdForUpdate(firstLockId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + firstLockId));
        Account second = accountRepository.findByIdForUpdate(secondLockId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + secondLockId));

        Account sender = senderId.equals(firstLockId) ? first : second;
        Account receiver = destinationAccountId.equals(firstLockId) ? first : second;

        // Execute settlement
        sender.settleHold(hold.getAmount());
        receiver.creditPostedBalance(hold.getAmount());

        accountRepository.save(sender);
        accountRepository.save(receiver);

        hold.setStatus(HoldStatus.CAPTURED);
        paymentHoldRepository.save(hold);

        String txId = "tx_capture_" + UUID.randomUUID();
        Instant now = Instant.now();
        TransactionRecord tx = TransactionRecord.builder()
                .id(txId)
                .idempotencyKey(idempotencyKey)
                .referenceId(hold.getReferenceId())
                .description(description != null ? description : "Capture for hold " + holdId)
                .status(TransactionStatus.POSTED)
                .postedAt(now)
                .build();
        transactionRepository.save(tx);

        String prevSenderHash = journalPostingRepository.findLatestPostingForAccount(sender.getId())
                .map(JournalPosting::getEntryHash).orElse("GENESIS_HASH_" + sender.getId());
        String senderEntryHash = JournalPosting.computeHash(prevSenderHash, txId, sender.getId(), PostingDirection.DEBIT, hold.getAmount(), sender.getPostedBalance());

        JournalPosting debitPosting = JournalPosting.builder()
                .transactionId(txId)
                .accountId(sender.getId())
                .direction(PostingDirection.DEBIT)
                .amount(hold.getAmount())
                .currency(hold.getCurrency())
                .entrySequence(1)
                .accountBalanceAfter(sender.getPostedBalance())
                .entryHash(senderEntryHash)
                .build();

        String prevReceiverHash = journalPostingRepository.findLatestPostingForAccount(receiver.getId())
                .map(JournalPosting::getEntryHash).orElse("GENESIS_HASH_" + receiver.getId());
        String receiverEntryHash = JournalPosting.computeHash(prevReceiverHash, txId, receiver.getId(), PostingDirection.CREDIT, hold.getAmount(), receiver.getPostedBalance());

        JournalPosting creditPosting = JournalPosting.builder()
                .transactionId(txId)
                .accountId(receiver.getId())
                .direction(PostingDirection.CREDIT)
                .amount(hold.getAmount())
                .currency(hold.getCurrency())
                .entrySequence(2)
                .accountBalanceAfter(receiver.getPostedBalance())
                .entryHash(receiverEntryHash)
                .build();

        journalPostingRepository.saveAll(List.of(debitPosting, creditPosting));

        outboxEventService.stageEvent(
                "PAYMENT_HOLD",
                sender.getId(),
                "HoldCapturedEvent",
                Map.of("holdId", holdId, "transactionId", txId, "amount", hold.getAmount())
        );

        log.info("Payment hold captured successfully: holdId={}, txId={}, amount={}{}",
                holdId, txId, hold.getAmount(), hold.getCurrency());

        return tx;
    }

    @Transactional
    public PaymentHold voidHold(String holdId, String reason) {
        PaymentHold hold = paymentHoldRepository.findById(holdId)
                .orElseThrow(() -> new InvalidHoldException("Hold not found: " + holdId));

        if (hold.getStatus() != HoldStatus.HELD) {
            throw new InvalidHoldException("Hold " + holdId + " is not in HELD state. Current: " + hold.getStatus());
        }

        Account account = accountRepository.findByIdForUpdate(hold.getAccountId())
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + hold.getAccountId()));

        account.releaseReservedFunds(hold.getAmount());
        accountRepository.save(account);

        hold.setStatus(HoldStatus.RELEASED);
        paymentHoldRepository.save(hold);

        outboxEventService.stageEvent(
                "PAYMENT_HOLD",
                account.getId(),
                "HoldVoidedEvent",
                Map.of("holdId", holdId, "accountId", account.getId(), "reason", reason != null ? reason : "Client void")
        );

        log.info("Payment hold voided: holdId={}, accountId={}, amount={}{}",
                holdId, account.getId(), hold.getAmount(), hold.getCurrency());

        return hold;
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

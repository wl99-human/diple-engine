package com.google.payments.diple.api.dto;

import com.google.payments.diple.common.domain.HoldStatus;
import com.google.payments.diple.common.domain.PostingDirection;
import com.google.payments.diple.common.domain.TransactionStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.time.Instant;
import java.util.List;

public class PaymentDtos {

    public record TransferRequest(
            @NotBlank(message = "Sender account ID is required") String senderAccountId,
            @NotBlank(message = "Receiver account ID is required") String receiverAccountId,
            @Positive(message = "Transfer amount must be strictly positive") long amountMinorUnits,
            @NotBlank(message = "Currency is required") String currency,
            String referenceId,
            String description
    ) {}

    public record TransferResponse(
            String transactionId,
            String idempotencyKey,
            String senderAccountId,
            String receiverAccountId,
            long amountMinorUnits,
            String currency,
            long senderBalanceAfter,
            long receiverBalanceAfter,
            TransactionStatus status,
            Instant postedAt,
            boolean isCachedReplay
    ) {}

    public record CreateHoldRequest(
            @NotBlank(message = "Account ID is required") String accountId,
            @Positive(message = "Amount must be strictly positive") long amountMinorUnits,
            @NotBlank(message = "Currency is required") String currency,
            String referenceId,
            String description,
            Integer holdDurationMinutes
    ) {}

    public record HoldResponse(
            String holdId,
            String idempotencyKey,
            String accountId,
            long amountMinorUnits,
            String currency,
            HoldStatus status,
            Instant expiresAt,
            Instant createdAt
    ) {}

    public record CaptureHoldRequest(
            @NotBlank(message = "Destination account ID is required") String destinationAccountId,
            String description
    ) {}

    public record JournalPostingResponse(
            Long id,
            String transactionId,
            String accountId,
            PostingDirection direction,
            long amountMinorUnits,
            String currency,
            int sequence,
            long balanceAfter,
            String entryHash,
            Instant createdAt
    ) {}
}

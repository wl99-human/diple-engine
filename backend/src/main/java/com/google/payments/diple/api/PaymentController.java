package com.google.payments.diple.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.payments.diple.api.dto.PaymentDtos.*;
import com.google.payments.diple.common.domain.Money;
import com.google.payments.diple.domain.JournalPosting;
import com.google.payments.diple.domain.PaymentHold;
import com.google.payments.diple.domain.TransactionRecord;
import com.google.payments.diple.idempotency.CachedResponse;
import com.google.payments.diple.idempotency.IdempotencyService;
import com.google.payments.diple.ledger.LedgerTransferService;
import com.google.payments.diple.ledger.PaymentHoldService;
import com.google.payments.diple.repository.JournalPostingRepository;
import com.google.payments.diple.telemetry.MetricsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/v1/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final LedgerTransferService ledgerTransferService;
    private final PaymentHoldService paymentHoldService;
    private final IdempotencyService idempotencyService;
    private final JournalPostingRepository journalPostingRepository;
    private final MetricsService metricsService;
    private final ObjectMapper objectMapper;

    @PostMapping("/transfers")
    public ResponseEntity<?> executeTransfer(
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKeyHeader,
            @RequestHeader(value = "X-Client-Id", required = false) String clientId,
            @Valid @RequestBody TransferRequest request) {

        String idempotencyKey = (idempotencyKeyHeader != null && !idempotencyKeyHeader.isBlank())
                ? idempotencyKeyHeader
                : "auto_key_" + UUID.randomUUID();

        String uri = "/v1/payments/transfers";
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(request);
        } catch (Exception e) {
            payloadJson = request.toString();
        }

        // 1. Idempotency Check & Mutex Lock
        Optional<CachedResponse> cachedOpt = idempotencyService.checkAndLock(idempotencyKey, clientId, uri, payloadJson);
        if (cachedOpt.isPresent()) {
            metricsService.recordIdempotencyHit();
            CachedResponse cached = cachedOpt.get();
            try {
                TransferResponse cachedBody = objectMapper.readValue(cached.responseBody(), TransferResponse.class);
                TransferResponse responseWithFlag = new TransferResponse(
                        cachedBody.transactionId(),
                        cachedBody.idempotencyKey(),
                        cachedBody.senderAccountId(),
                        cachedBody.receiverAccountId(),
                        cachedBody.amountMinorUnits(),
                        cachedBody.currency(),
                        cachedBody.senderBalanceAfter(),
                        cachedBody.receiverBalanceAfter(),
                        cachedBody.status(),
                        cachedBody.postedAt(),
                        true // isCachedReplay
                );
                return ResponseEntity.status(cached.statusCode()).body(responseWithFlag);
            } catch (Exception e) {
                return ResponseEntity.status(cached.statusCode()).body(cached.responseBody());
            }
        }

        // 2. Execute Transfer within Telemetry Timer
        try {
            LedgerTransferService.TransferResult result = metricsService.recordTransferLatency(() ->
                    ledgerTransferService.executeTransfer(
                            idempotencyKey,
                            request.senderAccountId(),
                            request.receiverAccountId(),
                            Money.of(request.amountMinorUnits(), request.currency()),
                            request.referenceId(),
                            request.description()
                    )
            );

            TransferResponse response = new TransferResponse(
                    result.transactionId(),
                    result.idempotencyKey(),
                    result.senderAccountId(),
                    result.receiverAccountId(),
                    result.amountMinorUnits(),
                    result.currency(),
                    result.senderBalanceAfter(),
                    result.receiverBalanceAfter(),
                    com.google.payments.diple.common.domain.TransactionStatus.POSTED,
                    result.postedAt(),
                    false // Fresh execution
            );

            metricsService.recordTransferSuccess();

            // 3. Commit Cached Response
            idempotencyService.commitResponse(idempotencyKey, HttpStatus.CREATED.value(), objectMapper.writeValueAsString(response));

            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (Exception ex) {
            metricsService.recordTransferFailure();
            idempotencyService.failExecution(idempotencyKey);
            if (ex instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException("Transfer execution failed", ex);
        }
    }

    @PostMapping("/holds")
    public ResponseEntity<?> createHold(
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKeyHeader,
            @Valid @RequestBody CreateHoldRequest request) {

        String idempotencyKey = (idempotencyKeyHeader != null && !idempotencyKeyHeader.isBlank())
                ? idempotencyKeyHeader
                : "hold_key_" + UUID.randomUUID();

        Duration duration = request.holdDurationMinutes() != null ? Duration.ofMinutes(request.holdDurationMinutes()) : Duration.ofMinutes(15);
        PaymentHold hold = paymentHoldService.authorizeHold(
                idempotencyKey,
                request.accountId(),
                Money.of(request.amountMinorUnits(), request.currency()),
                request.referenceId(),
                request.description(),
                duration
        );

        HoldResponse response = new HoldResponse(
                hold.getId(),
                hold.getIdempotencyKey(),
                hold.getAccountId(),
                hold.getAmount(),
                hold.getCurrency(),
                hold.getStatus(),
                hold.getExpiresAt(),
                hold.getCreatedAt()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/holds/{id}/capture")
    public ResponseEntity<?> captureHold(
            @PathVariable("id") String holdId,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKeyHeader,
            @Valid @RequestBody CaptureHoldRequest request) {

        String idempotencyKey = (idempotencyKeyHeader != null && !idempotencyKeyHeader.isBlank())
                ? idempotencyKeyHeader
                : "capture_key_" + UUID.randomUUID();

        TransactionRecord tx = paymentHoldService.captureHold(
                idempotencyKey,
                holdId,
                request.destinationAccountId(),
                request.description()
        );

        return ResponseEntity.ok(tx);
    }

    @PostMapping("/holds/{id}/void")
    public ResponseEntity<?> voidHold(
            @PathVariable("id") String holdId,
            @RequestParam(value = "reason", required = false) String reason) {

        PaymentHold hold = paymentHoldService.voidHold(holdId, reason);
        return ResponseEntity.ok(hold);
    }

    @GetMapping("/postings/recent")
    public ResponseEntity<List<JournalPostingResponse>> getRecentPostings() {
        List<JournalPosting> postings = journalPostingRepository.findTop50ByOrderByCreatedAtDescIdDesc();
        List<JournalPostingResponse> dtos = postings.stream()
                .map(p -> new JournalPostingResponse(
                        p.getId(),
                        p.getTransactionId(),
                        p.getAccountId(),
                        p.getDirection(),
                        p.getAmount(),
                        p.getCurrency(),
                        p.getEntrySequence(),
                        p.getAccountBalanceAfter(),
                        p.getEntryHash(),
                        p.getCreatedAt()
                ))
                .toList();
        return ResponseEntity.ok(dtos);
    }
}

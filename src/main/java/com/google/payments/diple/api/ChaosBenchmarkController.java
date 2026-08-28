package com.google.payments.diple.api;

import com.google.payments.diple.api.dto.ChaosDtos.ChaosRunRequest;
import com.google.payments.diple.api.dto.ChaosDtos.ChaosRunResult;
import com.google.payments.diple.api.dto.PaymentDtos.TransferRequest;
import com.google.payments.diple.common.domain.AccountStatus;
import com.google.payments.diple.common.domain.AccountType;
import com.google.payments.diple.domain.Account;
import com.google.payments.diple.domain.JournalPosting;
import com.google.payments.diple.ledger.LedgerReconciliationService;
import com.google.payments.diple.ledger.LedgerReconciliationService.SystemReconciliationReport;
import com.google.payments.diple.repository.AccountRepository;
import com.google.payments.diple.repository.JournalPostingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/v1/chaos")
@RequiredArgsConstructor
@Slf4j
public class ChaosBenchmarkController {

    private final PaymentController paymentController;
    private final AccountRepository accountRepository;
    private final LedgerReconciliationService reconciliationService;

    private final JournalPostingRepository journalPostingRepository;

    @PostMapping("/seed-accounts")
    public ResponseEntity<Map<String, Object>> seedAccounts() {
        // Ensure Central Treasury / Settlement Account exists
        if (accountRepository.findById("acc_central_settlement").isEmpty()) {
            accountRepository.save(Account.builder()
                    .id("acc_central_settlement")
                    .ownerId("system_treasury")
                    .accountType(AccountType.EQUITY)
                    .currency("USD")
                    .postedBalance(0L)
                    .pendingDebits(0L)
                    .status(AccountStatus.ACTIVE)
                    .build());
        }

        // Seed 10 User Accounts and 2 Merchant Accounts with Double-Entry Genesis Postings
        List<Account> seeded = new ArrayList<>();
        List<JournalPosting> genesisPostings = new ArrayList<>();

        String prevCentralHash = journalPostingRepository.findLatestPostingForAccount("acc_central_settlement")
                .map(JournalPosting::getEntryHash).orElse("GENESIS_HASH_acc_central_settlement");

        for (int i = 1; i <= 10; i++) {
            String accId = "acc_user_" + i;
            if (accountRepository.findById(accId).isEmpty()) {
                Account acc = Account.builder()
                        .id(accId)
                        .ownerId("user_" + i)
                        .accountType(AccountType.LIABILITY)
                        .currency("USD")
                        .postedBalance(100_000L) // $1,000.00 each
                        .pendingDebits(0L)
                        .status(AccountStatus.ACTIVE)
                        .build();
                seeded.add(acc);

                String txId = "genesis_seed_" + accId;
                String centralHash = JournalPosting.computeHash(prevCentralHash, txId, "acc_central_settlement", com.google.payments.diple.common.domain.PostingDirection.DEBIT, 100_000L, 0L);
                prevCentralHash = centralHash; // Advance Merkle chain pointer

                genesisPostings.add(JournalPosting.builder()
                        .transactionId(txId)
                        .accountId("acc_central_settlement")
                        .direction(com.google.payments.diple.common.domain.PostingDirection.DEBIT)
                        .amount(100_000L)
                        .currency("USD")
                        .entrySequence(1)
                        .accountBalanceAfter(0L)
                        .entryHash(centralHash)
                        .build());

                String prevHash = journalPostingRepository.findLatestPostingForAccount(accId)
                        .map(JournalPosting::getEntryHash).orElse("GENESIS_HASH_" + accId);
                String hash = JournalPosting.computeHash(prevHash, txId, accId, com.google.payments.diple.common.domain.PostingDirection.CREDIT, 100_000L, 100_000L);

                genesisPostings.add(JournalPosting.builder()
                        .transactionId(txId)
                        .accountId(accId)
                        .direction(com.google.payments.diple.common.domain.PostingDirection.CREDIT)
                        .amount(100_000L)
                        .currency("USD")
                        .entrySequence(2)
                        .accountBalanceAfter(100_000L)
                        .entryHash(hash)
                        .build());
            }
        }

        for (int m = 1; m <= 2; m++) {
            String merchantId = "merchant_google_store_" + m;
            if (accountRepository.findById(merchantId).isEmpty()) {
                seeded.add(Account.builder()
                        .id(merchantId)
                        .ownerId("merchant_" + m)
                        .accountType(AccountType.REVENUE)
                        .currency("USD")
                        .postedBalance(0L)
                        .pendingDebits(0L)
                        .status(AccountStatus.ACTIVE)
                        .build());
            }
        }

        if (!seeded.isEmpty()) {
            accountRepository.saveAll(seeded);
            accountRepository.findById("acc_central_settlement").ifPresent(cs -> {
                cs.debitPostedBalance(seeded.size() * 100_000L);
                accountRepository.save(cs);
            });
            journalPostingRepository.saveAll(genesisPostings);
        }
        return ResponseEntity.ok(Map.of("seededCount", seeded.size(), "message", "Test accounts seeded successfully"));
    }

    private static String computeSha256(String input) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @PostMapping("/run")
    public ResponseEntity<ChaosRunResult> runChaosBenchmark(@RequestBody(required = false) ChaosRunRequest customRequest) {
        int totalRequests = (customRequest != null && customRequest.totalTransfers() > 0) ? customRequest.totalTransfers() : 100;
        int concurrency = (customRequest != null && customRequest.concurrencyLevel() > 0) ? customRequest.concurrencyLevel() : 20;
        int duplicateRate = (customRequest != null) ? customRequest.duplicateRatePercent() : 25;
        boolean useHotMerchant = (customRequest == null || customRequest.useHotMerchant());

        // Ensure accounts exist
        seedAccounts();

        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        List<Future<Long>> futures = new ArrayList<>();

        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger duplicateHits = new AtomicInteger(0);
        AtomicInteger conflicts = new AtomicInteger(0);
        AtomicInteger failures = new AtomicInteger(0);
        List<Long> latencies = new CopyOnWriteArrayList<>();

        String staticDuplicateKey = "static_chaos_replay_key_" + System.currentTimeMillis();
        long startTime = System.nanoTime();

        for (int i = 0; i < totalRequests; i++) {
            final int index = i;
            futures.add(executor.submit(() -> {
                long taskStart = System.nanoTime();
                try {
                    int senderNum = (index % 10) + 1;
                    String sender = "acc_user_" + senderNum;
                    String receiver = useHotMerchant ? "merchant_google_store_1" : "acc_user_" + (((senderNum + 1) % 10) + 1);

                    boolean isDuplicate = (ThreadLocalRandom.current().nextInt(100) < duplicateRate);
                    String key = isDuplicate ? staticDuplicateKey : "chaos_key_" + UUID.randomUUID();

                    TransferRequest request = new TransferRequest(
                            sender,
                            receiver,
                            100L, // $1.00
                            "USD",
                            "chaos_ref_" + index,
                            "Chaos Concurrency Test #" + index
                    );

                    var responseEntity = paymentController.executeTransfer(key, "chaos-tester", request);

                    if (responseEntity.getStatusCode().is2xxSuccessful()) {
                        successes.incrementAndGet();
                    } else if (responseEntity.getStatusCode().value() == 409) {
                        conflicts.incrementAndGet();
                    }
                } catch (com.google.payments.diple.common.exception.IdempotencyConflictException ce) {
                    conflicts.incrementAndGet();
                } catch (Exception ex) {
                    log.error("Chaos transfer error: {}", ex.getMessage());
                    failures.incrementAndGet();
                } finally {
                    long elapsedMs = (System.nanoTime() - taskStart) / 1_000_000L;
                    latencies.add(elapsedMs);
                }
                return 0L;
            }));
        }

        for (Future<Long> f : futures) {
            try {
                f.get();
            } catch (Exception ignored) {}
        }
        executor.shutdown();

        double totalDurationSec = (System.nanoTime() - startTime) / 1_000_000_000.0;
        double rps = totalRequests / Math.max(0.001, totalDurationSec);

        Collections.sort(latencies);
        double p50 = latencies.isEmpty() ? 0 : latencies.get((int) (latencies.size() * 0.50));
        double p95 = latencies.isEmpty() ? 0 : latencies.get((int) (latencies.size() * 0.95));
        double p99 = latencies.isEmpty() ? 0 : latencies.get((int) (latencies.size() * 0.99));

        // Perform automated ledger audit verification
        SystemReconciliationReport audit = reconciliationService.runFullAudit();
        boolean integrityPassed = "HEALTHY_100_PERCENT_CONSERVED".equals(audit.status());

        String summary = String.format(
                "Completed %d requests in %.2fs (%.1f RPS). p50=%.1fms, p95=%.1fms, p99=%.1fms. Ledger Balance Conservation: %s.",
                totalRequests, totalDurationSec, rps, p50, p95, p99, integrityPassed ? "100% INTACT (ZERO LEAKAGE)" : "FAILED");

        ChaosRunResult result = new ChaosRunResult(
                totalRequests,
                successes.get(),
                duplicateHits.get(),
                conflicts.get(),
                failures.get(),
                totalDurationSec,
                rps,
                p50,
                p95,
                p99,
                integrityPassed,
                summary
        );

        return ResponseEntity.ok(result);
    }
}

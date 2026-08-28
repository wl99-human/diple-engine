package com.google.payments.diple.api.dto;

import java.util.List;

public class ChaosDtos {

    public record ChaosRunRequest(
            int totalTransfers,
            int concurrencyLevel,
            int duplicateRatePercent,
            boolean useHotMerchant
    ) {}

    public record ChaosRunResult(
            int totalRequests,
            int successfulTransfers,
            int duplicateHitsReplayed,
            int inFlightConflictsHandled,
            int failedRequests,
            double totalDurationSeconds,
            double requestsPerSecond,
            double p50LatencyMs,
            double p95LatencyMs,
            double p99LatencyMs,
            boolean ledgerIntegrityPassed,
            String verificationSummary
    ) {}
}

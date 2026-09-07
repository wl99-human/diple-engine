package com.google.payments.diple.telemetry;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class MetricsService {

    private final Timer transferLatencyTimer;
    private final Counter transfersSuccessCounter;
    private final Counter transfersFailureCounter;
    private final Counter idempotencyHitsCounter;
    private final Counter idempotencyConflictsCounter;
    private final Counter invariantViolationsCounter;
    private final AtomicLong activeAccountsGauge = new AtomicLong(0);

    public MetricsService(MeterRegistry registry) {
        this.transferLatencyTimer = Timer.builder("diple.transfer.latency")
                .description("Execution latency for double-entry ledger transfers")
                .publishPercentiles(0.50, 0.95, 0.99)
                .publishPercentileHistogram()
                .minimumExpectedValue(Duration.ofMillis(1))
                .maximumExpectedValue(Duration.ofSeconds(5))
                .register(registry);

        this.transfersSuccessCounter = Counter.builder("diple.transfers.success")
                .description("Number of successful ledger transfers")
                .register(registry);

        this.transfersFailureCounter = Counter.builder("diple.transfers.failure")
                .description("Number of failed ledger transfers")
                .register(registry);

        this.idempotencyHitsCounter = Counter.builder("diple.idempotency.hits")
                .description("Number of idempotent duplicate requests replayed")
                .register(registry);

        this.idempotencyConflictsCounter = Counter.builder("diple.idempotency.conflicts")
                .description("Number of in-flight idempotency collisions (409)")
                .register(registry);

        this.invariantViolationsCounter = Counter.builder("diple.ledger.invariants.violations")
                .description("Number of ledger balance invariant violations detected")
                .register(registry);

        registry.gauge("diple.accounts.active", activeAccountsGauge);
    }

    public <T> T recordTransferLatency(Callable<T> callable) throws Exception {
        return transferLatencyTimer.recordCallable(callable);
    }

    public void recordTransferSuccess() {
        transfersSuccessCounter.increment();
    }

    public void recordTransferFailure() {
        transfersFailureCounter.increment();
    }

    public void recordIdempotencyHit() {
        idempotencyHitsCounter.increment();
    }

    public void recordIdempotencyConflict() {
        idempotencyConflictsCounter.increment();
    }

    public void recordInvariantViolation() {
        invariantViolationsCounter.increment();
    }

    public void updateActiveAccountsCount(long count) {
        activeAccountsGauge.set(count);
    }
}

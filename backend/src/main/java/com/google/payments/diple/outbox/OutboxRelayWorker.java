package com.google.payments.diple.outbox;

import com.google.payments.diple.domain.OutboxEvent;
import com.google.payments.diple.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxRelayWorker {

    private final OutboxEventRepository outboxEventRepository;
    private final EventBroker eventBroker;

    public static final String PAYMENT_TOPIC = "payment.transfers.v1";
    private static final int BATCH_SIZE = 50;

    @Scheduled(fixedDelayString = "${diple.outbox.relay-interval-ms:100}")
    @Transactional
    public void pollAndRelayEvents() {
        Instant now = Instant.now();
        List<OutboxEvent> pendingEvents = outboxEventRepository.findPendingEventsForRelay(
                now, PageRequest.of(0, BATCH_SIZE));

        if (pendingEvents.isEmpty()) {
            return;
        }

        for (OutboxEvent event : pendingEvents) {
            try {
                eventBroker.publish(PAYMENT_TOPIC, event.getAggregateId(), event.getPayload())
                        .thenAccept(v -> {
                            event.setStatus("PUBLISHED");
                            event.setPublishedAt(Instant.now());
                            outboxEventRepository.save(event);
                        })
                        .exceptionally(ex -> {
                            handleRelayFailure(event, ex);
                            return null;
                        });
            } catch (Exception ex) {
                handleRelayFailure(event, ex);
            }
        }
    }

    private void handleRelayFailure(OutboxEvent event, Throwable ex) {
        log.error("Outbox relay failed for eventId={}: {}", event.getId(), ex.getMessage());
        int nextRetry = event.getRetryCount() + 1;
        event.setRetryCount(nextRetry);
        event.setLastError(ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());

        if (nextRetry >= event.getMaxRetries()) {
            event.setStatus("DEAD_LETTER");
            log.error("Event escalated to DEAD_LETTER: eventId={}", event.getId());
        } else {
            // Exponential Backoff with Decorrelated Jitter: backoff = min(10000, rand(100, 500 * 2^retry))
            long maxBackoff = Math.min(10000L, (long) (500L * Math.pow(2, nextRetry)));
            long jitteredBackoff = ThreadLocalRandom.current().nextLong(100, Math.max(101, maxBackoff));
            event.setNextRetryAt(Instant.now().plusMillis(jitteredBackoff));
        }
        outboxEventRepository.save(event);
    }
}

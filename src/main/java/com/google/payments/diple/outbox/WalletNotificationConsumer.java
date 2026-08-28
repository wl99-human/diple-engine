package com.google.payments.diple.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
@RequiredArgsConstructor
@Slf4j
public class WalletNotificationConsumer {

    private final InMemoryEventBroker eventBroker;
    private final ObjectMapper objectMapper;

    public static final String DLQ_TOPIC = "payment.transfers.dlq";

    private final Set<String> processedTransactions = ConcurrentHashMap.newKeySet();
    private final AtomicLong consumedCount = new AtomicLong(0);
    private final AtomicLong dlqCount = new AtomicLong(0);

    @PostConstruct
    public void init() {
        eventBroker.subscribe(OutboxRelayWorker.PAYMENT_TOPIC, this::processPaymentEvent);
    }

    public void processPaymentEvent(String partitionKey, String payload) {
        consumedCount.incrementAndGet();
        try {
            JsonNode root = objectMapper.readTree(payload);
            String txId = root.path("transactionId").asText();

            if (txId == null || txId.isBlank()) {
                log.warn("Invalid event payload missing transactionId: {}", payload);
                routeToDlq(partitionKey, payload, "Missing transactionId");
                return;
            }

            // Consumer-side deduplication check (Guaranteeing Exactly-Once effect downstream)
            if (!processedTransactions.add(txId)) {
                log.info("Duplicate event ignored by consumer: txId={}", txId);
                return;
            }

            // Simulating Google Wallet Pass notification update
            String senderId = root.path("senderAccountId").asText();
            String receiverId = root.path("receiverAccountId").asText();
            long amount = root.path("amountMinorUnits").asLong();
            String currency = root.path("currency").asText();

            log.info("Google Wallet Notification dispatched: txId={}, sender={}, receiver={}, amount={}{}",
                    txId, senderId, receiverId, amount, currency);

        } catch (Exception ex) {
            log.error("Failed to process payment event: error={}", ex.getMessage());
            routeToDlq(partitionKey, payload, ex.getMessage());
        }
    }

    private void routeToDlq(String partitionKey, String payload, String reason) {
        dlqCount.incrementAndGet();
        log.warn("Routing unprocessable event to DLQ: key={}, reason={}", partitionKey, reason);
        eventBroker.publish(DLQ_TOPIC, partitionKey, payload);
    }

    public long getConsumedCount() {
        return consumedCount.get();
    }

    public long getDlqCount() {
        return dlqCount.get();
    }

    public void clear() {
        processedTransactions.clear();
        consumedCount.set(0);
        dlqCount.set(0);
    }
}

package com.google.payments.diple.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.payments.diple.domain.OutboxEvent;
import com.google.payments.diple.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxEventService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    /**
     * Atomically stages an event into outbox_events within the caller's transaction.
     */
    public OutboxEvent stageEvent(String aggregateType, String aggregateId, String eventType, Object payloadObj) {
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payloadObj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize outbox event payload", e);
        }

        OutboxEvent event = OutboxEvent.builder()
                .id("evt_" + UUID.randomUUID())
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .eventType(eventType)
                .payload(payloadJson)
                .status("PENDING")
                .retryCount(0)
                .maxRetries(4)
                .nextRetryAt(Instant.now())
                .build();

        return outboxEventRepository.save(event);
    }
}

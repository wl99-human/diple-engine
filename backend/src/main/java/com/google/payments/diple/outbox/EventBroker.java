package com.google.payments.diple.outbox;

import java.util.concurrent.CompletableFuture;

/**
 * High-performance event broker abstraction (Kafka, Cloud Pub/Sub, In-Memory).
 */
public interface EventBroker {

    /**
     * Publishes an event to a designated topic with partition key ordering.
     *
     * @param topic the topic identifier
     * @param partitionKey key used for FIFO partition hashing
     * @param payload the JSON message payload
     * @return CompletableFuture completing when message is acknowledged by broker
     */
    CompletableFuture<Void> publish(String topic, String partitionKey, String payload);
}

package com.google.payments.diple.outbox;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

@Component
@Slf4j
public class InMemoryEventBroker implements EventBroker {

    public record BrokerMessage(String topic, String partitionKey, String payload, long timestamp) {}

    private final ConcurrentHashMap<String, List<BiConsumer<String, String>>> subscribers = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<BrokerMessage> messageHistory = new CopyOnWriteArrayList<>();
    private final AtomicLong publishedCount = new AtomicLong(0);

    @Override
    public CompletableFuture<Void> publish(String topic, String partitionKey, String payload) {
        return CompletableFuture.runAsync(() -> {
            publishedCount.incrementAndGet();
            BrokerMessage msg = new BrokerMessage(topic, partitionKey, payload, System.currentTimeMillis());
            messageHistory.add(msg);

            // Limit in-memory history to 500 records
            if (messageHistory.size() > 500) {
                messageHistory.remove(0);
            }

            List<BiConsumer<String, String>> topicSubscribers = subscribers.getOrDefault(topic, List.of());
            for (BiConsumer<String, String> subscriber : topicSubscribers) {
                try {
                    subscriber.accept(partitionKey, payload);
                } catch (Exception ex) {
                    log.error("Error in topic '{}' subscriber for partitionKey={}: {}", topic, partitionKey, ex.getMessage());
                }
            }
        });
    }

    public void subscribe(String topic, BiConsumer<String, String> consumer) {
        subscribers.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>()).add(consumer);
    }

    public List<BrokerMessage> getMessageHistory() {
        return new ArrayList<>(messageHistory);
    }

    public long getPublishedCount() {
        return publishedCount.get();
    }

    public void clear() {
        messageHistory.clear();
        publishedCount.set(0);
    }
}

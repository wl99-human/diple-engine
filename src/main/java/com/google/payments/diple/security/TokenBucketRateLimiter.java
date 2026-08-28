package com.google.payments.diple.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class TokenBucketRateLimiter {

    private final long capacity;
    private final long refillRatePerSecond;

    private static class Bucket {
        long tokens;
        long lastRefillTimestamp;

        Bucket(long capacity) {
            this.tokens = capacity;
            this.lastRefillTimestamp = System.currentTimeMillis();
        }
    }

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public TokenBucketRateLimiter(
            @Value("${diple.security.rate-limit-per-second:1000}") long rateLimit) {
        this.capacity = rateLimit;
        this.refillRatePerSecond = rateLimit;
    }

    public synchronized boolean tryConsume(String key) {
        long now = System.currentTimeMillis();
        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(capacity));

        // Refill tokens
        long elapsedMs = now - bucket.lastRefillTimestamp;
        if (elapsedMs > 0) {
            long tokensToAdd = (elapsedMs * refillRatePerSecond) / 1000L;
            if (tokensToAdd > 0) {
                bucket.tokens = Math.min(capacity, bucket.tokens + tokensToAdd);
                bucket.lastRefillTimestamp = now;
            }
        }

        if (bucket.tokens > 0) {
            bucket.tokens--;
            return true;
        }
        return false;
    }

    public void clear() {
        buckets.clear();
    }
}

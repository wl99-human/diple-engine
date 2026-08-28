package com.google.payments.diple.idempotency;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class InMemoryDistributedLockManager implements DistributedLockManager {

    private record LockEntry(String ownerId, Instant expiresAt, long fencingToken) {}

    private final ConcurrentHashMap<String, LockEntry> locks = new ConcurrentHashMap<>();
    private final AtomicLong fencingTokenGenerator = new AtomicLong(1000L);

    @Override
    public Optional<Long> tryAcquire(String lockKey, String ownerId, Duration ttl) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(ttl);
        long fencingToken = fencingTokenGenerator.incrementAndGet();

        LockEntry newEntry = new LockEntry(ownerId, expiresAt, fencingToken);

        LockEntry previous = locks.compute(lockKey, (k, existing) -> {
            if (existing == null || existing.expiresAt().isBefore(now)) {
                return newEntry;
            }
            return existing; // Lock already held by active worker
        });

        if (previous == newEntry) {
            return Optional.of(fencingToken);
        }
        return Optional.empty();
    }

    @Override
    public boolean release(String lockKey, String ownerId) {
        return locks.computeIfPresent(lockKey, (k, existing) -> {
            if (existing.ownerId().equals(ownerId)) {
                return null; // Remove lock
            }
            return existing; // Don't release other worker's lock
        }) == null;
    }

    public void clear() {
        locks.clear();
    }
}

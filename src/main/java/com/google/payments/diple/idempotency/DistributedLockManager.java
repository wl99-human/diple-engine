package com.google.payments.diple.idempotency;

import java.time.Duration;
import java.util.Optional;

/**
 * Interface for distributed lock management with fencing tokens.
 * Backed by Redis in distributed topologies or In-Memory in standalone instances.
 */
public interface DistributedLockManager {

    /**
     * Attempts to acquire a distributed mutex lock for the specified key with TTL.
     *
     * @param lockKey the unique lock resource identifier
     * @param ownerId the unique identifier of the worker requesting the lock
     * @param ttl the duration before the lock automatically expires
     * @return an Optional containing a monotonic fencing token if acquired, or empty if busy
     */
    Optional<Long> tryAcquire(String lockKey, String ownerId, Duration ttl);

    /**
     * Releases the lock if and only if the current owner matches.
     *
     * @param lockKey the lock resource identifier
     * @param ownerId the worker identifier
     * @return true if successfully released
     */
    boolean release(String lockKey, String ownerId);
}

package com.google.payments.diple.common.domain;

/**
 * State machine stages for distributed idempotency keys.
 */
public enum IdempotencyStatus {
    PENDING,     // In-flight processing lock held by active worker
    COMPLETED,   // Successfully committed; cached response available
    FAILED       // Execution aborted; retry allowed
}

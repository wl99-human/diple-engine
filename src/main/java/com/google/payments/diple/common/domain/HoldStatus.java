package com.google.payments.diple.common.domain;

/**
 * Lifecycle state for Two-Phase Payment Holds (Authorizations).
 */
public enum HoldStatus {
    HELD,       // Funds reserved from available balance
    CAPTURED,   // Hold settled to posted balance and journaled
    RELEASED,   // Hold voided and funds restored to available balance
    EXPIRED     // Hold elapsed TTL and auto-released
}

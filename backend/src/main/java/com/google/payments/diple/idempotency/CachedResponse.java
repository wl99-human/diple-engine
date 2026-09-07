package com.google.payments.diple.idempotency;

public record CachedResponse(int statusCode, String responseBody) {}

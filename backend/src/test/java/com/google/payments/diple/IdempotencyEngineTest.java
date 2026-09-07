package com.google.payments.diple;

import com.google.payments.diple.common.exception.IdempotencyConflictException;
import com.google.payments.diple.common.exception.IdempotencyPayloadMismatchException;
import com.google.payments.diple.idempotency.CachedResponse;
import com.google.payments.diple.idempotency.IdempotencyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class IdempotencyEngineTest {

    @Autowired
    private IdempotencyService idempotencyService;

    @Test
    @DisplayName("Fresh key should acquire lock and return empty cache (allowed to proceed)")
    void testFreshKeyExecution() {
        String key = "test_key_" + UUID.randomUUID();
        String payload = "{\"sender\":\"acc_1\",\"amount\":100}";

        Optional<CachedResponse> cached = idempotencyService.checkAndLock(key, "client-1", "/transfers", payload);
        assertThat(cached).isEmpty();

        // Complete execution
        idempotencyService.commitResponse(key, 201, "{\"status\":\"SUCCESS\"}");

        // Second request with same key and payload should return cached response
        Optional<CachedResponse> replayed = idempotencyService.checkAndLock(key, "client-1", "/transfers", payload);
        assertThat(replayed).isPresent();
        assertThat(replayed.get().statusCode()).isEqualTo(201);
        assertThat(replayed.get().responseBody()).contains("SUCCESS");
    }

    @Test
    @DisplayName("Reusing key with mutated payload must throw IdempotencyPayloadMismatchException (HTTP 422)")
    void testPayloadTamperingDetection() {
        String key = "test_key_" + UUID.randomUUID();
        String originalPayload = "{\"sender\":\"acc_1\",\"amount\":100}";
        String tamperedPayload = "{\"sender\":\"acc_1\",\"amount\":9999}";

        idempotencyService.checkAndLock(key, "client-1", "/transfers", originalPayload);
        idempotencyService.commitResponse(key, 201, "{\"status\":\"SUCCESS\"}");

        assertThatThrownBy(() -> idempotencyService.checkAndLock(key, "client-1", "/transfers", tamperedPayload))
                .isInstanceOf(IdempotencyPayloadMismatchException.class)
                .hasMessageContaining("different request payload");
    }
}

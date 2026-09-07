package com.google.payments.diple.idempotency;

import com.google.payments.diple.common.domain.IdempotencyStatus;
import com.google.payments.diple.common.exception.IdempotencyConflictException;
import com.google.payments.diple.common.exception.IdempotencyPayloadMismatchException;
import com.google.payments.diple.domain.IdempotencyKeyRecord;
import com.google.payments.diple.repository.IdempotencyKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {

    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final DistributedLockManager distributedLockManager;

    private static final String WORKER_ID = "worker_" + UUID.randomUUID().toString().substring(0, 8);
    private static final Duration LOCK_TTL = Duration.ofSeconds(5);
    private static final Duration KEY_RETENTION = Duration.ofHours(24);

    public String computeRequestHash(String clientId, String uri, String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String raw = (clientId != null ? clientId : "anonymous") + "|" + uri + "|" + (payload != null ? payload : "");
            byte[] hashBytes = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm missing", e);
        }
    }

    /**
     * Evaluates the idempotency state for an incoming request.
     * Returns an existing CachedResponse if already completed and valid.
     * Returns Optional.empty() if caller is granted permission to proceed and execute.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<CachedResponse> checkAndLock(String idempotencyKey, String clientId, String uri, String payload) {
        String requestHash = computeRequestHash(clientId, uri, payload);
        String lockResource = "lock:idempotency:" + idempotencyKey;

        // 1. Fast-Path: Acquire Distributed Lock Mutex
        Optional<Long> fencingToken = distributedLockManager.tryAcquire(lockResource, WORKER_ID, LOCK_TTL);

        // 2. Check Database State
        Optional<IdempotencyKeyRecord> recordOpt = idempotencyKeyRepository.findById(idempotencyKey);

        if (recordOpt.isPresent()) {
            IdempotencyKeyRecord record = recordOpt.get();

            // Check for payload mutation / tampering
            if (!record.getRequestHash().equals(requestHash)) {
                fencingToken.ifPresent(token -> distributedLockManager.release(lockResource, WORKER_ID));
                throw new IdempotencyPayloadMismatchException(
                        idempotencyKey,
                        "Idempotency key '" + idempotencyKey + "' was already used with a different request payload.");
            }

            if (record.getStatus() == IdempotencyStatus.COMPLETED) {
                fencingToken.ifPresent(token -> distributedLockManager.release(lockResource, WORKER_ID));
                log.info("Idempotency cache hit for key={}. Returning cached response.", idempotencyKey);
                return Optional.of(new CachedResponse(
                        record.getResponseCode() != null ? record.getResponseCode() : 200,
                        record.getResponseBody() != null ? record.getResponseBody() : ""));
            }

            if (record.getStatus() == IdempotencyStatus.PENDING) {
                // If the record was acquired recently, it is in-flight
                if (record.getLockAcquiredAt() != null &&
                        record.getLockAcquiredAt().isAfter(Instant.now().minus(LOCK_TTL))) {
                    fencingToken.ifPresent(token -> distributedLockManager.release(lockResource, WORKER_ID));
                    throw new IdempotencyConflictException(
                            idempotencyKey,
                            "Transaction with idempotency key '" + idempotencyKey + "' is currently in-flight. Please retry shortly.");
                } else {
                    // Lease expired / crashed worker recovery: take over lock
                    log.warn("Recovering expired pending idempotency lease for key={}", idempotencyKey);
                    record.setLockOwner(WORKER_ID);
                    record.setLockAcquiredAt(Instant.now());
                    idempotencyKeyRepository.save(record);
                    return Optional.empty();
                }
            }
        }

        if (fencingToken.isEmpty()) {
            throw new IdempotencyConflictException(
                    idempotencyKey,
                    "Concurrent in-flight request detected for idempotency key '" + idempotencyKey + "'.");
        }

        // 3. Register PENDING record in DB
        IdempotencyKeyRecord newRecord = IdempotencyKeyRecord.builder()
                .idempotencyKey(idempotencyKey)
                .requestHash(requestHash)
                .clientId(clientId != null ? clientId : "anonymous")
                .status(IdempotencyStatus.PENDING)
                .lockOwner(WORKER_ID)
                .lockAcquiredAt(Instant.now())
                .expiresAt(Instant.now().plus(KEY_RETENTION))
                .build();

        idempotencyKeyRepository.save(newRecord);
        return Optional.empty();
    }

    /**
     * Commits response payload and marks idempotency record as COMPLETED.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void commitResponse(String idempotencyKey, int statusCode, String responseBody) {
        idempotencyKeyRepository.findById(idempotencyKey).ifPresent(record -> {
            record.setStatus(IdempotencyStatus.COMPLETED);
            record.setResponseCode(statusCode);
            record.setResponseBody(responseBody);
            idempotencyKeyRepository.save(record);
        });

        distributedLockManager.release("lock:idempotency:" + idempotencyKey, WORKER_ID);
    }

    /**
     * Marks record as FAILED and releases the lock so client can safely retry.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(String idempotencyKey) {
        idempotencyKeyRepository.findById(idempotencyKey).ifPresent(record -> {
            record.setStatus(IdempotencyStatus.FAILED);
            idempotencyKeyRepository.save(record);
        });

        distributedLockManager.release("lock:idempotency:" + idempotencyKey, WORKER_ID);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failExecution(String idempotencyKey) {
        markFailed(idempotencyKey);
    }
}

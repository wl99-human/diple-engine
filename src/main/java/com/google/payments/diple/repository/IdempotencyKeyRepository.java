package com.google.payments.diple.repository;

import com.google.payments.diple.common.domain.IdempotencyStatus;
import com.google.payments.diple.domain.IdempotencyKeyRecord;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKeyRecord, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM IdempotencyKeyRecord i WHERE i.idempotencyKey = :key")
    Optional<IdempotencyKeyRecord> findByIdForUpdate(@Param("key") String key);

    List<IdempotencyKeyRecord> findByExpiresAtBefore(Instant now);

    List<IdempotencyKeyRecord> findByStatusAndLockAcquiredAtBefore(IdempotencyStatus status, Instant threshold);
}

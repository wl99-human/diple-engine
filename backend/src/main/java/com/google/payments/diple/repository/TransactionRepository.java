package com.google.payments.diple.repository;

import com.google.payments.diple.domain.TransactionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<TransactionRecord, String> {

    Optional<TransactionRecord> findByIdempotencyKey(String idempotencyKey);
}

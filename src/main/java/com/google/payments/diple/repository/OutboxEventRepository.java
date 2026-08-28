package com.google.payments.diple.repository;

import com.google.payments.diple.domain.OutboxEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {

    @Query("SELECT o FROM OutboxEvent o WHERE o.status = 'PENDING' AND o.nextRetryAt <= :now ORDER BY o.createdAt ASC")
    List<OutboxEvent> findPendingEventsForRelay(@Param("now") Instant now, Pageable pageable);

    long countByStatus(String status);

    List<OutboxEvent> findTop20ByOrderByCreatedAtDesc();
}

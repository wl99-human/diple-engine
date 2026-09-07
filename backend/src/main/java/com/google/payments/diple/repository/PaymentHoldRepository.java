package com.google.payments.diple.repository;

import com.google.payments.diple.common.domain.HoldStatus;
import com.google.payments.diple.domain.PaymentHold;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentHoldRepository extends JpaRepository<PaymentHold, String> {

    Optional<PaymentHold> findByIdempotencyKey(String idempotencyKey);

    List<PaymentHold> findByAccountIdAndStatus(String accountId, HoldStatus status);

    List<PaymentHold> findByStatus(HoldStatus status);
}

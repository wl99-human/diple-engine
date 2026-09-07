package com.google.payments.diple;

import com.google.payments.diple.common.domain.AccountStatus;
import com.google.payments.diple.common.domain.AccountType;
import com.google.payments.diple.common.domain.Money;
import com.google.payments.diple.domain.Account;
import com.google.payments.diple.ledger.LedgerReconciliationService;
import com.google.payments.diple.ledger.LedgerTransferService;
import com.google.payments.diple.repository.AccountRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class PessimisticLockDeadlockTest {

    @Autowired
    private LedgerTransferService ledgerTransferService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private LedgerReconciliationService reconciliationService;

    @Test
    @DisplayName("High-concurrency circular transfers (A->B and B->A) must complete with zero deadlocks")
    void testConcurrentCircularTransfersNoDeadlock() throws InterruptedException {
        String accA = "deadlock_acc_a_" + UUID.randomUUID().toString().substring(0, 6);
        String accB = "deadlock_acc_b_" + UUID.randomUUID().toString().substring(0, 6);

        long initialBalance = 1_000_000L; // $10,000 each

        accountRepository.save(Account.builder()
                .id(accA).ownerId("u_a").accountType(AccountType.LIABILITY).currency("USD")
                .postedBalance(initialBalance).pendingDebits(0L).status(AccountStatus.ACTIVE).build());

        accountRepository.save(Account.builder()
                .id(accB).ownerId("u_b").accountType(AccountType.LIABILITY).currency("USD")
                .postedBalance(initialBalance).pendingDebits(0L).status(AccountStatus.ACTIVE).build());

        int totalTransfers = 100;
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(totalTransfers);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        for (int i = 0; i < totalTransfers; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    boolean aToB = (index % 2 == 0);
                    String sender = aToB ? accA : accB;
                    String receiver = aToB ? accB : accA;
                    String key = "deadlock_test_key_" + UUID.randomUUID();

                    ledgerTransferService.executeTransfer(
                            key, sender, receiver, Money.of(100L, "USD"), "ref_" + index, "Concurrent Transfer");

                    successCount.incrementAndGet();
                } catch (Exception ex) {
                    errorCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean finished = latch.await(20, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).isTrue();
        assertThat(successCount.get()).isEqualTo(totalTransfers);
        assertThat(errorCount.get()).isZero();

        // Verify total combined balance is 100% conserved
        Account finalA = accountRepository.findById(accA).orElseThrow();
        Account finalB = accountRepository.findById(accB).orElseThrow();

        long totalFinal = finalA.getPostedBalance() + finalB.getPostedBalance();
        assertThat(totalFinal).isEqualTo(initialBalance * 2);

        var audit = reconciliationService.runFullAudit();
        assertThat(audit.isDoubleEntryConserved()).isTrue();
        assertThat(audit.isMerkleChainIntact()).isTrue();
    }
}

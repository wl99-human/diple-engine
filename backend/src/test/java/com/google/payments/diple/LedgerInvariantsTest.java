package com.google.payments.diple;

import com.google.payments.diple.common.domain.AccountStatus;
import com.google.payments.diple.common.domain.AccountType;
import com.google.payments.diple.common.domain.Money;
import com.google.payments.diple.common.exception.InsufficientFundsException;
import com.google.payments.diple.domain.Account;
import com.google.payments.diple.domain.PaymentHold;
import com.google.payments.diple.ledger.LedgerReconciliationService;
import com.google.payments.diple.ledger.LedgerTransferService;
import com.google.payments.diple.ledger.PaymentHoldService;
import com.google.payments.diple.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class LedgerInvariantsTest {

    @Autowired
    private LedgerTransferService ledgerTransferService;

    @Autowired
    private PaymentHoldService paymentHoldService;

    @Autowired
    private LedgerReconciliationService reconciliationService;

    @Autowired
    private AccountRepository accountRepository;

    private String senderId;
    private String receiverId;

    @BeforeEach
    void setUp() {
        senderId = "test_user_a_" + UUID.randomUUID().toString().substring(0, 6);
        receiverId = "test_user_b_" + UUID.randomUUID().toString().substring(0, 6);

        Account a = Account.builder()
                .id(senderId)
                .ownerId("owner_a")
                .accountType(AccountType.LIABILITY)
                .currency("USD")
                .postedBalance(100_000L) // $1,000.00
                .pendingDebits(0L)
                .status(AccountStatus.ACTIVE)
                .build();

        Account b = Account.builder()
                .id(receiverId)
                .ownerId("owner_b")
                .accountType(AccountType.LIABILITY)
                .currency("USD")
                .postedBalance(50_000L) // $500.00
                .pendingDebits(0L)
                .status(AccountStatus.ACTIVE)
                .build();

        accountRepository.save(a);
        accountRepository.save(b);
    }

    @Test
    @DisplayName("Transfer must conserve total balance across sender and receiver")
    void testTransferConservation() {
        Money transferAmount = Money.of(25_000L, "USD"); // $250.00
        String key = "tx_key_" + UUID.randomUUID();

        ledgerTransferService.executeTransfer(key, senderId, receiverId, transferAmount, "ref_1", "Test transfer");

        Account updatedSender = accountRepository.findById(senderId).orElseThrow();
        Account updatedReceiver = accountRepository.findById(receiverId).orElseThrow();

        assertThat(updatedSender.getPostedBalance()).isEqualTo(75_000L);
        assertThat(updatedReceiver.getPostedBalance()).isEqualTo(75_000L);

        // System reconciliation audit check
        var report = reconciliationService.runFullAudit();
        assertThat(report.isDoubleEntryConserved()).isTrue();
        assertThat(report.isMerkleChainIntact()).isTrue();
    }

    @Test
    @DisplayName("Should prevent transfer exceeding available balance")
    void testInsufficientFundsGuard() {
        Money excessiveAmount = Money.of(200_000L, "USD"); // $2,000.00 (sender only has $1,000)
        String key = "tx_key_" + UUID.randomUUID();

        assertThatThrownBy(() ->
                ledgerTransferService.executeTransfer(key, senderId, receiverId, excessiveAmount, "ref_fail", "Overdraft test")
        ).isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    @DisplayName("Two-Phase Hold: Authorize -> Capture should properly adjust pending and posted balances")
    void testTwoPhaseHoldLifecycle() {
        Money holdAmount = Money.of(30_000L, "USD"); // $300.00
        String holdKey = "hold_key_" + UUID.randomUUID();

        PaymentHold hold = paymentHoldService.authorizeHold(
                holdKey, senderId, holdAmount, "order_123", "Reserve for checkout", Duration.ofMinutes(10));

        Account afterHold = accountRepository.findById(senderId).orElseThrow();
        assertThat(afterHold.getPostedBalance()).isEqualTo(100_000L);
        assertThat(afterHold.getPendingDebits()).isEqualTo(30_000L);
        assertThat(afterHold.getAvailableBalance()).isEqualTo(70_000L);

        // Capture hold to receiver
        String captureKey = "cap_key_" + UUID.randomUUID();
        paymentHoldService.captureHold(captureKey, hold.getId(), receiverId, "Settle checkout");

        Account afterCaptureSender = accountRepository.findById(senderId).orElseThrow();
        Account afterCaptureReceiver = accountRepository.findById(receiverId).orElseThrow();

        assertThat(afterCaptureSender.getPostedBalance()).isEqualTo(70_000L);
        assertThat(afterCaptureSender.getPendingDebits()).isEqualTo(0L);
        assertThat(afterCaptureSender.getAvailableBalance()).isEqualTo(70_000L);
        assertThat(afterCaptureReceiver.getPostedBalance()).isEqualTo(80_000L);
    }
}

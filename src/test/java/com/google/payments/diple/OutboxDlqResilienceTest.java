package com.google.payments.diple;

import com.google.payments.diple.common.domain.AccountStatus;
import com.google.payments.diple.common.domain.AccountType;
import com.google.payments.diple.common.domain.Money;
import com.google.payments.diple.domain.Account;
import com.google.payments.diple.domain.OutboxEvent;
import com.google.payments.diple.ledger.LedgerTransferService;
import com.google.payments.diple.outbox.InMemoryEventBroker;
import com.google.payments.diple.outbox.OutboxRelayWorker;
import com.google.payments.diple.outbox.WalletNotificationConsumer;
import com.google.payments.diple.repository.AccountRepository;
import com.google.payments.diple.repository.OutboxEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class OutboxDlqResilienceTest {

    @Autowired
    private LedgerTransferService ledgerTransferService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private OutboxRelayWorker outboxRelayWorker;

    @Autowired
    private InMemoryEventBroker eventBroker;

    @Autowired
    private WalletNotificationConsumer walletConsumer;

    @Test
    @DisplayName("Transactional Outbox: Transfer must stage event, and Relay worker must publish to broker")
    void testOutboxStagingAndRelay() throws InterruptedException {
        String sender = "outbox_sender_" + UUID.randomUUID().toString().substring(0, 6);
        String receiver = "outbox_recv_" + UUID.randomUUID().toString().substring(0, 6);

        accountRepository.save(Account.builder().id(sender).ownerId("u1").accountType(AccountType.LIABILITY).currency("USD").postedBalance(50_000L).status(AccountStatus.ACTIVE).build());
        accountRepository.save(Account.builder().id(receiver).ownerId("u2").accountType(AccountType.LIABILITY).currency("USD").postedBalance(10_000L).status(AccountStatus.ACTIVE).build());

        String key = "outbox_tx_key_" + UUID.randomUUID();
        ledgerTransferService.executeTransfer(key, sender, receiver, Money.of(1000L, "USD"), "outbox_ref", "Outbox Test");

        // Verify outbox record was created atomically
        List<OutboxEvent> events = outboxEventRepository.findAll();
        assertThat(events).isNotEmpty();

        // Run Outbox Relay Worker
        outboxRelayWorker.pollAndRelayEvents();

        // Wait brief moment for async broker dispatch
        Thread.sleep(200);

        assertThat(eventBroker.getPublishedCount()).isGreaterThan(0);
        assertThat(walletConsumer.getConsumedCount()).isGreaterThan(0);
    }
}

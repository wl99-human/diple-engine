package com.google.payments.diple;

import com.google.payments.diple.common.domain.AccountStatus;
import com.google.payments.diple.common.domain.AccountType;
import com.google.payments.diple.domain.Account;
import com.google.payments.diple.domain.JournalPosting;
import com.google.payments.diple.repository.AccountRepository;
import com.google.payments.diple.repository.JournalPostingRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.List;

@SpringBootApplication
@EnableScheduling
@EnableAsync
@Slf4j
public class DipleApplication {

    public static void main(String[] args) {
        SpringApplication.run(DipleApplication.class, args);
    }

    @Bean
    public CommandLineRunner initDatabase(
            AccountRepository accountRepository,
            JournalPostingRepository journalPostingRepository) {
        return args -> {
            log.info("Initializing DIPLE (Distributed Idempotent Payment & Ledger Engine)...");

            if (accountRepository.count() == 0) {
                // Central Settlement / Equity Account (System Treasury Genesis Reserve)
                Account centralSettlement = Account.builder()
                        .id("acc_central_settlement")
                        .ownerId("system_treasury")
                        .accountType(AccountType.EQUITY)
                        .currency("USD")
                        .postedBalance(10_000_000_000L) // $100,000,000 Treasury Genesis Reserve
                        .pendingDebits(0L)
                        .status(AccountStatus.ACTIVE)
                        .build();
                accountRepository.save(centralSettlement);

                // Initialize default accounts
                Account alice = Account.builder()
                        .id("acc_alice_01").ownerId("usr_alice").accountType(AccountType.LIABILITY)
                        .currency("USD").postedBalance(0L).pendingDebits(0L).status(AccountStatus.ACTIVE).build();

                Account bob = Account.builder()
                        .id("acc_bob_02").ownerId("usr_bob").accountType(AccountType.LIABILITY)
                        .currency("USD").postedBalance(0L).pendingDebits(0L).status(AccountStatus.ACTIVE).build();

                Account charlie = Account.builder()
                        .id("acc_charlie_03").ownerId("usr_charlie").accountType(AccountType.LIABILITY)
                        .currency("USD").postedBalance(0L).pendingDebits(0L).status(AccountStatus.ACTIVE).build();

                Account googleStore = Account.builder()
                        .id("merchant_google_store").ownerId("org_google_payments").accountType(AccountType.REVENUE)
                        .currency("USD").postedBalance(0L).pendingDebits(0L).status(AccountStatus.ACTIVE).build();

                Account platformFees = Account.builder()
                        .id("acc_platform_fees").ownerId("org_platform").accountType(AccountType.REVENUE)
                        .currency("USD").postedBalance(0L).pendingDebits(0L).status(AccountStatus.ACTIVE).build();

                accountRepository.saveAll(List.of(alice, bob, charlie, googleStore, platformFees));

                // Fund accounts with initial double-entry postings from central settlement
                fundAccount(alice, 500_000L, "acc_central_settlement", accountRepository, journalPostingRepository);
                fundAccount(bob, 200_000L, "acc_central_settlement", accountRepository, journalPostingRepository);
                fundAccount(charlie, 100_000L, "acc_central_settlement", accountRepository, journalPostingRepository);

                log.info("Initialized 6 accounts with double-entry balanced genesis postings.");
            }
        };
    }

    private void fundAccount(
            Account acc,
            long amount,
            String centralSettlementId,
            AccountRepository accountRepo,
            JournalPostingRepository journalRepo) {
        Account centralSettlement = accountRepo.findById(centralSettlementId).orElseThrow();
        acc.creditPostedBalance(amount);
        centralSettlement.debitPostedBalance(amount);
        accountRepo.save(acc);
        accountRepo.save(centralSettlement);

        String txId = "genesis_fund_" + acc.getId();

        // 1. Debit Central Settlement
        String prevCentralHash = journalRepo.findLatestPostingForAccount(centralSettlement.getId())
                .map(JournalPosting::getEntryHash).orElse("GENESIS_HASH_" + centralSettlement.getId());
        String centralHash = JournalPosting.computeHash(prevCentralHash, txId, centralSettlement.getId(), com.google.payments.diple.common.domain.PostingDirection.DEBIT, amount, centralSettlement.getPostedBalance());

        JournalPosting debitPosting = JournalPosting.builder()
                .transactionId(txId)
                .accountId(centralSettlement.getId())
                .direction(com.google.payments.diple.common.domain.PostingDirection.DEBIT)
                .amount(amount)
                .currency("USD")
                .entrySequence(1)
                .accountBalanceAfter(centralSettlement.getPostedBalance())
                .entryHash(centralHash)
                .build();

        // 2. Credit User Account
        String prevAccHash = journalRepo.findLatestPostingForAccount(acc.getId())
                .map(JournalPosting::getEntryHash).orElse("GENESIS_HASH_" + acc.getId());
        String accHash = JournalPosting.computeHash(prevAccHash, txId, acc.getId(), com.google.payments.diple.common.domain.PostingDirection.CREDIT, amount, acc.getPostedBalance());

        JournalPosting creditPosting = JournalPosting.builder()
                .transactionId(txId)
                .accountId(acc.getId())
                .direction(com.google.payments.diple.common.domain.PostingDirection.CREDIT)
                .amount(amount)
                .currency("USD")
                .entrySequence(2)
                .accountBalanceAfter(acc.getPostedBalance())
                .entryHash(accHash)
                .build();

        journalRepo.saveAll(List.of(debitPosting, creditPosting));
    }

    private static String computeSha256(String input) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

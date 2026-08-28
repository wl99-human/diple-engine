package com.google.payments.diple.api;

import com.google.payments.diple.api.dto.AccountDtos.AccountResponse;
import com.google.payments.diple.api.dto.AccountDtos.CreateAccountRequest;
import com.google.payments.diple.api.dto.PaymentDtos.JournalPostingResponse;
import com.google.payments.diple.common.domain.AccountStatus;
import com.google.payments.diple.common.domain.Money;
import com.google.payments.diple.common.exception.AccountNotFoundException;
import com.google.payments.diple.domain.Account;
import com.google.payments.diple.domain.JournalPosting;
import com.google.payments.diple.repository.AccountRepository;
import com.google.payments.diple.repository.JournalPostingRepository;
import com.google.payments.diple.telemetry.MetricsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountRepository accountRepository;
    private final JournalPostingRepository journalPostingRepository;
    private final MetricsService metricsService;

    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(@Valid @RequestBody CreateAccountRequest request) {
        Account account = Account.builder()
                .id(request.id())
                .ownerId(request.ownerId())
                .accountType(request.accountType())
                .currency(request.currency().toUpperCase())
                .postedBalance(request.initialBalanceMinorUnits())
                .pendingDebits(0L)
                .status(AccountStatus.ACTIVE)
                .partitionBucket(Math.abs(request.id().hashCode() % 10))
                .build();

        Account saved = accountRepository.save(account);
        metricsService.updateActiveAccountsCount(accountRepository.count());

        return ResponseEntity.status(HttpStatus.CREATED).body(mapToResponse(saved));
    }

    @GetMapping
    public ResponseEntity<List<AccountResponse>> listAccounts() {
        List<Account> accounts = accountRepository.findAll();
        metricsService.updateActiveAccountsCount(accounts.size());
        return ResponseEntity.ok(accounts.stream().map(this::mapToResponse).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AccountResponse> getAccount(@PathVariable("id") String id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + id));
        return ResponseEntity.ok(mapToResponse(account));
    }

    @GetMapping("/{id}/postings")
    public ResponseEntity<List<JournalPostingResponse>> getAccountPostings(@PathVariable("id") String id) {
        List<JournalPosting> postings = journalPostingRepository.findByAccountIdOrderByCreatedAtDesc(id);
        List<JournalPostingResponse> dtos = postings.stream()
                .map(p -> new JournalPostingResponse(
                        p.getId(),
                        p.getTransactionId(),
                        p.getAccountId(),
                        p.getDirection(),
                        p.getAmount(),
                        p.getCurrency(),
                        p.getEntrySequence(),
                        p.getAccountBalanceAfter(),
                        p.getEntryHash(),
                        p.getCreatedAt()
                ))
                .toList();
        return ResponseEntity.ok(dtos);
    }

    private AccountResponse mapToResponse(Account a) {
        Money money = a.getAvailableMoney();
        return new AccountResponse(
                a.getId(),
                a.getOwnerId(),
                a.getAccountType(),
                a.getCurrency(),
                a.getPostedBalance(),
                a.getPendingDebits(),
                a.getAvailableBalance(),
                a.getStatus(),
                money.toFormattedString()
        );
    }
}

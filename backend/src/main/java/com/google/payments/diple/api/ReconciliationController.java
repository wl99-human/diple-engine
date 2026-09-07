package com.google.payments.diple.api;

import com.google.payments.diple.ledger.LedgerReconciliationService;
import com.google.payments.diple.ledger.LedgerReconciliationService.SystemReconciliationReport;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/reconciliation")
@RequiredArgsConstructor
public class ReconciliationController {

    private final LedgerReconciliationService reconciliationService;

    @PostMapping("/run")
    public ResponseEntity<SystemReconciliationReport> runReconciliationAudit() {
        SystemReconciliationReport report = reconciliationService.runFullAudit();
        return ResponseEntity.ok(report);
    }

    @GetMapping("/status")
    public ResponseEntity<SystemReconciliationReport> getReconciliationStatus() {
        SystemReconciliationReport report = reconciliationService.runFullAudit();
        return ResponseEntity.ok(report);
    }
}

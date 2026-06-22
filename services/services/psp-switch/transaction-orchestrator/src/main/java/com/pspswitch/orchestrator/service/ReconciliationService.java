package com.pspswitch.orchestrator.service;

import com.pspswitch.orchestrator.adapter.LedgerService;
import com.pspswitch.orchestrator.adapter.NpciAdapter;
import com.pspswitch.orchestrator.adapter.NotificationService;
import com.pspswitch.orchestrator.model.*;
import com.pspswitch.orchestrator.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Reconciliation Service — resolves UNKNOWN transactions via NPCI re-query.
 *
 * Runs as a @Scheduled job every 60 seconds (fixedDelay, not fixedRate).
 * fixedDelay ensures the next sweep starts 60s AFTER the previous one finishes,
 * preventing sweep stacking if a sweep takes longer than 60 seconds.
 *
 * When the NPCI webhook callback never arrives within the 5-second timeout
 * window,
 * the transaction is marked UNKNOWN. This service picks up those transactions,
 * re-queries NPCI for the final status, and resolves them to SUCCESS or FAILED.
 *
 * Demo flow:
 * 1. POST /api/v1/control/npci-timeout?enabled=true (suppress webhook)
 * 2. POST /api/v1/txn → state goes PENDING → SUBMITTED → UNKNOWN
 * 3. POST /api/v1/control/npci-timeout?enabled=false (restore NPCI)
 * 4. GET /api/v1/control/reconcile-now → manual trigger
 * 5. GET /api/v1/txn/{txnId} → state=SUCCESS (resolved!)
 */
@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    private final TransactionRepository transactionRepository;
    private final TransactionStateService transactionStateService;
    private final NpciAdapter npciAdapter;
    private final LedgerService ledgerService;
    private final NotificationService notificationService;

    public ReconciliationService(TransactionRepository transactionRepository,
            TransactionStateService transactionStateService,
            NpciAdapter npciAdapter,
            LedgerService ledgerService,
            NotificationService notificationService) {
        this.transactionRepository = transactionRepository;
        this.transactionStateService = transactionStateService;
        this.npciAdapter = npciAdapter;
        this.ledgerService = ledgerService;
        this.notificationService = notificationService;
    }

    /**
     * Scheduled reconciliation sweep — runs every 60 seconds.
     * Finds all UNKNOWN transactions and re-queries NPCI for final status.
     *
     * fixedDelay=60000: next execution starts 60s after previous COMPLETES
     * (prevents overlapping sweeps if one takes longer than 60s)
     */
    @Scheduled(fixedDelay = 60000)
    public void reconcileUnknownTransactions() {
        // Step A: Query all UNKNOWN transactions from PostgreSQL
        List<TransactionEntity> unknowns = transactionRepository.findByState(TransactionState.UNKNOWN);

        log.info("[RECONCILIATION] Starting reconciliation sweep | found={} UNKNOWN transactions",
                unknowns.size());

        // Step B: If no UNKNOWN transactions, exit early
        if (unknowns.isEmpty()) {
            log.info("[RECONCILIATION] No UNKNOWN transactions found | sweep complete");
            return;
        }

        int resolved = 0;

        // Step C: For each UNKNOWN transaction, re-query NPCI
        for (TransactionEntity txnEntity : unknowns) {
            try {
                String tid = txnEntity.getTid();
                TransactionContext context = txnEntity.toContext();

                log.info("[RECONCILIATION] Querying NPCI for tid={} | tr={}", tid, context.getTr());

                // Call NPCI status query (simulated with 500ms delay)
                NpciCallbackPayload npciResponse = npciAdapter.queryStatus(tid);

                if ("00".equals(npciResponse.getResponseCode())) {
                    // ——— NPCI confirms SUCCESS ———
                    String arn = npciResponse.getApprovalRefNo();

                    // Update state → SUCCESS
                    context.setState(TransactionState.SUCCESS);
                    context.setApprovalRefNo(arn);
                    context.setResponseCode("00");
                    context.setFailureReason(null);
                    transactionStateService.update(context);

                    // Record in ledger
                    ledgerService.record(context, arn);

                    // Notify
                    notificationService.notify(tid, context.getPa(), context.getAm(), "SUCCESS");

                    log.info("[RECONCILIATION] tid={} | RESOLVED → SUCCESS | ARN={}", tid, arn);
                    resolved++;

                } else {
                    // ——— NPCI confirms FAILED ———
                    context.setState(TransactionState.FAILED);
                    context.setResponseCode(npciResponse.getResponseCode());
                    context.setFailureReason("Reconciliation: NPCI returned responseCode="
                            + npciResponse.getResponseCode());
                    transactionStateService.update(context);

                    notificationService.notifyFailure(tid, context.getPa(), context.getFailureReason());

                    log.info("[RECONCILIATION] tid={} | RESOLVED → FAILED | responseCode={}",
                            tid, npciResponse.getResponseCode());
                    resolved++;
                }

            } catch (Exception e) {
                // Log error but continue to next transaction — don't abort sweep
                log.error("[RECONCILIATION] tid={} | ERROR during reconciliation | {}",
                        txnEntity.getTid(), e.getMessage(), e);
            }
        }

        // Step D: Summary log
        log.info("[RECONCILIATION] Sweep complete | resolved={} transactions", resolved);
    }
}

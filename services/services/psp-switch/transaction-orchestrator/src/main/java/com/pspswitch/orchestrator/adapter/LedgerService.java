package com.pspswitch.orchestrator.adapter;

import com.pspswitch.orchestrator.model.LedgerEntity;
import com.pspswitch.orchestrator.model.TransactionContext;
import com.pspswitch.orchestrator.repository.LedgerRepository;
import com.pspswitch.orchestrator.service.DataCryptoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Ledger Service — Step 9 of the orchestration saga.
 *
 * Persists completed transactions to PostgreSQL via JPA (LedgerRepository).
 * Contains all fields needed for reconciliation: tid, tr, pa, pn, am,
 * approvalRefNo, mid, msid, mtid, and settledAt timestamp.
 *
 * PII fields (pa) are encrypted by DataCryptoService before persistence.
 */
@Service
public class LedgerService {

    private static final Logger log = LoggerFactory.getLogger(LedgerService.class);

    private final LedgerRepository repository;
    private final DataCryptoService dataCryptoService;

    public LedgerService(LedgerRepository repository, DataCryptoService dataCryptoService) {
        this.repository = repository;
        this.dataCryptoService = dataCryptoService;
    }

    /**
     * Records a completed transaction into the ledger (PostgreSQL).
     * Encrypts PII fields (pa) before persisting.
     */
    public void record(TransactionContext context, String approvalRefNo) {
        LedgerEntity entry = new LedgerEntity();
        entry.setTid(context.getTid());
        entry.setTr(context.getTr());
        entry.setPa(context.getPa());
        entry.setPn(context.getPn());
        entry.setAm(context.getAm());
        entry.setCu(context.getCu());
        entry.setApprovalRefNo(approvalRefNo);
        entry.setResponseCode("00");
        entry.setMid(context.getMid());
        entry.setMsid(context.getMsid());
        entry.setMtid(context.getMtid());
        entry.setSettledAt(Instant.now());

        // Encrypt PII before persisting to PostgreSQL
        entry.setPa(dataCryptoService.encrypt(entry.getPa()));
        entry.setPn(dataCryptoService.encrypt(entry.getPn()));
        entry.setMid(dataCryptoService.encrypt(entry.getMid()));

        repository.save(entry);

        log.info("[LEDGER] tid={} | tr={} | pa={} | am={} | approvalRefNo={} | RECORDED",
                context.getTid(), context.getTr(), context.getPa(), context.getAm(), approvalRefNo);
    }

    /**
     * Checks if a ledger entry exists for a given tid.
     */
    public boolean hasEntry(String tid) {
        return repository.existsById(tid);
    }

    /**
     * Retrieves a ledger entry by tid.
     * Decrypts PII fields after reading from PostgreSQL.
     */
    public LedgerEntity getEntry(String tid) {
        return repository.findById(tid)
                .map(entry -> {
                    // Decrypt PII after read
                    entry.setPa(dataCryptoService.decrypt(entry.getPa()));
                    entry.setPn(dataCryptoService.decrypt(entry.getPn()));
                    entry.setMid(dataCryptoService.decrypt(entry.getMid()));
                    return entry;
                })
                .orElse(null);
    }

    public int size() {
        return (int) repository.count();
    }

    public void clear() {
        repository.deleteAll();
    }
}

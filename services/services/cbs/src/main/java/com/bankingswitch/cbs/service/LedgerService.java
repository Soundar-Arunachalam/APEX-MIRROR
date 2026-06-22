package com.bankingswitch.cbs.service;

import com.bankingswitch.cbs.model.entity.TransactionLedger;
import com.bankingswitch.cbs.repository.TransactionLedgerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class LedgerService {

    private final TransactionLedgerRepository ledgerRepository;

    public LedgerService(TransactionLedgerRepository ledgerRepository) {
        this.ledgerRepository = ledgerRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordTransaction(String txnId, String vpa, String type, BigDecimal amount, BigDecimal before, BigDecimal after) {
        TransactionLedger ledger = TransactionLedger.builder()
                .txnId(txnId)
                .vpa(vpa)
                .type(type)
                .amount(amount)
                .balanceBefore(before)
                .balanceAfter(after)
                .timestamp(LocalDateTime.now())
                .build();
        ledgerRepository.save(ledger);
    }
}

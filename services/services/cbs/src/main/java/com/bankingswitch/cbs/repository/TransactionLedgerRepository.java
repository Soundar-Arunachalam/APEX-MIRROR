package com.bankingswitch.cbs.repository;

import com.bankingswitch.cbs.model.entity.TransactionLedger;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionLedgerRepository extends JpaRepository<TransactionLedger, Long> {
}

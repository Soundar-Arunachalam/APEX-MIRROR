package com.bankingswitch.npci.repository;

import com.bankingswitch.npci.model.entity.NpciTransactionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TransactionLogRepository extends JpaRepository<NpciTransactionLog, String> {
}

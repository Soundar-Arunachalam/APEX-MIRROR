package com.pspswitch.ledger.repository;

import com.pspswitch.ledger.entity.UpiTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UpiTransactionRepository extends JpaRepository<UpiTransaction, Long> {

    Optional<UpiTransaction> findByTid(String tid);
}

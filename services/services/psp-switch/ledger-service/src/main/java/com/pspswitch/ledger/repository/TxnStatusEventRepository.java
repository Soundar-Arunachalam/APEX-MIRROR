package com.pspswitch.ledger.repository;

import com.pspswitch.ledger.entity.TxnStatusEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TxnStatusEventRepository extends JpaRepository<TxnStatusEvent, Long> {

    List<TxnStatusEvent> findByTidOrderByOccurredAtAsc(String tid);
}

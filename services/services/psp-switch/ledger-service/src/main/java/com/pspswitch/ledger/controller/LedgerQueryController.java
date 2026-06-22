package com.pspswitch.ledger.controller;

import com.pspswitch.ledger.entity.UpiTransaction;
import com.pspswitch.ledger.repository.UpiTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/api/v1/ledger")
@RequiredArgsConstructor
public class LedgerQueryController {

    private final UpiTransactionRepository txnRepository;

    @GetMapping("/transactions/{tid}")
    public ResponseEntity<UpiTransaction> getTransactionByTid(@PathVariable String tid) {
        Optional<UpiTransaction> txn = txnRepository.findByTid(tid);
        return txn.map(ResponseEntity::ok)
                  .orElse(ResponseEntity.notFound().build());
    }
}

package com.hpe.upi.psp.controller;

import com.hpe.upi.psp.model.Transaction;
import com.hpe.upi.psp.service.PspService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/psp")
@CrossOrigin(origins = "*")
public class PspController {

    private final PspService pspService;

    public PspController(PspService pspService) {
        this.pspService = pspService;
    }

    @PostMapping("/pay")
    public ResponseEntity<Map<String, Object>> initiatePayment(@RequestBody Map<String, String> request) {
        String txnId = "TXN" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        String payerVpa = request.getOrDefault("payerVpa", "alice@sbi");
        String payeeVpa = request.getOrDefault("payeeVpa", "bob@hdfc");
        String payerBank = payerVpa.contains("@") ? payerVpa.split("@")[1].toUpperCase() : "SBI";
        String payeeBank = payeeVpa.contains("@") ? payeeVpa.split("@")[1].toUpperCase() : "HDFC";
        BigDecimal amount = new BigDecimal(request.getOrDefault("amount", "100"));
        boolean simulateFailure = Boolean.parseBoolean(request.getOrDefault("simulateFailure", "false"));

        Transaction txn = pspService.initiateTransaction(txnId, payerVpa, payeeVpa, payerBank, payeeBank, amount, simulateFailure);

        return ResponseEntity.ok(Map.of(
            "txnId", txn.getTxnId(),
            "rrn", txn.getRrn() != null ? txn.getRrn() : "",
            "status", txn.getStatus(),
            "message", "Transaction initiated and sent to NPCI Router"
        ));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "PSP-Switch"));
    }

    @GetMapping("/transactions")
    public ResponseEntity<Map<String, Object>> getTransactions() {
        return ResponseEntity.ok(Map.of(
            "transactions", pspService.getRecentTransactions(),
            "total", pspService.getRecentTransactions().size()
        ));
    }
}

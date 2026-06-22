package com.hpe.upi.cbs.messaging;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hpe.upi.cbs.service.CbsCreditService;
import com.hpe.upi.cbs.service.CbsDebitService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class CbsKafkaConsumer {

    private final CbsDebitService debitService;
    private final CbsCreditService creditService;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    public CbsKafkaConsumer(CbsDebitService debitService, CbsCreditService creditService) {
        this.debitService = debitService;
        this.creditService = creditService;
    }

    /** NPCI routes here for debit operation — writes to cbs_debit DB */
    @KafkaListener(topics = "upi.cbs.debit", groupId = "cbs-service")
    public void onDebitRequest(String message) {
        try {
            Map<String, Object> txn = mapper.readValue(message, new TypeReference<>() {});
            debitService.processDebit(txn);
        } catch (Exception e) {
            System.err.println("[CBS] Error processing debit request: " + e.getMessage());
        }
    }

    /** NPCI routes here for credit operation (after debit confirmed) — writes to cbs_credit DB */
    @KafkaListener(topics = "upi.cbs.credit", groupId = "cbs-service")
    public void onCreditRequest(String message) {
        try {
            Map<String, Object> txn = mapper.readValue(message, new TypeReference<>() {});
            creditService.processCredit(txn);
        } catch (Exception e) {
            System.err.println("[CBS] Error processing credit request: " + e.getMessage());
        }
    }

    /** NPCI initiates reversal after detecting credit bank failure — CBS credits payer back in cbs_debit DB */
    @KafkaListener(topics = "upi.cbs.reversal", groupId = "cbs-service")
    public void onReversalRequest(String message) {
        try {
            Map<String, Object> txn = mapper.readValue(message, new TypeReference<>() {});
            System.out.println("[CBS] REVERSAL REQUEST from NPCI for txn: " + txn.get("txnId"));
            debitService.processReversal(txn);
        } catch (Exception e) {
            System.err.println("[CBS] Error processing reversal request: " + e.getMessage());
        }
    }
}

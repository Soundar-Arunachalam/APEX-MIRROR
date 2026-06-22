package com.pspswitch.orchestrator.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pspswitch.orchestrator.adapter.CbsAdapter;
import com.pspswitch.orchestrator.adapter.LedgerService;
import com.pspswitch.orchestrator.model.*;
import com.pspswitch.orchestrator.service.IdempotencyService;
import com.pspswitch.orchestrator.service.TransactionStateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * Kafka consumer for topic {@code npci.inbound.response}.
 *
 * <p>Published by:
 * <ul>
 *   <li>npci-adapter — after receiving async RespPay/RespBalEnq from NPCI</li>
 *   <li>npci-response-consumer — after parsing XML callbacks from NPCI</li>
 * </ul>
 *
 * <p>This consumer replaces the mock {@code WebhookController.handleNpciCallback()}
 * path for production deployments. It performs the same saga completion logic:
 * <ol>
 *   <li>Cancels the pending NPCI timeout</li>
 *   <li>Branches on result: SUCCESS → SEND or COLLECT completion; FAILURE → FAILED state</li>
 *   <li>Writes ledger entry on success</li>
 *   <li>Publishes {@code SwitchCompletedEvent} to tpap-egress topic</li>
 * </ol>
 */
@Component
public class NpciResponseKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(NpciResponseKafkaConsumer.class);

    private final TransactionStateService stateService;
    private final IdempotencyService idempotencyService;
    private final CbsAdapter cbsAdapter;
    private final LedgerService ledgerService;
    private final SwitchCompletedEventProducer switchCompletedProducer;
    private final ObjectMapper objectMapper;

    // Shared timeout-future registry (same map used by TransactionOrchestrator)
    private final Map<String, ScheduledFuture<?>> timeoutFutures;

    public NpciResponseKafkaConsumer(TransactionStateService stateService,
                                      IdempotencyService idempotencyService,
                                      CbsAdapter cbsAdapter,
                                      LedgerService ledgerService,
                                      SwitchCompletedEventProducer switchCompletedProducer,
                                      ObjectMapper objectMapper) {
        this.stateService = stateService;
        this.idempotencyService = idempotencyService;
        this.cbsAdapter = cbsAdapter;
        this.ledgerService = ledgerService;
        this.switchCompletedProducer = switchCompletedProducer;
        this.objectMapper = objectMapper;
        this.timeoutFutures = new ConcurrentHashMap<>();
    }

    /**
     * Registers a timeout future so it can be cancelled when the NPCI response arrives.
     * Called by {@link com.pspswitch.orchestrator.orchestrator.TransactionOrchestrator}.
     */
    public void registerTimeoutFuture(String tid, ScheduledFuture<?> future) {
        timeoutFutures.put(tid, future);
    }

    /**
     * Consumes the NPCI inbound response event and completes the transaction saga.
     */
    @KafkaListener(topics = "${app.kafka.topic.npci-inbound-response}",
                   groupId = "psp-orchestrator-npci-response")
    public void consume(String message) {
        NpciInboundResponseEvent event;
        try {
            event = objectMapper.readValue(message, NpciInboundResponseEvent.class);
        } catch (Exception e) {
            log.error("[NPCI-RESPONSE-CONSUMER] Failed to deserialise message: {}", e.getMessage(), e);
            return;
        }

        String txnId = event.getTxnId();
        log.info("[NPCI-RESPONSE-CONSUMER] Received | txnId={} | type={} | result={}",
                txnId, event.getType(), event.getResult());

        // Cancel the timeout watchdog
        ScheduledFuture<?> timeout = timeoutFutures.remove(txnId);
        if (timeout != null) {
            timeout.cancel(false);
            log.info("[NPCI-RESPONSE-CONSUMER] Timeout cancelled | txnId={}", txnId);
        }

        TransactionContext context = stateService.getByTid(txnId);
        if (context == null) {
            log.warn("[NPCI-RESPONSE-CONSUMER] Unknown txnId={} — skipping", txnId);
            return;
        }

        try {
            if ("BALANCE".equalsIgnoreCase(event.getType())) {
                context.setState(event.isSuccess() ? TransactionState.SUCCESS : TransactionState.FAILED);
                if (!event.isSuccess()) context.setFailureReason("NPCI rejected: " + event.getResult());
                stateService.update(context);
                
                String key = idempotencyService.buildKey(context.getTr(), context.getPa() + ":BAL");
                idempotencyService.cacheResponse(key, TransactionResponse.fromContext(context));
                
                switchCompletedProducer.publishBalanceResult(context, event.getBalance(), event.getCurrency());
                log.info("[NPCI-RESPONSE-CONSUMER] BALANCE COMPLETE | tid={}", txnId);
                return;
            }
            
            if ("VPA_LOOKUP".equalsIgnoreCase(event.getType()) || "REQ_VPA".equalsIgnoreCase(event.getType())) {
                context.setState(event.isSuccess() ? TransactionState.SUCCESS : TransactionState.FAILED);
                if (!event.isSuccess()) context.setFailureReason("NPCI rejected: " + event.getResult());
                stateService.update(context);
                
                String key = idempotencyService.buildKey(context.getTr(), context.getPa() + ":VPA");
                idempotencyService.cacheResponse(key, TransactionResponse.fromContext(context));
                
                // Repurpose 'balance' field for payeeName if needed, or just pass empty string if not returned
                String name = event.getBalance() != null ? event.getBalance() : "";
                switchCompletedProducer.publish(SwitchCompletedEvent.forVpa(context, event.getResult(), name), txnId);
                log.info("[NPCI-RESPONSE-CONSUMER] VPA_LOOKUP COMPLETE | tid={}", txnId);
                return;
            }

            if (event.isSuccess()) {
                // Map NPCI result to approvalRefNo (use msgId as proxy if no ARN)
                context.setApprovalRefNo(event.getMsgId());
                context.setResponseCode("00");

                if (context.getFlowDirection() == FlowDirection.COLLECT) {
                    processCbsCreditAsync(context);
                } else {
                    completeSendFlow(context, event);
                }
            } else if (event.isTimeout()) {
                log.warn("[NPCI-RESPONSE-CONSUMER] TIMEOUT | txnId={}", txnId);
                context.setState(TransactionState.UNKNOWN);
                context.setFailureReason("NPCI timeout — no response received");
                context.setResponseCode("XT");
                stateService.update(context);
                cacheAndNotify(context);
            } else {
                log.info("[NPCI-RESPONSE-CONSUMER] FAILURE | txnId={} | errCode={}",
                        txnId, event.getErrCode());
                context.setState(TransactionState.FAILED);
                context.setResponseCode(event.getErrCode() != null && !event.getErrCode().isBlank()
                        ? event.getErrCode() : "ZM");
                context.setFailureReason("NPCI rejected: " + event.getResult()
                        + (event.getErrCode() != null ? " [" + event.getErrCode() + "]" : ""));
                stateService.update(context);
                cacheAndNotify(context);
            }
        } catch (Exception e) {
            log.error("[NPCI-RESPONSE-CONSUMER] Error completing saga | txnId={} | error={}",
                    txnId, e.getMessage(), e);
        }
    }

    // ── SEND flow ──────────────────────────────────────────────────────────────

    private void completeSendFlow(TransactionContext context, NpciInboundResponseEvent event) {
        String tid = context.getTid();

        ledgerService.record(context, context.getApprovalRefNo());

        context.setState(TransactionState.SUCCESS);
        stateService.update(context);

        cacheAndNotify(context);
        log.info("[NPCI-RESPONSE-CONSUMER] SEND COMPLETE | tid={} | state=SUCCESS", tid);
    }

    // ── COLLECT flow ───────────────────────────────────────────────────────────

    @Async("orchestratorExecutor")
    public void processCbsCreditAsync(TransactionContext context) {
        String tid = context.getTid();
        try {
            boolean cbsSuccess = cbsAdapter.creditPayee(
                    tid, context.getPa(), context.getAm(),
                    context.getMid(), context.getMsid(), context.getMtid());

            if (cbsSuccess) {
                ledgerService.record(context, context.getApprovalRefNo());
                context.setState(TransactionState.SUCCESS);
                stateService.update(context);
                cacheAndNotify(context);
                log.info("[NPCI-RESPONSE-CONSUMER] COLLECT COMPLETE | tid={} | state=SUCCESS", tid);
            } else {
                performCompensation(context);
            }
        } catch (Exception e) {
            log.error("[NPCI-RESPONSE-CONSUMER] CBS error | tid={}: {}", tid, e.getMessage(), e);
            performCompensation(context);
        }
    }

    private void performCompensation(TransactionContext context) {
        context.setState(TransactionState.COMPENSATED);
        context.setFailureReason("CBS credit failed — reversal sent to NPCI");
        stateService.update(context);
        cacheAndNotify(context);
        log.info("[NPCI-RESPONSE-CONSUMER] COMPENSATED | tid={}", context.getTid());
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private void cacheAndNotify(TransactionContext context) {
        String key = idempotencyService.buildKey(context.getTr(), context.getPa());
        idempotencyService.cacheResponse(key, TransactionResponse.fromContext(context));
        switchCompletedProducer.publishPaymentResult(context);
    }
}

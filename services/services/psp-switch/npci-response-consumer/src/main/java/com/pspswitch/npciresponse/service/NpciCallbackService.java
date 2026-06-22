package com.pspswitch.npciresponse.service;

import com.pspswitch.npciresponse.model.NpciInboundResponseEvent;
import com.pspswitch.npciresponse.parser.NpciXmlParser;
import com.pspswitch.npciresponse.producer.NpciResponseKafkaProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Core service that orchestrates the NPCI XML callback processing pipeline:
 * <ol>
 *   <li>Parse XML (via NpciXmlParser)</li>
 *   <li>Build NpciInboundResponseEvent</li>
 *   <li>Publish to Kafka (via NpciResponseKafkaProducer)</li>
 * </ol>
 *
 * <p>All processing happens {@code @Async} after the HTTP 200 Ack is returned
 * to NPCI, matching the UPI spec requirement that the callback endpoint
 * must respond quickly.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NpciCallbackService {

    private final NpciXmlParser xmlParser;
    private final NpciResponseKafkaProducer producer;

    // ═══════════════════════════════════════════════════════════════════
    // RespPay — Payment response callback
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Processes a RespPay XML callback from NPCI.
     * Runs asynchronously after HTTP Ack is sent.
     *
     * @param txnId PSP transaction ID from the URL path
     * @param xml   raw XML body from NPCI
     */
    @Async
    public void processRespPay(String txnId, String xml) {
        log.info("[CALLBACK-SERVICE] Processing RespPay | txnId={}", txnId);

        String result  = xmlParser.parseResult(xml);
        String errCode = xmlParser.parseErrCode(xml);
        String msgId   = xmlParser.parseMsgId(xml);

        if (result.isBlank()) {
            result = "FAILURE";
            log.warn("[CALLBACK-SERVICE] Could not parse result from RespPay XML | txnId={}", txnId);
        }

        if ("DEEMED".equalsIgnoreCase(result)) {
            log.warn("[CALLBACK-SERVICE] DEEMED result | txnId={} — treated as SUCCESS downstream", txnId);
        }

        NpciInboundResponseEvent event;
        if ("SUCCESS".equalsIgnoreCase(result) || "DEEMED".equalsIgnoreCase(result)) {
            event = NpciInboundResponseEvent.success(
                    txnId,
                    msgId.isBlank() ? UUID.randomUUID().toString() : msgId,
                    "PAY",
                    null
            );
            // Override result with actual (DEEMED passes through)
            event.setResult(result.toUpperCase());
        } else {
            event = NpciInboundResponseEvent.failure(
                    txnId,
                    msgId.isBlank() ? UUID.randomUUID().toString() : msgId,
                    "PAY",
                    errCode
            );
        }

        producer.publish(event);
        log.info("[CALLBACK-SERVICE] RespPay processed and published | txnId={} | result={}", txnId, result);
    }

    // ═══════════════════════════════════════════════════════════════════
    // RespBalEnq — Balance enquiry response
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Processes a RespBalEnq XML callback from NPCI.
     * Runs asynchronously.
     */
    @Async
    public void processRespBalEnq(String txnId, String xml) {
        log.info("[CALLBACK-SERVICE] Processing RespBalEnq | txnId={}", txnId);

        String result   = xmlParser.parseResult(xml);
        String errCode  = xmlParser.parseErrCode(xml);
        String msgId    = xmlParser.parseMsgId(xml);
        String balance  = xmlParser.parseBalance(xml);
        String currency = xmlParser.parseCurrency(xml);

        NpciInboundResponseEvent event;
        if ("SUCCESS".equalsIgnoreCase(result)) {
            event = NpciInboundResponseEvent.balance(
                    txnId,
                    msgId.isBlank() ? UUID.randomUUID().toString() : msgId,
                    balance,
                    currency
            );
        } else {
            event = NpciInboundResponseEvent.failure(
                    txnId,
                    msgId.isBlank() ? UUID.randomUUID().toString() : msgId,
                    "BALANCE",
                    errCode
            );
        }

        producer.publish(event);
        log.info("[CALLBACK-SERVICE] RespBalEnq processed | txnId={} | balance={} {}", txnId, balance, currency);
    }

    // ═══════════════════════════════════════════════════════════════════
    // Inbound COLLECT (NPCI → PSP push payment)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Processes an inbound ReqPay COLLECT callback from NPCI.
     * (NPCI-initiated pull payment — requires PSP acceptance.)
     */
    @Async
    public void processInboundCollect(String txnId, String xml) {
        log.info("[CALLBACK-SERVICE] Processing InboundCollect | txnId={}", txnId);

        String payerVpa = xmlParser.parsePayerVpa(xml);
        String payeeVpa = xmlParser.parsePayeeVpa(xml);
        String amount   = xmlParser.parseAmount(xml);
        String txnType  = xmlParser.parseTxnType(xml);
        String msgId    = xmlParser.parseMsgId(xml);

        String eventType = (txnType.isBlank() || "PAY".equalsIgnoreCase(txnType)) ? "COLLECT" : txnType.toUpperCase();

        NpciInboundResponseEvent event = NpciInboundResponseEvent.builder()
                .txnId(txnId)
                .msgId(msgId.isBlank() ? UUID.randomUUID().toString() : msgId)
                .type(eventType)
                .result("SUCCESS")
                .errCode("")
                .build();
        event.setTimestamp(java.time.Instant.now().toString());

        producer.publish(event);
        log.info("[CALLBACK-SERVICE] InboundCollect published | txnId={} | payer={} | payee={} | amount={}",
                txnId, payerVpa, payeeVpa, amount);
    }
}

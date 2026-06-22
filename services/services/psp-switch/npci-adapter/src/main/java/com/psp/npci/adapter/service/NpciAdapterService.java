package com.psp.npci.adapter.service;

import com.psp.npci.adapter.model.NpciInboundResponseEvent;
import com.psp.npci.adapter.model.NpciOutboundRequestEvent;
import com.psp.npci.adapter.producer.NpciResponseProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.UUID;

/**
 * Core orchestration service — purely event-driven, zero Redis dependency.
 *
 * <h2>Architecture</h2>
 * 
 * <pre>
 *  Kafka (npci.outbound.request)
 *      └─► NpciOutboundConsumer
 *              └─► NpciAdapterService.handlePay() / handleBalance()
 *                      └─► POST XML ─► Mock NPCI (:9090)
 *
 *  Mock NPCI callback ─► NpciWebhookController (REST)
 *      └─► NpciAdapterService.processRespPayAsync()     [@Async]
 *              └─► Kafka (npci.inbound.response)
 *                      └─► Orchestrator consumes ─► saga resolved
 * </pre>
 *
 * <p>
 * There is NO Redis. The Orchestrator reacts to Kafka events instead of polling
 * a shared state store. The Adapter is a pure Kafka → REST → Kafka bridge.
 *
 * <p>
 * Idempotency against duplicate NPCI callbacks is handled by
 * {@link IdempotencyService} (in-memory {@code ConcurrentHashMap}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NpciAdapterService {

    private final RestTemplate npciRestTemplate;
    private final SigningService signingService;
    private final EncryptionService encryptionService;
    private final XmlBuilderService xmlBuilderService;
    private final XmlParserService xmlParserService;
    private final NpciResponseProducer responseProducer;
    private final IdempotencyService idempotencyService;

    @Value("${npci.base-url}")
    private String npciBaseUrl;

    @Value("${npci.org-id}")
    private String orgId;

    // ═════════════════════════════════════════════════════════════════════════
    // FLOW A — PSP→NPCI PAY (async callback pattern)
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Triggered by Kafka event (type=PAY).
     *
     * <ol>
     * <li>Generate msgId</li>
     * <li>Encrypt MPIN</li>
     * <li>Build + sign ReqPay XML</li>
     * <li>POST to NPCI — receive Ack</li>
     * <li>Return — NPCI will fire RespPay callback asynchronously</li>
     * </ol>
     */
    public void handlePay(NpciOutboundRequestEvent event) {
        String txnId = event.getTxnId();
        String msgId = UUID.randomUUID().toString();

        log.info("[NPCI-ADAPTER] PAY started | txnId={} | payer={} | payee={} | amount={}",
                txnId, event.getPayerVpa(), event.getPayeeVpa(), event.getAmount());

        // Encrypt MPIN
        String encryptedMpin = encryptionService.encryptMpin(event.getPayerVpa());
        log.info("[NPCI-ADAPTER] MPIN encrypted | txnId={}", txnId);

        // Build XML
        String xml = xmlBuilderService.buildReqPay(
                txnId, msgId, event.getPayerVpa(), event.getPayeeVpa(),
                event.getAmount(), encryptedMpin, orgId);
        log.info("[NPCI-ADAPTER] ReqPay XML built | txnId={}", txnId);

        // Sign
        String signature = signingService.sign(xml);
        log.info("[NPCI-ADAPTER] XML signed | txnId={}", txnId);

        // POST to NPCI
        String url = npciBaseUrl + "/upi/ReqPay";
        log.info("[NPCI-ADAPTER] Calling NPCI ReqPay | url={} | txnId={}", url, txnId);

        try {
            ResponseEntity<String> response = npciRestTemplate.exchange(url, HttpMethod.POST,
                    new HttpEntity<>(xml, buildHeaders(signature)), String.class);

            log.info("[NPCI-ADAPTER] Ack received from NPCI | txnId={} | ack={}", txnId, response.getBody());
            log.info("[NPCI-ADAPTER] Waiting for RespPay callback | txnId={}", txnId);

        } catch (HttpClientErrorException | HttpServerErrorException ex) {
            log.error("[NPCI-ADAPTER] NPCI HTTP error on ReqPay | txnId={} | status={}",
                    txnId, ex.getStatusCode());
            publishResult(txnId, msgId, "PAY", "FAILURE", null, null, ex.getStatusCode().toString());

        } catch (ResourceAccessException ex) {
            log.error("[NPCI-ADAPTER] Timeout calling NPCI ReqPay | txnId={}", txnId);
            publishResult(txnId, msgId, "PAY", "TIMEOUT", null, null, "TIMEOUT");
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // FLOW A CALLBACK — NPCI RespPay async processing
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Called {@code @Async} from {@code NpciWebhookController} after the HTTP Ack
     * has already been sent back to NPCI. Runs on {@code npciAsyncExecutor}.
     *
     * <ol>
     * <li>Verify signature (WARN on mismatch, never reject in demo)</li>
     * <li>Parse result + errCode + msgId from RespPay XML</li>
     * <li>Publish to {@code npci.inbound.response} → Orchestrator consumes</li>
     * </ol>
     *
     * <p>
     * DEEMED is logged separately but published as-is so the Orchestrator
     * can apply its own domain rule (treat DEEMED = SUCCESS).
     */
    @Async("npciAsyncExecutor")
    public void processRespPayAsync(String txnId, String xml, String sigHeader) {

        // 3a — verify signature
        boolean sigOk = signingService.verify(xml, sigHeader);
        log.info("[NPCI-ADAPTER] Signature: {} | txnId={}", sigOk ? "PASS" : "WARN", txnId);

        // 3b — parse result from XML (msgId is echoed back by NPCI — no Redis needed)
        String result = xmlParserService.parseResult(xml);
        String errCode = xmlParserService.parseErrCode(xml);
        String msgId = xmlParserService.parseMsgId(xml); // NPCI echoes this back

        if (result.isBlank()) {
            result = "FAILURE";
            log.warn("[NPCI-ADAPTER] Could not parse result from RespPay XML | txnId={}", txnId);
        }

        log.info("[NPCI-ADAPTER] NPCI result={} errCode={} | txnId={}", result, errCode, txnId);

        if ("DEEMED".equalsIgnoreCase(result)) {
            log.warn("[NPCI-ADAPTER] DEEMED result — treated as SUCCESS downstream | txnId={}", txnId);
        }

        // 3c — mark idempotent
        idempotencyService.markProcessed(txnId, result);

        // 3d — publish Kafka → Orchestrator consumes, saga resolved
        publishResult(txnId, msgId.isBlank() ? UUID.randomUUID().toString() : msgId,
                "PAY", result, null, null, errCode);

        log.info("[NPCI-ADAPTER] Published result to Kafka | txnId={} | result={}", txnId, result);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // FLOW B — PSP→NPCI BALANCE ENQUIRY (synchronous)
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Triggered by Kafka event (type=BALANCE).
     *
     * <p>
     * Fully synchronous — NPCI replies in the same HTTP call.
     * Result is published to Kafka immediately.
     */
    public void handleBalance(NpciOutboundRequestEvent event) {
        String txnId = event.getTxnId();
        String msgId = UUID.randomUUID().toString();

        log.info("[NPCI-ADAPTER] BALANCE started | txnId={} | vpa={}", txnId, event.getPayerVpa());

        String encryptedMpin = encryptionService.encryptMpin(event.getPayerVpa());
        String xml = xmlBuilderService.buildReqBalEnq(txnId, msgId, event.getPayerVpa(), encryptedMpin, orgId);
        String signature = signingService.sign(xml);

        String url = npciBaseUrl + "/upi/ReqBalEnq";
        log.info("[NPCI-ADAPTER] Calling NPCI BalanceEnquiry | txnId={}", txnId);

        try {
            ResponseEntity<String> response = npciRestTemplate.exchange(url, HttpMethod.POST,
                    new HttpEntity<>(xml, buildHeaders(signature)), String.class);
            
            log.info("[NPCI-ADAPTER] Balance Enquiry Ack received | txnId={}", txnId);

        } catch (HttpClientErrorException | HttpServerErrorException ex) {
            log.error("[NPCI-ADAPTER] NPCI HTTP error on BalanceEnquiry | txnId={}", txnId);
            publishResult(txnId, msgId, "BALANCE", "FAILURE", null, null, ex.getStatusCode().toString());

        } catch (ResourceAccessException ex) {
            log.error("[NPCI-ADAPTER] Timeout on BalanceEnquiry | txnId={}", txnId);
            publishResult(txnId, msgId, "BALANCE", "TIMEOUT", null, null, "TIMEOUT");
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // FLOW D — PSP→NPCI VPA LOOKUP (synchronous)
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Triggered by Kafka event (type=VPA_LOOKUP).
     *
     * <p>
     * Fully synchronous — NPCI replies in the same HTTP call.
     * Result is published to Kafka immediately.
     */
    public void handleVpaLookup(NpciOutboundRequestEvent event) {
        String txnId = event.getTxnId();
        String msgId = UUID.randomUUID().toString();

        log.info("[NPCI-ADAPTER] VPA_LOOKUP started | txnId={} | vpa={}", txnId, event.getPayeeVpa());

        String xml = xmlBuilderService.buildReqVpa(txnId, msgId, event.getPayeeVpa(), orgId);
        String signature = signingService.sign(xml);

        String url = npciBaseUrl + "/upi/ReqValAdd";
        log.info("[NPCI-ADAPTER] Calling NPCI ReqValAdd | txnId={}", txnId);

        try {
            ResponseEntity<String> response = npciRestTemplate.exchange(url, HttpMethod.POST,
                    new HttpEntity<>(xml, buildHeaders(signature)), String.class);

            String respXml = response.getBody();

            // Verify response signature
            String respSig = response.getHeaders().getFirst("X-UPI-Signature");
            signingService.verify(respXml, respSig);

            // Parse synchronous response
            String result = xmlParserService.parseResult(respXml);
            String payeeName = xmlParserService.parsePayeeName(respXml);

            if (result.isBlank())
                result = "FAILURE";

            log.info("[NPCI-ADAPTER] VPA Lookup received | txnId={} | name={} | result={}",
                    txnId, payeeName, result);

            // Pass payeeName in the 'balance' field of the generic result event
            publishResult(txnId, msgId, "VPA_LOOKUP", result, payeeName, "", "");

            log.info("[NPCI-ADAPTER] VPA Lookup published to Kafka | txnId={}", txnId);

        } catch (HttpClientErrorException | HttpServerErrorException ex) {
            log.error("[NPCI-ADAPTER] NPCI HTTP error on ReqVpa | txnId={}", txnId);
            publishResult(txnId, msgId, "VPA_LOOKUP", "FAILURE", null, null, ex.getStatusCode().toString());

        } catch (ResourceAccessException ex) {
            log.error("[NPCI-ADAPTER] Timeout on ReqVpa | txnId={}", txnId);
            publishResult(txnId, msgId, "VPA_LOOKUP", "TIMEOUT", null, null, "TIMEOUT");
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // FLOW C — NPCI→PSP Inbound Collect (async after Ack)
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Called {@code @Async} from the webhook controller after the HTTP Ack is sent.
     *
     * <ol>
     * <li>Verify signature</li>
     * <li>Parse XML: payerVpa, payeeVpa, amount, type</li>
     * <li>Publish COLLECT event to Kafka → Orchestrator decides to
     * accept/reject</li>
     * </ol>
     */
    @Async("npciAsyncExecutor")
    public void processInboundCollectAsync(String txnId, String xml, String sigHeader) {

        signingService.verify(xml, sigHeader);

        String payerVpa = xmlParserService.parsePayerVpa(xml);
        String payeeVpa = xmlParserService.parsePayeeVpa(xml);
        String amount = xmlParserService.parseAmount(xml);
        String txnType = xmlParserService.parseTxnType(xml);
        String msgId = xmlParserService.parseMsgId(xml);

        String eventType = (txnType.isBlank() || "PAY".equalsIgnoreCase(txnType))
                ? "COLLECT"
                : txnType.toUpperCase();

        idempotencyService.markProcessed(txnId, eventType);

        NpciInboundResponseEvent collectEvent = NpciInboundResponseEvent.builder()
                .txnId(txnId)
                .msgId(msgId.isBlank() ? UUID.randomUUID().toString() : msgId)
                .type(eventType)
                .result("SUCCESS")
                .balance(null)
                .currency(null)
                .errCode("")
                .timestamp(Instant.now().toString())
                .build();

        responseProducer.publish(collectEvent);
        log.info("[NPCI-ADAPTER] Inbound collect published | txnId={} | payer={} | payee={} | amount={}",
                txnId, payerVpa, payeeVpa, amount);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Helpers
    // ═════════════════════════════════════════════════════════════════════════

    private HttpHeaders buildHeaders(String signature) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_XML);
        headers.set("X-UPI-Signature", signature);
        headers.set("X-PSP-OrgId", orgId);
        return headers;
    }

    private void publishResult(String txnId, String msgId, String type,
            String result, String balance, String currency, String errCode) {
        NpciInboundResponseEvent event = NpciInboundResponseEvent.builder()
                .txnId(txnId)
                .msgId(msgId)
                .type(type)
                .result(result)
                .balance(balance)
                .currency(currency)
                .errCode(errCode != null ? errCode.substring(0, Math.min(64, errCode.length())) : "")
                .timestamp(Instant.now().toString())
                .build();
        responseProducer.publish(event);
    }
}

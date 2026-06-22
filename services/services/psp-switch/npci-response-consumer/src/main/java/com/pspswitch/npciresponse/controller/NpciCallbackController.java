package com.pspswitch.npciresponse.controller;

import com.pspswitch.npciresponse.service.NpciCallbackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Receives XML callbacks from NPCI (or the NPCI simulator) and delegates
 * async processing to {@link NpciCallbackService}.
 *
 * <p>All endpoints immediately return HTTP 200 with an Ack body — this is
 * required by the UPI spec. Actual parsing and Kafka publish happen async.
 *
 * <h2>Endpoint mapping (mirrors previous NpciWebhookController in npci-adapter)</h2>
 * <ul>
 *   <li>POST /npci/callback/resp-pay/{txnId} — RespPay from NPCI</li>
 *   <li>POST /npci/callback/resp-bal-enq/{txnId} — RespBalEnq from NPCI</li>
 *   <li>POST /npci/callback/inbound-collect/{txnId} — Inbound ReqPay COLLECT</li>
 *   <li>POST /upi/RespPay/1.0/urn:txnid:{txnId} — Original UPI path (backward compat)</li>
 *   <li>POST /upi/ReqPay/1.0/urn:txnid:{txnId} — Inbound COLLECT UPI path</li>
 * </ul>
 *
 * <p>Configure the NPCI simulator's PSP_CALLBACK_URL to point at:
 *   {@code http://<npci-response-consumer-host>:8084}
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class NpciCallbackController {

    private final NpciCallbackService callbackService;

    // ═══════════════════════════════════════════════════════════════════
    // Clean REST paths (recommended for new deployments)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * RespPay callback — NPCI notifies PSP of payment result.
     */
    @PostMapping(
            value = "/npci/callback/resp-pay/{txnId}",
            consumes = {MediaType.APPLICATION_XML_VALUE, MediaType.TEXT_XML_VALUE, MediaType.TEXT_PLAIN_VALUE, "*/*"}
    )
    public ResponseEntity<Map<String, String>> handleRespPay(
            @PathVariable String txnId,
            @RequestBody String xmlBody,
            @RequestHeader(value = "X-UPI-Signature", required = false) String signature) {

        log.info("[CALLBACK-CTRL] RespPay received | txnId={} | bodyLen={}", txnId, xmlBody.length());
        callbackService.processRespPay(txnId, xmlBody);
        return ResponseEntity.ok(Map.of("status", "ACK", "txnId", txnId));
    }

    /**
     * RespBalEnq callback — NPCI returns balance enquiry result.
     */
    @PostMapping(
            value = "/npci/callback/resp-bal-enq/{txnId}",
            consumes = {MediaType.APPLICATION_XML_VALUE, MediaType.TEXT_XML_VALUE, MediaType.TEXT_PLAIN_VALUE, "*/*"}
    )
    public ResponseEntity<Map<String, String>> handleRespBalEnq(
            @PathVariable String txnId,
            @RequestBody String xmlBody,
            @RequestHeader(value = "X-UPI-Signature", required = false) String signature) {

        log.info("[CALLBACK-CTRL] RespBalEnq received | txnId={} | bodyLen={}", txnId, xmlBody.length());
        callbackService.processRespBalEnq(txnId, xmlBody);
        return ResponseEntity.ok(Map.of("status", "ACK", "txnId", txnId));
    }

    /**
     * Inbound COLLECT — NPCI-initiated pull payment request from payer's PSP.
     */
    @PostMapping(
            value = "/npci/callback/inbound-collect/{txnId}",
            consumes = {MediaType.APPLICATION_XML_VALUE, MediaType.TEXT_XML_VALUE, MediaType.TEXT_PLAIN_VALUE, "*/*"}
    )
    public ResponseEntity<Map<String, String>> handleInboundCollect(
            @PathVariable String txnId,
            @RequestBody String xmlBody) {

        log.info("[CALLBACK-CTRL] InboundCollect received | txnId={}", txnId);
        callbackService.processInboundCollect(txnId, xmlBody);
        return ResponseEntity.ok(Map.of("status", "ACK", "txnId", txnId));
    }

    // ═══════════════════════════════════════════════════════════════════
    // UPI spec paths (backward compat with npci-service simulator)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * UPI-spec RespPay path — backward compatible with NPCI simulator.
     * Maps: POST /upi/RespPay/1.0/urn:txnid:{txnId}
     */
    @PostMapping(
            value = "/upi/RespPay/1.0/urn:txnid:{txnId}",
            consumes = {MediaType.APPLICATION_XML_VALUE, MediaType.TEXT_XML_VALUE, "*/*"}
    )
    public ResponseEntity<Map<String, String>> handleUpiRespPay(
            @PathVariable String txnId,
            @RequestBody String xmlBody,
            @RequestHeader(value = "X-UPI-Signature", required = false) String signature) {

        log.info("[CALLBACK-CTRL] UPI RespPay received | txnId={}", txnId);
        callbackService.processRespPay(txnId, xmlBody);
        return ResponseEntity.ok(Map.of("status", "ACK", "txnId", txnId));
    }

    /**
     * UPI-spec inbound ReqPay (COLLECT) path.
     * Maps: POST /upi/ReqPay/1.0/urn:txnid:{txnId}
     */
    @PostMapping(
            value = "/upi/ReqPay/1.0/urn:txnid:{txnId}",
            consumes = {MediaType.APPLICATION_XML_VALUE, MediaType.TEXT_XML_VALUE, "*/*"}
    )
    public ResponseEntity<Map<String, String>> handleUpiInboundReqPay(
            @PathVariable String txnId,
            @RequestBody String xmlBody) {

        log.info("[CALLBACK-CTRL] UPI InboundCollect received | txnId={}", txnId);
        callbackService.processInboundCollect(txnId, xmlBody);
        return ResponseEntity.ok(Map.of("status", "ACK", "txnId", txnId));
    }

    // ═══════════════════════════════════════════════════════════════════
    // Health / info
    // ═══════════════════════════════════════════════════════════════════

    @GetMapping("/npci/callback/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("service", "npci-response-consumer", "status", "UP"));
    }
}

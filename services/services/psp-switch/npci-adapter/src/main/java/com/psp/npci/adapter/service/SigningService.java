package com.psp.npci.adapter.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Service;

/**
 * Handles signing and signature verification for outbound/inbound NPCI XML
 * messages.
 *
 * <h2>Demo vs Production</h2>
 * <p>
 * <b>Demo (current)</b>: SHA-256 hex digest of the XML bytes (UTF-8).
 * The signature is placed in the {@code X-UPI-Signature} HTTP header on every
 * outbound call. Inbound signatures are verified by recomputing SHA-256 and
 * comparing — mismatches log a WARN but are never rejected in demo mode.
 *
 * <p>
 * <b>Production</b>: Uses ECDSA P-256 with the PSP private key registered with
 * NPCI.
 * The interface ({@link #sign}/{@link #verify}) stays exactly the same — only
 * the
 * implementation body changes. No callers need to be modified.
 */
@Slf4j
@Service
public class SigningService {

    /**
     * Signs the given XML payload.
     *
     * <p>
     * Demo: computes SHA-256(xml bytes UTF-8) → lowercase hex string.
     * Production: ECDSA P-256 signature with PSP private key → Base64-encoded DER
     * bytes.
     *
     * @param xml the XML string to sign
     * @return the signature string to be placed in {@code X-UPI-Signature}
     */
    public String sign(String xml) {
        // DigestUtils.sha256Hex handles UTF-8 encoding internally
        String signature = DigestUtils.sha256Hex(xml);
        log.debug("[SIGNING] SHA-256 signature computed (first 16 chars): {}", signature.substring(0, 16));
        return signature;
    }

    /**
     * Verifies an inbound NPCI signature against the received XML payload.
     *
     * <p>
     * Recomputes SHA-256(xml) and compares with the provided signature.
     * In demo mode a mismatch logs a WARN but never throws — the transaction
     * continues processing to keep the demo flowing end-to-end.
     *
     * <p>
     * Production: verify ECDSA P-256 signature using the NPCI public certificate.
     *
     * @param xml       the raw XML body received from NPCI
     * @param signature the value of the {@code X-UPI-Signature} request header
     * @return {@code true} if signatures match, {@code false} otherwise
     */
    public boolean verify(String xml, String signature) {
        String computed = DigestUtils.sha256Hex(xml);
        boolean match = computed.equals(signature);
        if (match) {
            log.info("[SIGNING] Signature: PASS — inbound signature matches computed SHA-256");
        } else {
            log.warn("[SIGNING] Signature: WARN — mismatch (demo mode, ignoring). " +
                    "expected={} received={}", computed.substring(0, 16),
                    signature == null ? "null" : signature.substring(0, Math.min(16, signature.length())));
        }
        return match;
    }
}

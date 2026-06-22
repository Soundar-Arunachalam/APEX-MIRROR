package com.psp.npci.adapter.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight in-memory idempotency guard for inbound NPCI webhook callbacks.
 *
 * <p>
 * In a purely event-driven architecture the Adapter does NOT need Redis.
 * The Orchestrator reacts to Kafka events on {@code npci.inbound.response}
 * instead of polling a shared state store.
 *
 * <p>
 * The only state the Adapter must track is: "have I already processed this
 * txnId?" — to prevent duplicate Kafka publishes when NPCI retries a callback.
 * A {@link ConcurrentHashMap} is sufficient for this purpose in a
 * single-instance
 * deployment (or behind a sticky load balancer).
 *
 * <h2>Production note</h2>
 * <p>
 * For a multi-instance deployment, replace the in-memory map with a Redis SET
 * or a database unique-constraint check. The interface ({@link #markProcessed}
 * /
 * {@link #isAlreadyProcessed}) stays exactly the same.
 */
@Slf4j
@Service
public class IdempotencyService {

    /**
     * Stores txnIds that have been fully processed (result published to Kafka).
     * Value is the final result string (for debugging/logging convenience).
     */
    private final ConcurrentHashMap<String, String> processedTxns = new ConcurrentHashMap<>();

    /**
     * Returns {@code true} if this txnId was already processed.
     * If true, the caller must skip processing to prevent duplicate Kafka
     * publishes.
     */
    public boolean isAlreadyProcessed(String txnId) {
        boolean processed = processedTxns.containsKey(txnId);
        if (processed) {
            log.warn("[NPCI-ADAPTER] Duplicate callback detected — txnId={} already processed with result={}",
                    txnId, processedTxns.get(txnId));
        }
        return processed;
    }

    /**
     * Marks a txnId as processed with its final result.
     * Subsequent calls to {@link #isAlreadyProcessed} for this txnId return
     * {@code true}.
     */
    public void markProcessed(String txnId, String result) {
        processedTxns.put(txnId, result);
        log.debug("[IDEMPOTENCY] Marked txnId={} as processed | result={}", txnId, result);
    }
}

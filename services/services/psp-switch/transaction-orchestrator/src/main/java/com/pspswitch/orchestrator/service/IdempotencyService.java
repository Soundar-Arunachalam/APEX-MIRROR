package com.pspswitch.orchestrator.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pspswitch.orchestrator.model.TransactionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Idempotency Service — Step 1 of the orchestration saga.
 *
 * Uses Redis for distributed, durable idempotency checking.
 *
 * Lifecycle:
 * 1. claimSlot(key) → SETNX in Redis. Returns true if NEW, false if DUPLICATE.
 * 2. On NEW: stores "PROCESSING" marker with 1-hour TTL
 * 3. On completion: cacheResponse(key, response) stores the final JSON response
 * 4. On DUPLICATE: getCachedResponse(key) deserializes the cached response
 *
 * Thread-safety: guaranteed by Redis atomic SETNX operation.
 * TTL: 1 hour (configurable) — prevents stale keys from accumulating.
 *
 * Architecture rationale:
 * Redis is used instead of ConcurrentHashMap because:
 * - Survives service restarts
 * - Works across multiple instances (horizontal scaling)
 * - Sub-millisecond latency for duplicate detection
 */
@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);
    private static final String PROCESSING_MARKER = "PROCESSING";
    private static final String KEY_PREFIX = "idempotency:";
    private static final Duration TTL = Duration.ofHours(1);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public IdempotencyService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Builds the composite idempotency key from transaction reference and payee UPI
     * ID.
     */
    public String buildKey(String tr, String pa) {
        return tr + "::" + pa;
    }

    /**
     * Attempts to claim a processing slot for the given key.
     * Uses Redis SETNX (SET if Not eXists) — atomic operation.
     * Returns true if this is a NEW request, false if DUPLICATE.
     */
    public boolean claimSlot(String key) {
        String redisKey = KEY_PREFIX + key;
        Boolean claimed = redisTemplate.opsForValue().setIfAbsent(redisKey, PROCESSING_MARKER, TTL);

        if (Boolean.TRUE.equals(claimed)) {
            log.info("[IDEMPOTENCY] key={} | NEW_REQUEST | Slot claimed", key);
            return true;
        } else {
            log.info("[IDEMPOTENCY] key={} | DUPLICATE | Returning cached response", key);
            return false;
        }
    }

    /**
     * Checks if a key exists in Redis.
     */
    public boolean exists(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + key));
    }

    /**
     * Caches the final TransactionResponse for a completed transaction.
     * Serializes to JSON and stores in Redis with TTL.
     */
    public void cacheResponse(String key, TransactionResponse response) {
        try {
            String json = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(KEY_PREFIX + key, json, TTL);
            log.info("[IDEMPOTENCY] key={} | RESPONSE_CACHED | state={}", key, response.getState());
        } catch (JsonProcessingException e) {
            log.error("[IDEMPOTENCY] key={} | CACHE_ERROR | Failed to serialize response", key, e);
        }
    }

    /**
     * Retrieves the cached response for a duplicate request.
     * Deserializes from JSON. Returns null if key doesn't exist or is still
     * PROCESSING.
     */
    public TransactionResponse getCachedResponse(String key) {
        String json = redisTemplate.opsForValue().get(KEY_PREFIX + key);

        if (json == null || PROCESSING_MARKER.equals(json)) {
            return null;
        }

        try {
            return objectMapper.readValue(json, TransactionResponse.class);
        } catch (JsonProcessingException e) {
            log.error("[IDEMPOTENCY] key={} | DESERIALIZE_ERROR", key, e);
            return null;
        }
    }

    /**
     * Returns approximate count of idempotency keys (for status endpoint).
     */
    public int size() {
        // Note: KEYS is expensive in production — use SCAN or separate counter
        var keys = redisTemplate.keys(KEY_PREFIX + "*");
        return keys != null ? keys.size() : 0;
    }

    /**
     * Clears all idempotency keys — used for testing.
     */
    public void clear() {
        var keys = redisTemplate.keys(KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
}

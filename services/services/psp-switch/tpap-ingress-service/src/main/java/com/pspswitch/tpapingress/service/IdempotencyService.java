package com.pspswitch.tpapingress.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pspswitch.tpapingress.dto.response.AcceptedResponse;
import com.pspswitch.tpapingress.exception.IdempotencyStoreException;
import com.pspswitch.tpapingress.idempotency.IdempotencyRecord;
import com.pspswitch.tpapingress.idempotency.IdempotencyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Dual-layer idempotency store: Redis (fast path, TTL 24h) + PostgreSQL (durable fallback).
 * Key = SHA-256(tpapId + ":" + txnId).
 * See architecture_spec.md Section 5.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final StringRedisTemplate redisTemplate;
    private final IdempotencyRepository repository;
    private final ObjectMapper objectMapper;

    @Value("${app.idempotency.redis-ttl-hours:24}")
    private int redisTtlHours;

    /**
     * Computes the idempotency key: SHA-256(tpapId + ":" + txnId)
     */
    public static String computeKey(String tpapId, String txnId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((tpapId + ":" + txnId).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Checks Redis first, then PostgreSQL.
     * Returns the cached AcceptedResponse if this txnId was already processed.
     */
    public Optional<AcceptedResponse> isDuplicate(String idempotencyKey) {
        try {
            // Redis fast path
            String cached = redisTemplate.opsForValue().get("idempotency:" + idempotencyKey);
            if (cached != null) {
                AcceptedResponse response = objectMapper.readValue(cached, AcceptedResponse.class);
                response.setIdempotentReplay(true);
                return Optional.of(response);
            }

            // PostgreSQL fallback
            Optional<IdempotencyRecord> record = repository.findByIdempotencyKey(idempotencyKey);
            if (record.isPresent()) {
                AcceptedResponse response = objectMapper.readValue(
                        record.get().getResponseBody(), AcceptedResponse.class);
                response.setIdempotentReplay(true);
                // Warm Redis cache
                redisTemplate.opsForValue().set(
                        "idempotency:" + idempotencyKey,
                        record.get().getResponseBody(),
                        Duration.ofHours(redisTtlHours));
                return Optional.of(response);
            }

            return Optional.empty();
        } catch (JsonProcessingException e) {
            throw new IdempotencyStoreException("Failed to deserialize cached response");
        } catch (Exception e) {
            if (e instanceof IdempotencyStoreException) throw e;
            throw new IdempotencyStoreException("Idempotency store unreachable: " + e.getMessage());
        }
    }

    /**
     * Persists the idempotency key to both Redis and PostgreSQL.
     * Must succeed before Kafka publish.
     */
    public void persist(String idempotencyKey, AcceptedResponse response) {
        try {
            String json = objectMapper.writeValueAsString(response);

            // Redis write
            redisTemplate.opsForValue().set(
                    "idempotency:" + idempotencyKey,
                    json,
                    Duration.ofHours(redisTtlHours));

            // PostgreSQL write
            IdempotencyRecord record = IdempotencyRecord.builder()
                    .idempotencyKey(idempotencyKey)
                    .tpapId(extractTpapId(response.getTxnId()))
                    .txnId(response.getTxnId())
                    .eventType("REQUEST")
                    .correlationId(response.getCorrelationId())
                    .responseBody(json)
                    .createdAt(Instant.now())
                    .expiresAt(Instant.now().plus(Duration.ofHours(redisTtlHours)))
                    .build();
            repository.save(record);
        } catch (Exception e) {
            throw new IdempotencyStoreException("Failed to persist idempotency key: " + e.getMessage());
        }
    }

    private String extractTpapId(String txnId) {
        if (txnId != null && txnId.contains("-")) {
            return txnId.substring(0, txnId.indexOf('-'));
        }
        return "unknown";
    }
}

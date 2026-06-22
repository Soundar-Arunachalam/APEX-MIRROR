package com.pspswitch.orchestrator.service;

import com.pspswitch.orchestrator.model.TransactionContext;
import com.pspswitch.orchestrator.model.TransactionEntity;
import com.pspswitch.orchestrator.model.TransactionState;
import com.pspswitch.orchestrator.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Transaction State Service — manages TransactionContext lifecycle.
 *
 * Persists all state transitions to PostgreSQL via JPA (TransactionRepository).
 * Also maintains an in-memory ConcurrentHashMap for fast lookups during the
 * saga
 * (avoids hitting the DB on every webhook callback).
 *
 * Architecture rationale:
 * - PostgreSQL = durable state (survives restarts)
 * - ConcurrentHashMap = fast in-memory cache (saga performance)
 * - Both are updated on every state change
 *
 * IMPORTANT: The ConcurrentHashMap cache stores PLAINTEXT TransactionContext.
 * DataCryptoService encrypts ONLY the JPA entity before writing to PostgreSQL.
 * On read from DB, the entity is decrypted back to plaintext before caching.
 */
@Service
public class TransactionStateService {

    private static final Logger log = LoggerFactory.getLogger(TransactionStateService.class);

    private final TransactionRepository repository;
    private final DataCryptoService dataCryptoService;
    private final java.util.concurrent.ConcurrentHashMap<String, TransactionContext> cache = new java.util.concurrent.ConcurrentHashMap<>();

    public TransactionStateService(TransactionRepository repository,
            DataCryptoService dataCryptoService) {
        this.repository = repository;
        this.dataCryptoService = dataCryptoService;
    }

    /**
     * Saves a new TransactionContext (Step 5 — PENDING state).
     * Writes to both PostgreSQL (encrypted PII) and in-memory cache (plaintext).
     */
    public void save(TransactionContext context) {
        // Persist to PostgreSQL — encrypt PII fields before write
        TransactionEntity entity = TransactionEntity.fromContext(context);
        dataCryptoService.encryptEntity(entity);
        repository.save(entity);

        // Cache in memory (PLAINTEXT — not encrypted)
        cache.put(context.getTid(), context);

        log.info("[STATE] tid={} | SAVED | state={}", context.getTid(), context.getState());
    }

    /**
     * Updates an existing TransactionContext after a saga step changes the state.
     * Encrypts PII before writing to PostgreSQL, updates plaintext cache.
     */
    public void update(TransactionContext context) {
        context.setUpdatedAt(Instant.now());

        // Persist to PostgreSQL — encrypt PII fields before write
        TransactionEntity entity = TransactionEntity.fromContext(context);
        dataCryptoService.encryptEntity(entity);
        repository.save(entity);

        // Update cache (PLAINTEXT)
        cache.put(context.getTid(), context);

        log.info("[STATE] tid={} | UPDATED | state={}", context.getTid(), context.getState());
    }

    /**
     * Retrieves a TransactionContext by tid.
     * Checks in-memory cache first (plaintext), falls back to PostgreSQL (decrypt
     * on read).
     */
    public TransactionContext getByTid(String tid) {
        // Check cache first (PLAINTEXT)
        TransactionContext cached = cache.get(tid);
        if (cached != null) {
            return cached;
        }

        // Fall back to PostgreSQL — decrypt PII after read
        return repository.findById(tid)
                .map(entity -> {
                    dataCryptoService.decryptEntity(entity);
                    TransactionContext ctx = entity.toContext();
                    cache.put(tid, ctx); // warm the cache with plaintext
                    return ctx;
                })
                .orElse(null);
    }

    /**
     * Lookup by composite key (tr + pa).
     */
    public TransactionContext getByCompositeKey(String tr, String pa) {
        // Try cache first (plaintext)
        TransactionContext fromCache = cache.values().stream()
                .filter(ctx -> tr.equals(ctx.getTr()) && pa.equals(ctx.getPa()))
                .findFirst()
                .orElse(null);

        if (fromCache != null)
            return fromCache;

        // Fall back to PostgreSQL — note: pa in DB is encrypted, so this query
        // won't match encrypted values. This is a demo limitation.
        // In production, you'd use a deterministic hash index for lookups.
        return repository.findByTrAndPa(tr, pa)
                .map(entity -> {
                    dataCryptoService.decryptEntity(entity);
                    return entity.toContext();
                })
                .orElse(null);
    }

    /**
     * Counts transactions in a given state (from PostgreSQL for accuracy).
     */
    public long countByState(TransactionState state) {
        return repository.countByState(state);
    }

    /**
     * Returns total transaction count.
     */
    public int size() {
        return (int) repository.count();
    }

    /**
     * Clears both cache and PostgreSQL — used for testing.
     */
    public void clear() {
        cache.clear();
        repository.deleteAll();
    }
}

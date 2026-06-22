package com.pspswitch.orchestrator.service;

import com.pspswitch.orchestrator.model.TransactionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for IdempotencyService — now backed by Redis.
 * Requires Redis running on localhost:6379.
 */
@SpringBootTest
class IdempotencyServiceTest {

    @Autowired
    private IdempotencyService idempotencyService;

    @BeforeEach
    void setUp() {
        idempotencyService.clear();
    }

    @Test
    void buildKey_compositeFormat() {
        String key = idempotencyService.buildKey("ORD-001", "merchant@yesbank");
        assertEquals("ORD-001::merchant@yesbank", key);
    }

    @Test
    void claimSlot_newRequest() {
        boolean claimed = idempotencyService.claimSlot("ORD-T001::merchant@yesbank");
        assertTrue(claimed, "First claim should succeed");
    }

    @Test
    void claimSlot_duplicateRequest() {
        String key = "ORD-T002::merchant@yesbank";
        assertTrue(idempotencyService.claimSlot(key), "First claim should succeed");
        assertFalse(idempotencyService.claimSlot(key), "Second claim should fail (duplicate)");
    }

    @Test
    void cacheAndRetrieveResponse() {
        String key = "ORD-T003::merchant@yesbank";
        idempotencyService.claimSlot(key);

        TransactionResponse resp = new TransactionResponse();
        resp.setTxnId("PSP-ABCD1234");
        resp.setState("SUCCESS");

        idempotencyService.cacheResponse(key, resp);

        TransactionResponse cached = idempotencyService.getCachedResponse(key);
        assertNotNull(cached);
        assertEquals("PSP-ABCD1234", cached.getTxnId());
        assertEquals("SUCCESS", cached.getState());
    }

    @Test
    void getCachedResponse_returnsNullForProcessing() {
        String key = "ORD-T004::merchant@yesbank";
        idempotencyService.claimSlot(key);
        TransactionResponse cached = idempotencyService.getCachedResponse(key);
        assertNull(cached, "Should return null while still PROCESSING");
    }

    @Test
    void getCachedResponse_returnsNullForUnknownKey() {
        TransactionResponse cached = idempotencyService.getCachedResponse("NONEXISTENT::key");
        assertNull(cached, "Should return null for unknown key");
    }

    @Test
    void exists_tracksKeys() {
        String key = "ORD-T005::merchant@yesbank";
        assertFalse(idempotencyService.exists(key));
        idempotencyService.claimSlot(key);
        assertTrue(idempotencyService.exists(key));
    }

    @Test
    void clear_resetsStore() {
        idempotencyService.claimSlot("ORD-T006::a");
        idempotencyService.clear();
        assertFalse(idempotencyService.exists("ORD-T006::a"));
    }
}

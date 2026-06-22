package com.pspswitch.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pspswitch.orchestrator.adapter.CbsAdapter;
import com.pspswitch.orchestrator.adapter.LedgerService;
import com.pspswitch.orchestrator.adapter.NpciAdapter;
import com.pspswitch.orchestrator.model.TransactionResponse;
import com.pspswitch.orchestrator.service.IdempotencyService;
import com.pspswitch.orchestrator.service.TransactionStateService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Duration;

/**
 * Integration Tests — end-to-end tests for the Dual-Direction Transaction
 * Orchestrator.
 *
 * Uses @SpringBootTest + MockMvc + Awaitility for async state assertions.
 * H2 replaces PostgreSQL, real Redis on localhost:6379,
 * Kafka listener auto-startup disabled.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OrchestratorIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private NpciAdapter npciAdapter;
    @Autowired
    private CbsAdapter cbsAdapter;
    @Autowired
    private LedgerService ledgerService;
    @Autowired
    private IdempotencyService idempotencyService;
    @Autowired
    private TransactionStateService stateService;

    @BeforeEach
    void resetState() {
        npciAdapter.setFailureMode(false);
        npciAdapter.setSuppressWebhook(false);
        cbsAdapter.setFailureMode(false);
        idempotencyService.clear();
        stateService.clear();
        ledgerService.clear();
    }

    private Map<String, Object> validRequest(String tr) {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("tid", "TID-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        req.put("tr", tr);
        req.put("pa", "merchant@yesbank");
        req.put("pn", "Fresh Mart");
        req.put("mc", "5411");
        req.put("am", 500.00);
        req.put("mam", 100.00);
        req.put("cu", "INR");
        req.put("mode", "16");
        req.put("mid", "MID-001");
        req.put("msid", "STORE-01");
        req.put("mtid", "POS-01");
        req.put("isSignatureVerified", true);
        return req;
    }

    private String extractTxnId(MvcResult result) throws Exception {
        String json = result.getResponse().getContentAsString();
        TransactionResponse resp = objectMapper.readValue(json, TransactionResponse.class);
        return resp.getTxnId();
    }

    private void awaitState(String txnId, String expectedState) {
        await().atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    MvcResult r = mockMvc.perform(get("/api/v1/txn/" + txnId))
                            .andExpect(status().isOk())
                            .andReturn();
                    String json = r.getResponse().getContentAsString();
                    TransactionResponse resp = objectMapper.readValue(json, TransactionResponse.class);
                    assertEquals(expectedState, resp.getState());
                });
    }

    // TEST 1 — Happy path
    @Test
    @Order(1)
    void test01_happyPath() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/txn")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest("ORD-HP-001"))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.txnId").isNotEmpty())
                .andExpect(jsonPath("$.state").value("PENDING"))
                .andReturn();

        String txnId = extractTxnId(result);
        awaitState(txnId, "SUCCESS");
        assertTrue(ledgerService.hasEntry(txnId));

        MvcResult finalResult = mockMvc.perform(get("/api/v1/txn/" + txnId))
                .andExpect(status().isOk()).andReturn();
        TransactionResponse resp = objectMapper.readValue(
                finalResult.getResponse().getContentAsString(), TransactionResponse.class);
        assertNotNull(resp.getApprovalRefNo());
        assertTrue(resp.getApprovalRefNo().startsWith("ARN-"));
    }

    // TEST 2 — Duplicate request
    @Test
    @Order(2)
    void test02_duplicateRequest() throws Exception {
        Map<String, Object> req = validRequest("ORD-DUP-001");

        MvcResult first = mockMvc.perform(post("/api/v1/txn")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isAccepted()).andReturn();
        String txnId = extractTxnId(first);
        awaitState(txnId, "SUCCESS");

        mockMvc.perform(post("/api/v1/txn")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.txnId").value(txnId));
    }

    // TEST 3 — Amount below minimum
    @Test
    @Order(3)
    void test03_amountBelowMinimum() throws Exception {
        Map<String, Object> req = validRequest("ORD-MAM-001");
        req.put("am", 50.00);
        req.put("mam", 100.00);

        mockMvc.perform(post("/api/v1/txn")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.state").value("FAILED"))
                .andExpect(jsonPath("$.failureReason", containsString("minimum")));
    }

    // TEST 4 — NPCI failure
    @Test
    @Order(4)
    void test04_npciFailure() throws Exception {
        npciAdapter.setFailureMode(true);

        MvcResult result = mockMvc.perform(post("/api/v1/txn")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest("ORD-NF-001"))))
                .andExpect(status().isAccepted()).andReturn();

        String txnId = extractTxnId(result);
        awaitState(txnId, "FAILED");
        assertFalse(ledgerService.hasEntry(txnId));
    }

    // TEST 5 — COLLECT flow: CBS failure → COMPENSATED
    @Test
    @Order(5)
    void test05_collectCbsFailureCompensation() throws Exception {
        cbsAdapter.setFailureMode(true);

        Map<String, Object> req = validRequest("ORD-CBS-001");
        req.put("flowDirection", "COLLECT");

        MvcResult result = mockMvc.perform(post("/api/v1/txn")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isAccepted()).andReturn();

        String txnId = extractTxnId(result);
        awaitState(txnId, "COMPENSATED");
        assertFalse(ledgerService.hasEntry(txnId));
    }

    // TEST 6 — mode=04 requiresPasscode=true
    @Test
    @Order(6)
    void test06_mode04RequiresPasscode() throws Exception {
        Map<String, Object> req = validRequest("ORD-M04-001");
        req.put("mode", "04");
        req.put("isSignatureVerified", false);

        mockMvc.perform(post("/api/v1/txn")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.requiresPasscode").value(true));
    }

    // TEST 7 — mode=05 requiresPasscode=false
    @Test
    @Order(7)
    void test07_mode05NoPasscode() throws Exception {
        Map<String, Object> req = validRequest("ORD-M05-001");
        req.put("mode", "05");
        req.put("isSignatureVerified", true);

        mockMvc.perform(post("/api/v1/txn")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.requiresPasscode").value(false));
    }

    // TEST 8 — Missing tr
    @Test
    @Order(8)
    void test08_missingTr() throws Exception {
        Map<String, Object> req = validRequest("ORD-MISS-001");
        req.put("tr", "");

        mockMvc.perform(post("/api/v1/txn")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.failureReason", containsString("mandatory")));
    }

    // TEST 9 — Retry after SUCCESS
    @Test
    @Order(9)
    void test09_retryAfterSuccess() throws Exception {
        Map<String, Object> req = validRequest("ORD-RETRY-001");

        MvcResult first = mockMvc.perform(post("/api/v1/txn")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isAccepted()).andReturn();
        String txnId = extractTxnId(first);
        awaitState(txnId, "SUCCESS");

        mockMvc.perform(post("/api/v1/txn")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.txnId").value(txnId))
                .andExpect(jsonPath("$.state").value("SUCCESS"));
    }

    // TEST 10 — UNKNOWN on timeout
    @Test
    @Order(10)
    void test10_unknownOnTimeout() throws Exception {
        npciAdapter.setSuppressWebhook(true);

        MvcResult result = mockMvc.perform(post("/api/v1/txn")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest("ORD-TO-001"))))
                .andExpect(status().isAccepted()).andReturn();

        String txnId = extractTxnId(result);
        awaitState(txnId, "UNKNOWN");
    }

    // TEST 11 — Missing mid for mode=16
    @Test
    @Order(11)
    void test11_missingMidForMode16() throws Exception {
        Map<String, Object> req = validRequest("ORD-MID-001");
        req.put("mid", "");

        mockMvc.perform(post("/api/v1/txn")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.failureReason", containsString("terminal IDs")));
    }

    // TEST 12 — P2P flow (mc=0000)
    @Test
    @Order(12)
    void test12_p2pFlow() throws Exception {
        Map<String, Object> req = validRequest("ORD-P2P-001");
        req.put("mc", "0000");
        req.put("mode", "04");

        mockMvc.perform(post("/api/v1/txn")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.flowType").value("P2P"));
    }

    // TEST 13 — COLLECT flow: Happy path (CBS success)
    @Test
    @Order(13)
    void test13_collectHappyPath() throws Exception {
        Map<String, Object> req = validRequest("ORD-COLLECT-001");
        req.put("flowDirection", "COLLECT");

        MvcResult result = mockMvc.perform(post("/api/v1/txn")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.flowDirection").value("COLLECT"))
                .andReturn();

        String txnId = extractTxnId(result);
        awaitState(txnId, "SUCCESS");
        assertTrue(ledgerService.hasEntry(txnId));
    }
}

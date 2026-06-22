---
id: test-architecture
sidebar_position: 2
---

# Test Suite Architecture

## Technology Stack

| Tool | Role |
|------|------|
| **JUnit 5** | Test runner, `@Test`, `@Order`, `@BeforeEach` |
| **Spring Boot Test** | Full application context with real beans |
| **MockMvc** | HTTP-layer testing without starting a real server |
| **Awaitility** | Polls async state assertions without `Thread.sleep()` |
| **H2 In-Memory DB** | Replaces PostgreSQL during tests — zero setup |
| **Redis (localhost:6379)** | Real Redis required for idempotency tests |
| **Awaitility** | Waits for async Saga to complete before asserting final state |

---

## Configuration: `src/test/resources/application.properties`

The test profile swaps PostgreSQL for H2 and disables Kafka auto-startup:

```properties
# H2 replaces PostgreSQL during tests
spring.datasource.url=jdbc:h2:mem:testdb
spring.datasource.driver-class-name=org.h2.Driver

# Disable Kafka consumer auto-startup
spring.kafka.listener.auto-startup=false

# Disable encryption for test readability
crypto.enabled=false
```

---

## How Async Saga Testing Works

The Orchestrator is fully asynchronous. The HTTP response returns `202 PENDING` immediately, and the final state is written seconds later by background threads. Standard `assertEquals` would fail because it runs before the Saga finishes.

**Solution: Awaitility**

```java
private void awaitState(String txnId, String expectedState) {
    await().atMost(Duration.ofSeconds(10))
           .pollInterval(Duration.ofMillis(200))
           .untilAsserted(() -> {
               TransactionResponse resp = getTransaction(txnId);
               assertEquals(expectedState, resp.getState());
           });
}
```

This polls the `GET /api/v1/txn/{id}` endpoint every 200ms for up to 10 seconds, asserting that the final state eventually matches.

---

## The Race Condition We Discovered & Fixed

During testing we discovered a critical production-class race condition where two background threads were racing to update the transaction state:

**Root Cause:** `NpciAdapter.forward()` was calling its own `@Async fireWebhookAsync()` method internally. Because Spring's `@Async` proxy only intercepts external method calls, the webhook ran synchronously on the same thread, completed the full Saga flow to `SUCCESS`, and then the orchestrator thread regained control and overwrote the state back to `SUBMITTED`.

**Fix:**
```java
// BEFORE (broken — self-invocation bypasses Spring proxy)
fireWebhookAsync(tid);

// AFTER (correct — spawns truly independent JVM thread)
java.util.concurrent.CompletableFuture.runAsync(() -> fireWebhookAsync(tid));
```

This is a real-world Spring Boot gotcha that impacts thousands of production systems.

---

## Test Isolation Strategy

```java
@BeforeEach
void resetState() {
    npciAdapter.setFailureMode(false);   // Reset NPCI mock
    npciAdapter.setSuppressWebhook(false);
    cbsAdapter.setFailureMode(false);    // Reset CBS mock
    idempotencyService.clear();          // Flush Redis
    stateService.clear();               // Clear in-memory state
    ledgerService.clear();              // Clear audit log
}
```

Every test starts from a completely clean slate through in-memory mocks and real Redis flush.

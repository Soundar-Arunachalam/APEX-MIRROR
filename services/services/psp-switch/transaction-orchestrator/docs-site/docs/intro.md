---
id: intro
slug: /
sidebar_position: 1
---

# Introduction to Test-Driven Development

## What is TDD?

**Test-Driven Development (TDD)** is a software engineering discipline where you write the **test first**, before any implementation code exists.

The cycle is called **Red → Green → Refactor**:

```
🔴 RED     — Write a failing test that defines expected behaviour
🟢 GREEN   — Write the minimum code to make the test pass  
🔵 REFACTOR — Clean up the code while keeping all tests green
```

> "TDD is not about testing. It is about designing your system through your tests."

---

## Why TDD Matters in a Payment Switch

In a financial system like a UPI PSP Orchestrator, a single untested code change can:
- **Double-charge a customer** (missing idempotency check)
- **Lose a merchant's money** (missing compensation when CBS fails)
- **Leave transactions stuck** (missing timeout + reconciliation logic)

TDD gives us a **contractual guarantee**: the system will ALWAYS behave exactly as the test specification demands, regardless of who changes the code in the future.

---

## This Project's Test Results

| Metric | Value |
|--------|-------|
| **Total Tests** | 43 |
| **Failures** | 0 |
| **Errors** | 0 |
| **Test Execution Time** | ~44 seconds |
| **Test Framework** | JUnit 5 + Spring Boot Test + Awaitility |

---

## Test Classes

| Class | Tests | Purpose |
|-------|-------|---------|
| `OrchestratorIntegrationTest` | 13 | Full end-to-end Saga flows |
| `IdempotencyServiceTest` | 8 | Idempotency layer isolation |
| `ValidationServiceTest` | 14 | Validation rule isolation |
| `ModePreprocessingServiceTest` | 8 | Payment mode detection |

---

## Key Design Decisions Proven By Tests

1. **Async Saga** — NPCI webhook fires on a truly independent thread (fixed via `CompletableFuture.runAsync()` to avoid Spring proxy bypass)
2. **Idempotency** — Composite key `tr::pa` prevents double-spend on retries
3. **Compensation** — When CBS fails after NPCI succeeds, the system auto-reverses via NPCI
4. **Reconciliation** — Timeout guard catches UNKNOWN transactions for manual resolution

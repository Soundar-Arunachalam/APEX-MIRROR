---
id: edge-cases
sidebar_position: 5
---

# Edge Cases & Validation Tests

All 14 validation rule tests and 3 integration edge cases are covered here. The Validation Service applies **9 sequential rules** before any NPCI call is made, ensuring bad data is rejected synchronously at the API layer.

---

## Validation Service Tests (Unit Tests)

These run in complete isolation without any HTTP layer — just calling the service directly.

### Amount Validation (Rules 1–4)

| Test | Input | Expected Reason |
|------|-------|----------------|
| `TEST-001` | `am=0` | `Amount must be greater than zero` |
| `TEST-002` | `am=-1` | `Amount must be greater than zero` |
| `TEST-003` | `am=null` | `Amount must be greater than zero` |
| `TEST-004` | `am=10.999` | `Amount must have at most 2 decimal places` |

### Business Rule Validation (Rules 5–9)

| Test | Input | Expected Reason |
|------|-------|----------------|
| `TEST-005` | `am=50, mam=100` | `Amount 50.00 is below minimum 100.00` |
| `TEST-007` | `pa=""` | `Payee UPI ID is mandatory` |
| `TEST-008` | `pn=""` | `Payee name is mandatory` |
| `TEST-009` | `mc=""` | `Merchant category code is mandatory` |
| `TEST-010` | `cu="USD"` | `Only INR supported` |
| `TEST-011` | `tr=""` | `Transaction reference is mandatory` |
| `TEST-012` | `mode=16, mid=""` | `Merchant terminal IDs mandatory for mode 16` |

### Passing Tests

| Test | Scenario |
|------|----------|
| `TEST-013` | Minimum valid merchant transaction |
| `TEST-014` | Standard P2P transaction |
| `TEST-006` | mode=05 without terminal IDs (allowed) |

## Integration Edge Cases

To ensure the highest level of transaction safety, we didn't just test the "happy paths." Here are three critical edge cases we built integration tests for to prove our system enforces strict business rules before touching the database or external APIs:

### 1. The Minimum Amount Threshold
*   **The Scenario:** A merchant sets a minimum amount threshold (e.g., ₹100), but a customer tries to send only ₹50.
*   **How we handle it:** The Orchestrator intercepts this instantly. Instead of opening a database transaction or waking up the NPCI network, it synchronously rejects the request with an HTTP 400 Bad Request. 
*   **Why it matters:** This saves downstream processing power and prevents merchants from processing micro-transactions they explicitly blocked.

### 2. Missing Idempotency Keys (Transaction Reference)
*   **The Scenario:** The upstream TPAP forgets to include the `tr` (Transaction Reference) field in the payload.
*   **How we handle it:** The system detects that the core Intent-Based Idempotency key cannot be built. It instantly fails the transaction. 
:::note
By failing synchronously *before* persistence, we guarantee that we never accidentally save a "ghost" transaction to the database that we can't trace later!
:::

### 3. Missing Terminal IDs for PoS Payments (Mode 16)
*   **The Scenario:** A payment comes in with Mode `16` (UPI QR Code at a PoS Terminal), but it's missing the Store ID or Terminal ID.
*   **How we handle it:** The API validates the mode and rejects it immediately.
*   **Why it matters:** According to the official NPCI Specification, Mode 16 absolutely requires the Merchant ID, Store ID, and Terminal ID for settlement and dispute resolution. By enforcing this at the API layer, we guarantee strict compliance with NPCI regulations.

---

## Mode Preprocessing Tests

The `ModePreprocessingService` determines the `flowType` and `requiresPasscode` flag based on UPI payment mode:

| Mode | Description | requiresPasscode | flowType |
|------|-------------|-----------------|---------|
| `04` | Standard UPI (P2P or P2M with PIN) | `true` | MERCHANT or P2P |
| `05` | Tap & Pay / Intent (PIN-free below limit) | `false` | MERCHANT |
| `16` | QR Code at PoS Terminal | `false` | MERCHANT |
| `99` | Unknown (defaults to mode 16 behaviour) | `false` | MERCHANT |

The system gracefully handles unknown modes by defaulting to mode 16 behaviour and logging a warning — rather than crashing.

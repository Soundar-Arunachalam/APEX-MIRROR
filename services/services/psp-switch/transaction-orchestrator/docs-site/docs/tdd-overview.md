# Comprehensive TDD Test Coverage

Building a financial switch requires a bulletproof testing strategy. This document provides a high-level overview of our test-driven approach to the UPI Orchestrator. We employ a strict "Testing Pyramid," starting with granular unit tests and culminating in full end-to-end integration tests using an embedded Spring configuration.

Our test suite is split into **4 main test classes**, totaling **44 test scenarios**, designed to validate every edge case before the code ever touches production.

---

## 1. Unit Tests: Strict Request Validation
**(Class: `ValidationServiceTest.java` — 15 Tests)**

These tests act as our first line of defence. Before we ever allocate threads or hit a database, we ensure the structural integrity of the request.

- **Amount Integrity:** Tests missing amounts, negative amounts, too many decimal places, and values falling below the `mam` (Minimum Amount) threshold.
- **Payee Identification:** Ensures that VPA (`pa`), name (`pn`), and merchant code (`mc`) are explicitly defined.
- **Mode Contracts:** Asserts that Mode 16 (Dynamic QR) provides POS terminal strings (`mid`, `msid`, `mtid`), but doesn't punish pure P2P requests.

## 2. Unit Tests: Protocol Preprocessing
**(Class: `ModePreprocessingServiceTest.java` — 8 Tests)**

UPI uses modes (`04`, `05`, `16`) to dynamically determine routing logic. These tests assure that our routing map is impenetrable.

- **Passcode Requirements:** Proves that an unsigned `04` request strictly requires a `requiresPasscode=true` flag, while a signed `this-is-trusted` intent ignores the passcode barrier.
- **P2P vs P2M Routing:** Validates that `mc=0000` is forced down the P2P flow, but Mode `16` overrides all checks and defaults to Merchant flows.

## 3. Integration Tests: Concurrency & Locks
**(Class: `IdempotencyServiceTest.java` — 8 Tests)**

Financial ledgers cannot double-spend. This class uses a real embedded Redis instance to attack our locking mechanism with parallel requests.

- **Slot Claiming:** Ensures `tr::pa` pairs form a composite key that perfectly restricts duplicate entries.
- **Duplicate Prevention:** Simulates thread-racing logic where two identical requests arrive instantly; proves the second request is firmly rejected on the spot.
- **State Caching:** Confirms that once a request succeeds, the final response payload is securely cached and re-served, preventing wasted queries to NPCI.

## 4. End-to-End Tests: The Core Saga Orchestration
**(Class: `OrchestratorIntegrationTest.java` — 13 Tests)**

These tests tie everything together. They boot up a full Spring mock HTTP environment and simulate external outages to validate the system's absolute resilience.

### Happy Path Validation
- **P2M Sent Successfully:** Submits an honest request, waits for the asynchronous saga completion, and confirms exact 202 `PENDING` -> database `SUCCESS` lifecycles.
- **COLLECT Receiver Happy Path:** Proves that incoming requests directly trigger the CBS/Ledger to credit the payee before closing the loop with a 200 OK.

### Outage & Retry Defences
- **Idempotency Replay Simulation:** Submits a successful transaction, then violently fires the identical request again—Proving that the Orchestrator safely returns the exact same success response with an `X-Idempotent-Replayed` header, protecting the CBS from double credit.
- **Simulating NPCI Failure:** Proves the system gracefully updates to a `FAILED` state if the adapter receives an NPCI rejection string.
- **CBS / Bank Outages (Compensations):** In the COLLECT flow, proves that if the Ledger throws a timeout error during the credit transaction, the Orchestrator instantly triggers a Reversal and forces the state to `COMPENSATED`.
- **Complete Gateway Timeouts:** Simulates an environment where NPCI completely drops off the network with no webhook fired back—proving our watchdog timer wakes up after 5 seconds and safely marks the transaction as `UNKNOWN` for manual reconciliation.

---

### How to use this when explaining to your mentor:
> _"Architecturally, we implemented a Testing Pyramid because an Orchestrator's core job is **Confidence**. Our unit tests in the Validation and Preprocessing layer strip out malformed payloads defensively. The Redis lock testing guarantees we won't double-charge anyone in high-concurrency environments. The crown jewel is our OrchestratorIntegrationTest suite, which validates exactly what happens during network failures, such as when NPCI goes permanently offline or our Bank Ledger throws a runtime error. It proves our 'Compensated' and 'Unknown' fallback states are perfectly automated."_

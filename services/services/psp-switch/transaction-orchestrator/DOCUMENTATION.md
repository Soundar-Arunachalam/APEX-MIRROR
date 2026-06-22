# PSP Transaction Orchestrator — Complete Project Documentation

## Overview

A production-grade Spring Boot Transaction Orchestrator for a UPI PSP Switch. Implements a **10-step saga pattern** managing the full payment lifecycle with PostgreSQL persistence, Redis idempotency, Kafka event consumption, PII encryption, and automated reconciliation.

---

## Infrastructure

| Component | Purpose | Port |
|-----------|---------|------|
| **PostgreSQL 15** | Transaction state + Ledger persistence | 5432 |
| **Redis 7** | Idempotency cache (atomic SETNX) | 6379 |
| **Apache Kafka** | Event-driven consumption from Ingress Service | 9092 |
| **Spring Boot 3.2** | Application runtime (Java 17) | 8080 |

```bash
# Start everything
cd "d:\Transaction orc"
docker-compose up -d
mvn spring-boot:run
```

---

## State Machine

```
                    ┌──────────┐
                    │ PENDING  │  ← Step 5 (initial state)
                    └────┬─────┘
                         │  Step 6 (NPCI REST call)
                    ┌────▼─────┐
              ┌─────│SUBMITTED │──────────┐
              │     └────┬─────┘          │
              │          │                │ (5s timeout, no webhook)
         NPCI OK    NPCI FAIL            │
         (Step 7)   (Step 7)             │
              │          │                │
              ▼          ▼                ▼
        ┌──────────┐ ┌──────┐     ┌─────────┐
        │CBS Credit│ │FAILED│     │ UNKNOWN │
        │ (Step 8) │ └──────┘     └────┬────┘
        └────┬─────┘                   │
             │                    Reconciliation
        ┌────┴────┐              (re-query NPCI)
   CBS OK    CBS FAIL                  │
  (Step 9)  (Compensation)       ┌────┴────┐
        │         │          SUCCESS   FAILED
        ▼         ▼
   ┌────────┐ ┌───────────┐
   │SUCCESS │ │COMPENSATED│
   │(Step 10)│ │ (reversal) │
   └────────┘ └───────────┘
```

---

## 10-Step Saga

| Step | Name | Sync/Async | What Happens |
|------|------|-----------|--------------|
| 1 | Idempotency Check | Sync | Redis SETNX on `tr::pa` composite key |
| 2 | TID Generation | Sync | `PSP-` + 8-char uppercase UUID |
| 3 | Mode Preprocessing | Sync | Mode 04/05/16 → requiresPasscode, flowType |
| 4 | Validation | Sync | 9 sequential rules (amount, currency, fields) |
| 5 | Write PENDING | Sync | PostgreSQL + cache. **HTTP 202 returned here** |
| 6 | NPCI REST Call | Async | 800ms mock delay → state=SUBMITTED |
| 7 | NPCI Webhook | Async | 1500ms callback → SUCCESS or FAILED |
| 8 | CBS Credit | Async | 500ms mock → credit payee account |
| 9 | Ledger Write | Async | Record to PostgreSQL ledger_entries table |
| 10 | Finalise | Async | state=SUCCESS, cache response, notify |

---

## API Endpoints

### Transaction Endpoints
| Method | Endpoint | Description | Response |
|--------|----------|-------------|----------|
| POST | `/api/v1/txn` | Initiate payment | 202 (new) / 200 (duplicate) |
| GET | `/api/v1/txn/{txnId}` | Get state by tid | 200 / 404 |
| GET | `/api/v1/txn/ref?tr=X&pa=Y` | Lookup by composite key | 200 / 404 |

### Webhook Endpoints (internal)
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/webhook/npci` | NPCI callback (Steps 7-10) |
| POST | `/api/v1/webhook/cbs` | CBS confirmation (informational) |

### Control Endpoints (demo)
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/control/npci-failure?enabled=true` | Toggle NPCI failure |
| POST | `/api/v1/control/cbs-failure?enabled=true` | Toggle CBS failure |
| POST | `/api/v1/control/npci-timeout?enabled=true` | Suppress NPCI webhook |
| POST | `/api/v1/control/kafka-publish-test` | Publish test msg to Kafka |
| GET | `/api/v1/control/reconcile-now` | Manual reconciliation sweep |
| GET | `/api/v1/control/status` | Dashboard (toggles + counts) |

---

## Key Services

### IdempotencyService (Redis)
- **Composite key**: `tr::pa` (transaction reference + payee UPI ID)
- **Mechanism**: Redis `SETNX` (atomic Set-If-Not-Exists) with 1-hour TTL
- **Duplicate request**: Returns HTTP 200 + `X-Idempotent-Replayed: true` header

### DataCryptoService (AES-256)
- **Encrypts**: pa (payee UPI ID), pn (payee name), mid (merchant ID)
- **When**: Before every PostgreSQL write; decrypts after every read
- **Algorithm**: AES/ECB/PKCS5Padding (demo). Production: AES/GCM/NoPadding with per-record IV
- **Safety**: encrypt/decrypt never throw — return original value on error
- **Cache stores plaintext** — only PostgreSQL data is encrypted

### ReconciliationService (Scheduled)
- **Schedule**: `@Scheduled(fixedDelay = 60000)` — every 60s after previous sweep completes
- **Purpose**: Find UNKNOWN transactions, re-query NPCI (`queryStatus()`), resolve to SUCCESS or FAILED
- **Manual trigger**: `GET /api/v1/control/reconcile-now`

### TransactionStateService (PostgreSQL + Cache)
- **Dual-write**: JPA to PostgreSQL (durable) + ConcurrentHashMap (fast saga lookups)
- **Crypto touchpoints**: encrypt before save, decrypt after read

---

## Postman Demo Script

### Demo 1 — Happy Path
```
POST http://localhost:8080/api/v1/txn
Content-Type: application/json

{
  "tr": "ORD-001",
  "pa": "merchant@yesbank",
  "pn": "Fresh Mart",
  "mc": "5411",
  "am": 500.00,
  "mam": 100.00,
  "cu": "INR",
  "mode": "16",
  "mid": "MID-001",
  "msid": "STORE-01",
  "mtid": "POS-01",
  "isSignatureVerified": true
}

→ HTTP 202, note the txnId (e.g. PSP-AB12CD34)
```

```
GET http://localhost:8080/api/v1/txn/PSP-AB12CD34
→ state: PENDING → SUBMITTED → SUCCESS (poll every 1s)
```

### Demo 2 — Idempotency
```
POST http://localhost:8080/api/v1/txn   (same body as Demo 1)
→ HTTP 200
→ Header: X-Idempotent-Replayed: true
→ Same txnId returned
```

### Demo 3 — NPCI Failure
```
POST http://localhost:8080/api/v1/control/npci-failure?enabled=true
POST http://localhost:8080/api/v1/txn   (tr: "ORD-002")
GET  http://localhost:8080/api/v1/txn/{txnId}
→ state: FAILED, failureReason: "NPCI rejected: responseCode=ZM"
```

### Demo 4 — CBS Compensation
```
POST http://localhost:8080/api/v1/control/npci-failure?enabled=false
POST http://localhost:8080/api/v1/control/cbs-failure?enabled=true
POST http://localhost:8080/api/v1/txn   (tr: "ORD-003")
GET  http://localhost:8080/api/v1/txn/{txnId}
→ state: COMPENSATED, failureReason: "CBS credit failed — reversal sent to NPCI"
```

### Demo 5 — Reconciliation (UNKNOWN → SUCCESS)
```
POST http://localhost:8080/api/v1/control/cbs-failure?enabled=false
POST http://localhost:8080/api/v1/control/npci-timeout?enabled=true
POST http://localhost:8080/api/v1/txn   (tr: "ORD-004")
→ Wait 6 seconds...
GET  http://localhost:8080/api/v1/txn/{txnId}  →  state: UNKNOWN

POST http://localhost:8080/api/v1/control/npci-timeout?enabled=false
GET  http://localhost:8080/api/v1/control/reconcile-now
→ { "unknownBefore": 1, "resolved": 1, "unknownAfter": 0 }
GET  http://localhost:8080/api/v1/txn/{txnId}  →  state: SUCCESS ✓
```

### Demo 6 — PII Encryption
```
After any successful transaction:
Connect to PostgreSQL:  psql -h localhost -U orchestrator -d orchestrator
SELECT tid, pa, pn, mid FROM transactions;
→ pa, pn, mid columns show Base64 encrypted strings, NOT raw UPI IDs

GET http://localhost:8080/api/v1/txn/{txnId}
→ Response shows decrypted: pa="merchant@yesbank" (round-trip works)
```

### Demo 7 — Kafka (without Ingress Service)
```
POST http://localhost:8080/api/v1/control/kafka-publish-test
Content-Type: application/json
{ same body with tr: "ORD-005" }
→ Published to Kafka topic → Consumer picks up → Orchestrator processes
GET http://localhost:8080/api/v1/txn/ref?tr=ORD-005&pa=merchant@yesbank
→ state: SUCCESS
```

### Demo 8 — Validation Failure
```
POST http://localhost:8080/api/v1/txn
{ "tr":"ORD-006", "pa":"merchant@yesbank", "pn":"Fresh Mart",
  "mc":"5411", "am":50.00, "mam":100.00, "cu":"INR", "mode":"16",
  "mid":"MID-001", "msid":"STORE-01", "mtid":"POS-01",
  "isSignatureVerified":true }
→ HTTP 400
→ failureReason: "Amount 50.00 is below minimum 100.00"
```

### Demo 9 — Dashboard
```
GET http://localhost:8080/api/v1/control/status
→ Shows toggle states, transaction counts by state, crypto status
```

---

## Project Structure

```
d:\Transaction orc\
├── pom.xml                           Maven config (Spring Boot 3.2, Java 17)
├── docker-compose.yml                PostgreSQL, Redis, Kafka, Zookeeper
├── README.md                         Quick reference
│
├── src/main/resources/
│   └── application.properties        All connection configs + crypto key
│
├── src/main/java/com/pspswitch/orchestrator/
│   ├── OrchestratorApplication.java  Spring Boot main class
│   │
│   ├── config/
│   │   └── AsyncConfig.java          @EnableAsync + @EnableScheduling, 10-thread pool
│   │
│   ├── controller/
│   │   ├── TransactionController.java   POST /txn, GET /txn/{id}, GET /txn/ref
│   │   ├── WebhookController.java       NPCI + CBS webhook receivers
│   │   └── ControlController.java       Demo toggles, reconcile-now, Kafka publish
│   │
│   ├── orchestrator/
│   │   └── TransactionOrchestrator.java 10-step saga with timeout scheduler
│   │
│   ├── service/
│   │   ├── IdempotencyService.java      Redis SETNX, 1-hour TTL
│   │   ├── TransactionStateService.java JPA + ConcurrentHashMap dual-write + crypto
│   │   ├── ValidationService.java       9 sequential validation rules
│   │   ├── ModePreprocessingService.java Mode 04/05/16 logic
│   │   ├── DataCryptoService.java       AES-256 PII encryption at rest
│   │   └── ReconciliationService.java   @Scheduled UNKNOWN resolver
│   │
│   ├── adapter/
│   │   ├── NpciAdapter.java             Mock NPCI (forward, reversal, queryStatus)
│   │   ├── CbsAdapter.java             Mock CBS (creditPayee with failure toggle)
│   │   ├── LedgerService.java          PostgreSQL ledger with crypto
│   │   └── NotificationService.java    Mock push notifications
│   │
│   ├── kafka/
│   │   ├── PaymentRequestConsumer.java  @KafkaListener on upi.txn.requests
│   │   └── PaymentRequestProducer.java  Test message publisher
│   │
│   ├── model/
│   │   ├── TransactionState.java        Enum: PENDING/SUBMITTED/SUCCESS/FAILED/UNKNOWN/COMPENSATED
│   │   ├── UpiPaymentRequest.java       Request DTO (BigDecimal amounts)
│   │   ├── TransactionContext.java      Mutable saga context (in-memory)
│   │   ├── TransactionResponse.java     Response DTO
│   │   ├── TransactionEntity.java       JPA entity → transactions table
│   │   ├── LedgerEntity.java           JPA entity → ledger_entries table
│   │   ├── PreprocessingContext.java    Mode flags (requiresPasscode, flowType)
│   │   ├── NpciCallbackPayload.java     NPCI webhook DTO
│   │   └── CbsCallbackPayload.java      CBS webhook DTO
│   │
│   ├── repository/
│   │   ├── TransactionRepository.java   findByTrAndPa, findByState, countByState
│   │   └── LedgerRepository.java        CRUD for ledger_entries
│   │
│   └── exception/
│       ├── ValidationException.java     Custom exception with reason
│       └── GlobalExceptionHandler.java  400/500 error responses
│
└── src/test/
    ├── resources/application.properties H2 DB, crypto disabled, Kafka disabled
    └── java/com/pspswitch/orchestrator/
        ├── OrchestratorIntegrationTest.java  12 end-to-end tests
        └── service/
            ├── ValidationServiceTest.java     15 rule tests
            ├── IdempotencyServiceTest.java     8 Redis tests
            └── ModePreprocessingServiceTest.java 8 mode tests
```

---

## Design Decisions (Mentor Talking Points)

| Decision | Why | Production Upgrade |
|----------|-----|-------------------|
| BigDecimal for amounts | Financial precision, never float | Same |
| Redis SETNX for idempotency | Sub-ms atomic, survives restarts, horizontally scalable | Add TTL monitoring, cluster mode |
| JPA + ConcurrentHashMap | PostgreSQL=durability, HashMap=saga speed | Add Redis L2 cache |
| AES/ECB for crypto | Demo simplicity | AES/GCM/NoPadding + per-record IV + KMS key rotation |
| @Scheduled(fixedDelay) | fixedDelay prevents sweep stacking (vs fixedRate) | Distribute via ShedLock |
| Kafka consumer | Event-driven decoupling from Ingress | Add DLT (dead-letter topic) |
| volatile boolean toggles | Thread-safe without locks for simple flags | Feature flags service |
| Webhook simulation | Adapters call controller directly | Real HTTP calls to external NPCI |
| 5s NPCI timeout → UNKNOWN | Prevents indefinite SUBMITTED state | Configurable timeout + alerting |
| Compensation (NPCI reversal) | CBS fail after NPCI success = financial inconsistency | Saga log table for audit |

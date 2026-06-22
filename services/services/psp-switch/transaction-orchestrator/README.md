# PSP Transaction Orchestrator — UPI PSP Switch

A production-grade, demo-ready Spring Boot Transaction Orchestrator for a UPI PSP Switch.  
Implements a **10-step saga** with mock NPCI/CBS adapters, webhook callbacks, and production infrastructure.

## Infrastructure

| Component | Purpose | Port |
|-----------|---------|------|
| **PostgreSQL** | Transaction state + Ledger persistence | 5432 |
| **Redis** | Idempotency cache (SETNX atomic slot claim) | 6379 |
| **Apache Kafka** | Event-driven consumption from Ingress Service | 9092 |

## Quick Start

```bash
# 1. Start infrastructure
cd "d:\Transaction orc"
docker-compose up -d

# 2. Run the orchestrator
mvn spring-boot:run
```

Server starts on `http://localhost:8080`

## Run Tests

```bash
# Requires Redis running on localhost:6379
docker-compose up -d redis
mvn test
```

## Architecture

```
  ┌─────────────┐         ┌──────────────────┐
  │   Ingress   │────────▶│  Kafka Topic     │
  │   Service   │  publish│ upi.txn.requests │
  └─────────────┘         └────────┬─────────┘
                                    │ consume
  ┌─────────────┐                   ▼
  │  REST POST  │──────▶ ┌──────────────────────────────────────┐
  │  /api/v1/txn│        │     Transaction Orchestrator         │
  └─────────────┘        │                                      │
                          │  ┌────────┐  ┌─────────┐  ┌───────┐ │
                          │  │ Redis  │  │PostgreSQL│  │ Kafka │ │
                          │  │Idempot.│  │  State   │  │Consumer│ │
                          │  └────────┘  │  Ledger  │  └───────┘ │
                          │              └─────────┘             │
                          │                                      │
                          │  NPCI Adapter ──▶ Webhook Callback   │
                          │  CBS Adapter  ──▶ Webhook Callback   │
                          └──────────────────────────────────────┘
```

## State Machine

```
                    ┌──────────┐
                    │ PENDING  │
                    └────┬─────┘
                         │
                    ┌────▼─────┐
              ┌─────│SUBMITTED │─────┐
              │     └────┬─────┘     │
              │          │           │ (5s timeout)
         NPCI OK    NPCI FAIL       │
              │          │           │
              ▼          ▼           ▼
        ┌──────────┐ ┌──────┐ ┌─────────┐
        │CBS Credit│ │FAILED│ │ UNKNOWN │
        └────┬─────┘ └──────┘ └─────────┘
             │
        ┌────┴────┐
   CBS OK    CBS FAIL
        │         │
        ▼         ▼
   ┌────────┐ ┌───────────┐
   │SUCCESS │ │COMPENSATED│
   └────────┘ └───────────┘
```

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/txn` | Initiate payment (HTTP 202) |
| GET | `/api/v1/txn/{txnId}` | Get transaction state |
| GET | `/api/v1/txn/ref?tr={tr}&pa={pa}` | Lookup by composite key |
| POST | `/api/v1/webhook/npci` | NPCI webhook callback |
| POST | `/api/v1/webhook/cbs` | CBS webhook callback |
| POST | `/api/v1/control/npci-failure?enabled=true` | Toggle NPCI failure |
| POST | `/api/v1/control/cbs-failure?enabled=true` | Toggle CBS failure |
| POST | `/api/v1/control/npci-timeout?enabled=true` | Suppress NPCI webhook |
| POST | `/api/v1/control/kafka-publish-test` | Publish test msg to Kafka |
| GET | `/api/v1/control/status` | Show toggles + counts |

## Postman Demo Steps

### Step 1 — Happy Path
```
POST http://localhost:8080/api/v1/txn
Content-Type: application/json

{
  "tr":"ORD-001","pa":"merchant@yesbank","pn":"Fresh Mart",
  "mc":"5411","am":500.00,"mam":100.00,"cu":"INR","mode":"16",
  "mid":"MID-001","msid":"STORE-01","mtid":"POS-01",
  "isSignatureVerified":true
}
→ HTTP 202, note the txnId
```

### Step 2 — Poll Status
```
GET http://localhost:8080/api/v1/txn/{txnId}
→ Watch: PENDING → SUBMITTED → SUCCESS
```

### Step 3 — Duplicate Check
```
POST same body as Step 1 again
→ HTTP 200, Header: X-Idempotent-Replayed: true
```

### Step 4 — NPCI Failure
```
POST http://localhost:8080/api/v1/control/npci-failure?enabled=true
POST http://localhost:8080/api/v1/txn  (with "tr":"ORD-002")
GET  http://localhost:8080/api/v1/txn/{txnId}  → state=FAILED
```

### Step 5 — CBS Compensation
```
POST http://localhost:8080/api/v1/control/npci-failure?enabled=false
POST http://localhost:8080/api/v1/control/cbs-failure?enabled=true
POST http://localhost:8080/api/v1/txn  (with "tr":"ORD-003")
GET  http://localhost:8080/api/v1/txn/{txnId}  → state=COMPENSATED
```

### Step 6 — Validation Failure
```
POST http://localhost:8080/api/v1/txn
{ "tr":"ORD-004","pa":"merchant@yesbank","pn":"Fresh Mart",
  "mc":"5411","am":50.00,"mam":100.00,"cu":"INR","mode":"16",
  "mid":"MID-001","msid":"STORE-01","mtid":"POS-01",
  "isSignatureVerified":true }
→ HTTP 400, failureReason: "Amount 50.00 is below minimum 100.00"
```

### Step 7 — Kafka Demo (without Ingress Service)
```
POST http://localhost:8080/api/v1/control/cbs-failure?enabled=false
POST http://localhost:8080/api/v1/control/kafka-publish-test
  (same body with "tr":"ORD-005")
→ Message published to Kafka topic → Consumer picks it up → Orchestrator processes it
GET  http://localhost:8080/api/v1/txn/ref?tr=ORD-005&pa=merchant@yesbank
→ Watch state transitions
```

## Project Structure

```
com.pspswitch.orchestrator
├── controller/
│   ├── TransactionController.java      ← POST/GET endpoints
│   ├── WebhookController.java          ← NPCI + CBS webhook receivers
│   └── ControlController.java          ← Demo toggles + Kafka publish
├── orchestrator/
│   └── TransactionOrchestrator.java    ← 10-step saga
├── service/
│   ├── IdempotencyService.java         ← Redis SETNX atomic slot claim
│   ├── TransactionStateService.java    ← PostgreSQL + in-memory cache
│   ├── ModePreprocessingService.java   ← Mode 04/05/16 flags
│   └── ValidationService.java          ← 9 sequential rules
├── adapter/
│   ├── NpciAdapter.java                ← Mock REST + webhook simulation
│   ├── CbsAdapter.java                 ← Mock REST + webhook simulation
│   ├── LedgerService.java              ← PostgreSQL ledger
│   └── NotificationService.java        ← Mock notifications
├── kafka/
│   ├── PaymentRequestConsumer.java     ← @KafkaListener on upi.txn.requests
│   └── PaymentRequestProducer.java     ← Test message publisher
├── model/
│   ├── UpiPaymentRequest.java          ← Request DTO (BigDecimal)
│   ├── TransactionContext.java         ← Mutable saga context
│   ├── TransactionResponse.java        ← Response DTO
│   ├── TransactionState.java           ← 6-state enum
│   ├── TransactionEntity.java          ← JPA entity (PostgreSQL)
│   ├── LedgerEntity.java               ← JPA entity (PostgreSQL)
│   ├── PreprocessingContext.java        ← Mode flags
│   ├── NpciCallbackPayload.java        ← NPCI webhook DTO
│   └── CbsCallbackPayload.java         ← CBS webhook DTO
├── repository/
│   ├── TransactionRepository.java      ← Spring Data JPA
│   └── LedgerRepository.java           ← Spring Data JPA
├── config/
│   └── AsyncConfig.java                ← 10-thread executor + @EnableAsync
├── exception/
│   ├── ValidationException.java
│   └── GlobalExceptionHandler.java
└── OrchestratorApplication.java
```

## Key Design Decisions

| Decision | Rationale |
|----------|-----------|
| **BigDecimal for amounts** | Financial precision — never double/float |
| **PostgreSQL for state** | Durable, survives restarts, auditable |
| **Redis for idempotency** | Sub-ms atomicity via SETNX, horizontally scalable |
| **Kafka consumer** | Event-driven integration with Ingress Service |
| **@Async + ThreadPool** | POST returns 202 before NPCI call |
| **Webhook simulation** | Adapters call WebhookController directly |
| **ScheduledExecutorService** | 5s timeout → SUBMITTED → UNKNOWN |
| **volatile boolean failureMode** | Thread-safe demo toggles |
| **TransactionContext** | Single mutable object through entire saga |
| **JPA + ConcurrentHashMap** | Dual-write: durability + saga performance |

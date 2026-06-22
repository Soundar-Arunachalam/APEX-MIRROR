# NPCI Adapter Service

> **Role in APEX-UPI:** Bridges the PSP Switch internal world (Kafka) with the NPCI external network (REST + XML). It is the **only** service that calls NPCI outbound and the **only** service that exposes REST endpoints for NPCI callbacks.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                     PSP SWITCH (Internal)                           │
│                                                                     │
│   Orchestrator ──Kafka: npci.outbound.request──► NPCI Adapter      │
│   Orchestrator ◄─Kafka: npci.inbound.response── NPCI Adapter       │
└─────────────────────────────────────────────────────────────────────┘
                                          │  REST + XML (HTTP)
┌─────────────────────────────────────────┼───────────────────────────┐
│                   NPCI Network          ▼                           │
│                                    Mock NPCI (:9090)                │
└─────────────────────────────────────────────────────────────────────┘
```

**Design principles:**
- Kafka never crosses the PSP boundary — NPCI never sees Kafka
- Purely event-driven — no Redis, no polling
- Ack-first webhook pattern — HTTP 200 returned to NPCI before any processing
- In-memory idempotency guard prevents duplicate Kafka publishes on NPCI retries

---

## Port

| Service | Port |
|---|---|
| NPCI Adapter | **8081** |

---

## Transaction Flows

### Flow A — PAY (asynchronous callback)

```
Orchestrator
  → Kafka (npci.outbound.request, type=PAY)
    → Adapter builds ReqPay XML, signs, POSTs to NPCI
      → NPCI returns Ack (not the result)
        → ~2s later NPCI fires RespPay webhook → POST /upi/RespPay/...
          → Adapter: Ack returned immediately, result parsed @Async
            → Kafka (npci.inbound.response, result=SUCCESS|FAILURE|TIMEOUT)
              → Orchestrator resolves saga
```

### Flow B — BALANCE ENQUIRY (synchronous)

```
Orchestrator
  → Kafka (npci.outbound.request, type=BALANCE)
    → Adapter POSTs ReqBalEnq to NPCI
      → NPCI responds with balance in same HTTP call (no callback)
        → Kafka (npci.inbound.response, balance=25000.00, currency=INR)
```

### Flow C — INBOUND COLLECT (NPCI-initiated)

```
NPCI
  → POST /upi/ReqPay/... on Adapter (type=COLLECT)
    → Adapter: Ack returned immediately
      → @Async: parse XML, publish COLLECT event
        → Kafka (npci.inbound.response, type=COLLECT)
          → Orchestrator decides accept / reject
```

---

## Kafka Topics

| Topic | Direction | Producer | Consumer |
|---|---|---|---|
| `npci.outbound.request` | Inbound to Adapter | Orchestrator | NPCI Adapter |
| `npci.inbound.response` | Outbound from Adapter | NPCI Adapter | Orchestrator, Notification Service |

### Event Schemas

**Consumed — `npci.outbound.request`**
```json
{
  "txnId":    "uuid",
  "msgId":    "uuid",
  "type":     "PAY | BALANCE",
  "payerVpa": "user@demopsp",
  "payeeVpa": "merchant@okaxis",
  "amount":   "500.00",
  "timestamp":"2026-04-18T10:30:00Z"
}
```

**Produced — `npci.inbound.response`**
```json
{
  "txnId":    "uuid",
  "msgId":    "uuid",
  "type":     "PAY | BALANCE | COLLECT",
  "result":   "SUCCESS | FAILURE | TIMEOUT | DEEMED",
  "balance":  "25000.00",
  "currency": "INR",
  "errCode":  "",
  "timestamp":"2026-04-18T10:30:05Z"
}
```

---

## REST Endpoints (Webhook)

NPCI calls these — pre-registered, not passed in outbound requests.

| Method | Path | Flow | Description |
|---|---|---|---|
| POST | `/upi/RespPay/1.0/urn:txnid/{txnId}` | A | Async RespPay callback from NPCI |
| POST | `/upi/ReqPay/1.0/urn:txnid/{txnId}` | C | NPCI-initiated collect/credit |

**Response:** Ack XML (HTTP 200) — always returned immediately before processing.

---

## Configuration

| Property | Default | Description |
|---|---|---|
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka broker address |
| `NPCI_BASE_URL` | `http://localhost:9090` | Mock NPCI server URL |
| `NPCI_MTLS_ENABLED` | `false` | Enable mTLS (flip to `true` for production) |
| `NPCI_KEYSTORE_PATH` | _(empty)_ | PKCS12 keystore path (mTLS) |
| `NPCI_TRUSTSTORE_PATH` | _(empty)_ | PKCS12 truststore path (mTLS) |

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.2.5 |
| Messaging | Apache Kafka (spring-kafka) |
| HTTP Client | Apache HttpClient 5 |
| Build | Maven |
| Container | Docker (multi-stage, non-root) |

---

## Running Locally

**Prerequisites:** Java 17, Maven, Kafka running on `localhost:9092`

```bash
# Build
mvn clean package -DskipTests

# Run
java -jar target/npci-adapter-1.0.0.jar

# Docker
docker build -t npci-adapter:1.0.0 .
docker run -p 8081:8081 \
  -e KAFKA_BOOTSTRAP_SERVERS=localhost:9092 \
  -e NPCI_BASE_URL=http://localhost:9090 \
  npci-adapter:1.0.0
```

**Health check:**
```bash
curl http://localhost:8081/actuator/health
```

---

## Testing Webhooks Manually

```bash
# Simulate NPCI RespPay callback (Flow A)
curl -X POST "http://localhost:8081/upi/RespPay/1.0/urn:txnid/test-001" \
  -H "Content-Type: application/xml" \
  -H "X-UPI-Signature: dummy-sig" \
  -d '<?xml version="1.0" encoding="UTF-8"?>
<RespPay>
  <Head msgId="msg-001" orgId="NPCI"/>
  <Txn id="test-001" result="SUCCESS" errCode=""/>
</RespPay>'

# Simulate NPCI inbound collect (Flow C)
curl -X POST "http://localhost:8081/upi/ReqPay/1.0/urn:txnid/collect-001" \
  -H "Content-Type: application/xml" \
  -H "X-UPI-Signature: dummy-sig" \
  -d '<?xml version="1.0" encoding="UTF-8"?>
<ReqPay>
  <Head msgId="msg-002" orgId="NPCI"/>
  <Txn id="collect-001" type="COLLECT" ts="2026-04-18T10:30:00Z">
    <Payer addr="user@demopsp" type="PERSON">
      <Amount cur="INR" value="250.00"/>
    </Payer>
    <Payees>
      <Payee addr="merchant@okaxis" type="ENTITY">
        <Amount cur="INR" value="250.00"/>
      </Payee>
    </Payees>
  </Txn>
</ReqPay>'
```

---

## Demo vs Production

| Component | Demo | Production |
|---|---|---|
| Signing | SHA-256 hex | **ECDSA P-256** with PSP private key (HSM) |
| MPIN Encryption | Base64 mock | **AES-256-GCM** with NPCI public key |
| Transport | Plain HTTP | **mTLS** — flip `NPCI_MTLS_ENABLED=true` |
| Idempotency | In-memory `ConcurrentHashMap` | Redis SET (multi-instance safe) |
| XML Schema | Abbreviated demo tags | Full NPCI UPI 2.x XSD-compliant XML |

> The `sign()` / `verify()` and `encryptMpin()` interfaces are unchanged between demo and production — only the implementation body swaps.

---

## Project Structure

```
npci-adapter/
├── pom.xml
├── Dockerfile
├── src/main/resources/application.yml
└── src/main/java/com/psp/npci/adapter/
    ├── NpciAdapterApplication.java
    ├── config/
    │   ├── KafkaConfig.java
    │   ├── RestTemplateConfig.java     ← mTLS-aware
    │   └── AsyncConfig.java
    ├── model/
    │   ├── NpciOutboundRequestEvent.java
    │   └── NpciInboundResponseEvent.java
    ├── consumer/
    │   └── NpciOutboundConsumer.java
    ├── controller/
    │   └── NpciWebhookController.java
    ├── producer/
    │   └── NpciResponseProducer.java
    └── service/
        ├── NpciAdapterService.java
        ├── XmlBuilderService.java
        ├── XmlParserService.java
        ├── SigningService.java
        ├── EncryptionService.java
        └── IdempotencyService.java
```

---

*Part of [APEX-UPI](https://github.com/Apexupi-Coders/APEX-UPI) — a UPI PSP Switch reference implementation.*

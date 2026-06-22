# UPI PSP Switch — Complete Demo Stack

## Architecture

```
User (curl/dashboard)
        │
        ▼ POST /api/psp/pay
┌──────────────┐   Kafka: upi.transactions.initiated
│  PSP Switch  │ ─────────────────────────────────────►  ┌──────────────┐
│   :8081      │                                          │ NPCI Router  │
└──────────────┘                                          │   :8082      │
                                                          │              │
                              ┌──────────── DETECTS ─────┤ Auto-Reversal│
                              │             FAILURE       │  Scheduler   │
                              │             (>15s)        └──────┬───────┘
                              │                                  │
                              │         Kafka: upi.cbs.debit     │ Kafka: upi.cbs.credit
                              │                ◄─────────────────┤───────────────────►
                              │                                  │
                              ▼                          ┌───────▼──────┐
                     ┌─────────────┐                    │  CBS Service  │
                     │ CBS Debit DB│                    │    :8083      │
                     │ postgres    │◄───────────────────┤               │
                     │ :5433       │   debit_ledger      │               │───► ┌─────────────┐
                     │ cbs_debit   │   reversal_ledger   │               │     │ CBS Credit  │
                     └─────────────┘                    └───────────────┘     │ DB postgres │
                           ▲                                                   │ :5434       │
                           │  REVERSAL: credit payer back                      │ cbs_credit  │
                           └───────────────────────────────────────────────────┘
```

## Auto-Reversal Flow (Simulate with `simulateFailure: true`)

1. **PSP** receives payment → publishes to `upi.transactions.initiated`
2. **NPCI** picks up → routes to CBS for debit
3. **CBS** writes debit record to `cbs_debit` database → confirms back
4. **NPCI** routes to CBS for credit → *credit bank simulated DOWN*
5. **NPCI Scheduler** (runs every 5s) detects `CREDIT_PENDING > 15s`
6. **NPCI** publishes to `upi.cbs.reversal`
7. **CBS** updates `debit_ledger` status → inserts into `reversal_ledger` → credits payer back
8. **Dashboard** shows full lifecycle via WebSocket in real-time

## Services

| Service | Port | Role |
|---|---|---|
| PSP Switch | 8081 | Payment initiation, Kafka producer |
| NPCI Router | 8082 | Central switch, failure detection, reversal trigger |
| CBS Service | 8083 | Dual-DB core banking |
| Dashboard | 8084 | Real-time WebSocket UI |
| CBS Debit DB | 5433 | PostgreSQL — cbs_debit (debit_ledger, reversal_ledger) |
| CBS Credit DB | 5434 | PostgreSQL — cbs_credit (credit_ledger) |
| Kafka | 9093 | Event bus |

## Run

```bash
./run.sh
# OR
docker-compose up --build
```

## Test

```bash
# Normal payment
curl -X POST http://localhost:8081/api/psp/pay \
  -H 'Content-Type: application/json' \
  -d '{"payerVpa":"alice@sbi","payeeVpa":"bob@hdfc","amount":"500"}'

# Trigger auto-reversal demo
curl -X POST http://localhost:8081/api/psp/pay \
  -H 'Content-Type: application/json' \
  -d '{"payerVpa":"alice@sbi","payeeVpa":"bob@hdfc","amount":"1000","simulateFailure":"true"}'

# Watch NPCI detect failure and initiate reversal
docker-compose logs -f npci-router

# Watch CBS write to both DBs and process reversal
docker-compose logs -f cbs-service
```

## Kafka Topics

| Topic | Flow |
|---|---|
| `upi.transactions.initiated` | PSP → NPCI |
| `upi.cbs.debit` | NPCI → CBS (debit op) |
| `upi.cbs.debit.confirm` | CBS → NPCI (debit done) |
| `upi.cbs.credit` | NPCI → CBS (credit op) |
| `upi.cbs.credit.confirm` | CBS → NPCI (credit done) |
| `upi.cbs.reversal` | NPCI → CBS (reversal request) |
| `upi.cbs.reversal.confirm` | CBS → NPCI (reversal done) |
| `upi.dashboard.events` | All services → Dashboard WebSocket |

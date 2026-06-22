# APEX-UPI Banking Switch Architecture

This directory contains the new Banking Switch implementation, which simulates a complete end-to-end UPI transaction flow across a multi-VM architecture. It acts as an independent stack interacting with the existing PSP Switch.

## Architecture Overview

1. **NPCI Simulator (VM-2)**: `npci-service/`
   - Handles VPA discovery and routes transactions to appropriate banks.
   - Communicates purely over REST + XML.
   
2. **Banking Switch (VM-3)**: `payment-switch/`
   - Event-driven Kafka architecture (4 microservices).
   - Orchestrates balance inquiries and payment debits/credits.
   
3. **Mock CBS (VM-4)**: `cbs-service/`
   - Core Banking System simulator.
   - Enforces ACID guarantees (SERIALIZABLE isolation) on ledgers and accounts.

4. **Demo Tools (VM-2 & VM-4)**: `demo-tools/`
   - Sleek web UIs to send mock requests to NPCI and view CBS ledgers in real-time.

See the implementation plan for full data flow details.

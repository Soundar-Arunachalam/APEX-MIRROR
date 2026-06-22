# Banking Switch Demo Tools

This folder contains two standalone Spring Boot applications meant for monitoring and driving the system.

## 1. NPCI Demo Tool (Port 9090)
**Deploy on VM-2 (alongside NPCI Service)**
- Start with `mvn spring-boot:run`
- Access at `http://localhost:9090`
- Features: Send `ReqValAdd` (VPA Discovery), `ReqBalEnq`, and `ReqPay` manually via a sleek web UI.

## 2. CBS Demo Tool (Port 9090)
**Deploy on VM-4 (alongside CBS Service)**
- Start with `mvn spring-boot:run`
- Access at `http://localhost:9090`
- Features: View real-time changes to account balances and the immutable transaction ledger.

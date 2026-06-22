#!/bin/bash
set -e
echo ""
echo "╔══════════════════════════════════════════════════════╗"
echo "║     UPI PSP Switch — Complete Stack Launcher         ║"
echo "║  NPCI · PSP · CBS Debit DB · CBS Credit DB           ║"
echo "╚══════════════════════════════════════════════════════╝"
echo ""

# Check Docker
if ! command -v docker &>/dev/null; then
  echo "❌ Docker not found. Install Docker Desktop first."
  exit 1
fi
if ! docker info &>/dev/null; then
  echo "❌ Docker daemon not running. Start Docker Desktop."
  exit 1
fi

echo "🔧 Stopping any existing containers..."
docker-compose down --remove-orphans 2>/dev/null || true

echo "🏗  Building and starting all services..."
docker-compose up --build -d

echo ""
echo "⏳ Waiting for services to become healthy..."
sleep 10

MAX=60; COUNT=0
until docker-compose ps | grep -q "healthy" || [ $COUNT -ge $MAX ]; do
  sleep 2; COUNT=$((COUNT+2))
  echo "   ...waiting ($COUNT/$MAX s)"
done

echo ""
echo "✅ Services started!"
echo ""
echo "┌─────────────────────────────────────────────────────────┐"
echo "│  Service         │ URL                     │ Port       │"
echo "├─────────────────────────────────────────────────────────┤"
echo "│  PSP Switch      │ http://localhost:8081   │ 8081       │"
echo "│  NPCI Router     │ http://localhost:8082   │ 8082       │"
echo "│  CBS Service     │ http://localhost:8083   │ 8083       │"
echo "│  Dashboard       │ http://localhost:8084   │ 8084  ←    │"
echo "│  CBS Debit DB    │ localhost:5433/cbs_debit│ 5433       │"
echo "│  CBS Credit DB   │ localhost:5434/cbs_credit│ 5434      │"
echo "│  Kafka           │ localhost:9093          │ 9093       │"
echo "└─────────────────────────────────────────────────────────┘"
echo ""
echo "🌐 Open the dashboard: http://localhost:8084"
echo ""
echo "🧪 Quick test commands:"
echo ""
echo "  # Normal payment:"
echo "  curl -X POST http://localhost:8081/api/psp/pay \\"
echo "    -H 'Content-Type: application/json' \\"
echo "    -d '{\"payerVpa\":\"alice@sbi\",\"payeeVpa\":\"bob@hdfc\",\"amount\":\"500\"}'"
echo ""
echo "  # Simulate failure → triggers NPCI auto-reversal:"
echo "  curl -X POST http://localhost:8081/api/psp/pay \\"
echo "    -H 'Content-Type: application/json' \\"
echo "    -d '{\"payerVpa\":\"alice@sbi\",\"payeeVpa\":\"bob@hdfc\",\"amount\":\"1000\",\"simulateFailure\":\"true\"}'"
echo ""
echo "  # Logs:"
echo "  docker-compose logs -f npci-router    # watch NPCI auto-reversal detection"
echo "  docker-compose logs -f cbs-service    # watch dual DB writes"
echo ""

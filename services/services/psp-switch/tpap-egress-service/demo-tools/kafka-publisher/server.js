const express = require('express');
const { Kafka } = require('kafkajs');
const { v4: uuidv4 } = require('uuid');
const path = require('path');

const app = express();
const PORT = 3001;
const TOPIC = 'psp.switch.completed.events';

// ── Kafka Producer Setup ──
const kafka = new Kafka({
  clientId: 'tpap-demo-publisher',
  brokers: [process.env.KAFKA_BROKER || 'localhost:9092'],
});
const producer = kafka.producer();
let isConnected = false;
let publishCount = 0;
let lastEventId = null;

// ── Middleware ──
app.use(express.json({ limit: '1mb' }));
app.use(express.static(path.join(__dirname, 'public')));

// ── Sample Payload Templates ──
const templates = {
  VPA_VERIFICATION: {
    eventId: `evt-vpa-${uuidv4().slice(0, 8)}`,
    eventType: 'VPA_VERIFICATION',
    tpapId: 'DEMO_TPAP_001',
    txnId: `TXN${Date.now()}001`,
    correlationId: `corr-vpa-${uuidv4().slice(0, 8)}`,
    timestamp: new Date().toISOString(),
    schemaVersion: '1.0',
    payload: {
      vpa: 'rajesh.kumar@oksbi',
      accountHolderName: 'Rajesh Kumar',
      bankName: 'State Bank of India',
      verified: true,
      failureReason: null,
    },
  },
  BALANCE_INQUIRY: {
    eventId: `evt-bal-${uuidv4().slice(0, 8)}`,
    eventType: 'BALANCE_INQUIRY',
    tpapId: 'DEMO_TPAP_001',
    txnId: `TXN${Date.now()}002`,
    correlationId: `corr-bal-${uuidv4().slice(0, 8)}`,
    timestamp: new Date().toISOString(),
    schemaVersion: '1.0',
    payload: {
      vpa: 'priya.merchant@okicici',
      availableBalance: '25000.00',
      currency: 'INR',
      inquiryStatus: 'SUCCESS',
    },
  },
  PAYMENT_PUSH: {
    eventId: `evt-pay-${uuidv4().slice(0, 8)}`,
    eventType: 'PAYMENT_PUSH',
    tpapId: 'DEMO_TPAP_001',
    txnId: `TXN${Date.now()}003`,
    correlationId: `corr-pay-${uuidv4().slice(0, 8)}`,
    timestamp: new Date().toISOString(),
    schemaVersion: '1.0',
    payload: {
      payerVpa: 'amit.singh@okhdfc',
      payeeVpa: 'groceryshop@okaxis',
      amount: '1500.00',
      currency: 'INR',
      npciRrn: '426812345678',
      txnStatus: 'SUCCESS',
      failureReason: null,
    },
  },
};

// ── API: Get Templates ──
app.get('/api/templates', (_req, res) => {
  // Generate fresh IDs each time
  const fresh = {};
  for (const [key, tmpl] of Object.entries(templates)) {
    fresh[key] = {
      ...tmpl,
      eventId: `evt-${key.toLowerCase().slice(0, 3)}-${uuidv4().slice(0, 8)}`,
      txnId: `TXN${Date.now()}${key === 'VPA_VERIFICATION' ? '001' : key === 'BALANCE_INQUIRY' ? '002' : '003'}`,
      correlationId: `corr-${key.toLowerCase().slice(0, 3)}-${uuidv4().slice(0, 8)}`,
      timestamp: new Date().toISOString(),
    };
  }
  res.json(fresh);
});

// ── API: Publish Event ──
app.post('/api/publish', async (req, res) => {
  if (!isConnected) {
    return res.status(503).json({ error: 'Kafka producer not connected' });
  }

  const event = req.body;
  if (!event || !event.eventType || !event.tpapId) {
    return res.status(400).json({ error: 'Invalid event payload — eventType and tpapId are required' });
  }

  try {
    const key = `${event.tpapId}:${event.eventType}`;
    await producer.send({
      topic: TOPIC,
      messages: [
        {
          key,
          value: JSON.stringify(event),
          headers: {
            'X-Event-Type': event.eventType,
            'X-TPAP-ID': event.tpapId,
          },
        },
      ],
    });

    publishCount++;
    lastEventId = event.eventId;

    console.log(`✅ Published [${event.eventType}] eventId=${event.eventId} to ${TOPIC}`);
    res.json({
      success: true,
      eventId: event.eventId,
      topic: TOPIC,
      publishCount,
    });
  } catch (err) {
    console.error('❌ Publish failed:', err.message);
    res.status(500).json({ error: err.message });
  }
});

// ── API: Status ──
app.get('/api/status', (_req, res) => {
  res.json({
    kafkaConnected: isConnected,
    publishCount,
    lastEventId,
    topic: TOPIC,
    broker: process.env.KAFKA_BROKER || 'localhost:9092',
  });
});

// ── Start Server ──
async function start() {
  try {
    await producer.connect();
    isConnected = true;
    console.log('🔗 Kafka producer connected');
  } catch (err) {
    console.warn('⚠️  Kafka connection failed (will retry on publish):', err.message);
  }

  app.listen(PORT, () => {
    console.log(`\n╔══════════════════════════════════════════════╗`);
    console.log(`║  TPAP Kafka Event Publisher                  ║`);
    console.log(`║  UI:    http://localhost:${PORT}               ║`);
    console.log(`║  Topic: ${TOPIC}    ║`);
    console.log(`╚══════════════════════════════════════════════╝\n`);
  });
}

start();

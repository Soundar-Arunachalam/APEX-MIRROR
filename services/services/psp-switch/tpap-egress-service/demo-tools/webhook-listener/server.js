const express = require('express');
const path = require('path');

const app = express();
const PORT = 3002;

// ── In-memory event store ──
let eventHistory = [];
let sequence = 0;

// ── SSE clients ──
const sseClients = new Set();

// ── Middleware ──
app.use(express.json({ limit: '5mb' }));
app.use(express.static(path.join(__dirname, 'public')));

// CORS — allow publisher UI and egress service to call us
app.use((_req, res, next) => {
  res.header('Access-Control-Allow-Origin', '*');
  res.header('Access-Control-Allow-Methods', 'GET, POST, DELETE, OPTIONS');
  res.header('Access-Control-Allow-Headers', 'Content-Type, X-Event-Type, X-TPAP-ID');
  next();
});

// ── Webhook Receiver ──
app.post('/webhook', (req, res) => {
  sequence++;
  const entry = {
    id: sequence,
    receivedAt: new Date().toISOString(),
    headers: {
      'content-type': req.headers['content-type'],
      'x-event-type': req.headers['x-event-type'],
      'x-tpap-id': req.headers['x-tpap-id'],
      'user-agent': req.headers['user-agent'],
    },
    body: req.body,
    method: req.method,
    path: req.path,
    ip: req.ip,
  };

  eventHistory.unshift(entry);

  // Keep max 500 entries in memory
  if (eventHistory.length > 500) {
    eventHistory = eventHistory.slice(0, 500);
  }

  console.log(`📥 Webhook #${sequence} | ${entry.body?.eventType || 'UNKNOWN'} | eventId=${entry.body?.eventId || 'N/A'}`);

  // Broadcast to SSE clients
  broadcastSSE(entry);

  // Always respond 200 OK to the egress service
  res.status(200).json({
    received: true,
    sequence,
    timestamp: entry.receivedAt,
  });
});

// ── API: Event History ──
app.get('/api/history', (_req, res) => {
  res.json({
    total: eventHistory.length,
    events: eventHistory,
  });
});

// ── API: Clear History ──
app.delete('/api/history', (_req, res) => {
  const cleared = eventHistory.length;
  eventHistory = [];
  sequence = 0;
  console.log(`🗑️  Cleared ${cleared} events from history`);
  broadcastSSE({ type: 'clear' });
  res.json({ cleared, message: 'History cleared' });
});

// ── API: Stats ──
app.get('/api/stats', (_req, res) => {
  const typeCounts = {};
  eventHistory.forEach((e) => {
    const t = e.body?.eventType || 'UNKNOWN';
    typeCounts[t] = (typeCounts[t] || 0) + 1;
  });

  res.json({
    totalReceived: eventHistory.length,
    byType: typeCounts,
    sseClients: sseClients.size,
    uptime: process.uptime(),
  });
});

// ── SSE: Real-time Event Stream ──
app.get('/api/events', (req, res) => {
  res.writeHead(200, {
    'Content-Type': 'text/event-stream',
    'Cache-Control': 'no-cache',
    Connection: 'keep-alive',
    'Access-Control-Allow-Origin': '*',
  });

  // Send initial heartbeat
  res.write(`data: ${JSON.stringify({ type: 'connected', total: eventHistory.length })}\n\n`);

  sseClients.add(res);
  console.log(`🔗 SSE client connected (total: ${sseClients.size})`);

  req.on('close', () => {
    sseClients.delete(res);
    console.log(`🔌 SSE client disconnected (total: ${sseClients.size})`);
  });
});

function broadcastSSE(data) {
  const payload = `data: ${JSON.stringify(data)}\n\n`;
  sseClients.forEach((client) => {
    try {
      client.write(payload);
    } catch {
      sseClients.delete(client);
    }
  });
}

// ── Start ──
app.listen(PORT, () => {
  console.log(`\n╔══════════════════════════════════════════════╗`);
  console.log(`║  TPAP Webhook Listener                       ║`);
  console.log(`║  UI:      http://localhost:${PORT}               ║`);
  console.log(`║  Webhook: http://localhost:${PORT}/webhook       ║`);
  console.log(`╚══════════════════════════════════════════════╝\n`);
});

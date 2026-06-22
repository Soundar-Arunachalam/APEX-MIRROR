export const logs = [];
export const stats = {
  ALL: { total: 0, successful: 0, failed: 0, timeout: 0 },
  PAYMENT_PUSH: { total: 0, successful: 0, failed: 0, timeout: 0 },
  BALANCE_INQUIRY: { total: 0, successful: 0, failed: 0, timeout: 0 },
  VPA_VERIFICATION: { total: 0, successful: 0, failed: 0, timeout: 0 }
};

export function addLog(tpapId, payload) {
  const logEntry = {
    id: Date.now().toString() + Math.random().toString(36).substr(2, 5),
    tpapId,
    timestamp: new Date().toISOString(),
    payload
  };
  
  logs.unshift(logEntry);
  if (logs.length > 500) logs.pop(); // keep last 500

  const eventType = payload.eventType || 'UNKNOWN';
  if (!stats[eventType] && eventType !== 'UNKNOWN') {
    stats[eventType] = { total: 0, successful: 0, failed: 0, timeout: 0 };
  }

  // Parse payload status
  let statusStr = payload.status || payload.result || payload.txnStatus || 'UNKNOWN';
  statusStr = statusStr.toUpperCase();

  let category = 'failed';
  if (statusStr === 'SUCCESS' || statusStr === 'COMPLETED' || statusStr === 'ACCEPTED') {
    category = 'successful';
  } else if (statusStr === 'TIMEOUT') {
    category = 'timeout';
  }

  // Update ALL
  stats.ALL.total++;
  stats.ALL[category]++;

  // Update specific type
  if (stats[eventType]) {
    stats[eventType].total++;
    stats[eventType][category]++;
  }
}

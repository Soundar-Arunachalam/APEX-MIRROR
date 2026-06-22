"use client";

import { useState, useEffect } from 'react';
import './globals.css';

export default function Dashboard() {
  const [stats, setStats] = useState({ 
    ALL: { total: 0, successful: 0, failed: 0, timeout: 0 },
    PAYMENT_PUSH: { total: 0, successful: 0, failed: 0, timeout: 0 },
    BALANCE_INQUIRY: { total: 0, successful: 0, failed: 0, timeout: 0 },
    VPA_VERIFICATION: { total: 0, successful: 0, failed: 0, timeout: 0 }
  });
  const [logs, setLogs] = useState([]);
  
  // Dashboard UI State
  const [filterType, setFilterType] = useState('ALL');

  // Load Gen State
  const [targetIp, setTargetIp] = useState('192.168.29.203:8080');
  const [type, setType] = useState('MIXED');
  const [count, setCount] = useState(1);
  const [loading, setLoading] = useState(false);
  const [resultMsg, setResultMsg] = useState('');

  // Polling
  useEffect(() => {
    const fetchData = async () => {
      try {
        const [statsRes, logsRes] = await Promise.all([
          fetch('/api/stats'),
          fetch('/api/logs')
        ]);
        if (statsRes.ok) setStats(await statsRes.json());
        if (logsRes.ok) setLogs(await logsRes.json());
      } catch (err) {
        console.error('Fetch error:', err);
      }
    };
    
    fetchData();
    const interval = setInterval(fetchData, 2000);
    return () => clearInterval(interval);
  }, []);

  const fireLoad = async () => {
    setLoading(true);
    setResultMsg('Firing requests...');
    try {
      const res = await fetch('/api/load-test', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ targetIp, count: parseInt(count), type })
      });
      const data = await res.json();
      if (res.ok) {
        setResultMsg(data.message);
      } else {
        setResultMsg('Error: ' + data.error);
      }
    } catch (err) {
      setResultMsg('Request failed: ' + err.message);
    }
    setLoading(false);
  };

  const getStatusColor = (statusStr) => {
    const s = (statusStr || '').toUpperCase();
    if (s === 'SUCCESS' || s === 'COMPLETED' || s === 'ACCEPTED') return 'var(--success)';
    if (s === 'TIMEOUT') return 'var(--warning)';
    return 'var(--danger)';
  };

  const currentStats = stats[filterType] || { total: 0, successful: 0, failed: 0, timeout: 0 };
  const filteredLogs = filterType === 'ALL' ? logs : logs.filter(l => l.payload?.eventType === filterType);

  return (
    <div style={{ padding: '2rem', maxWidth: '1400px', margin: '0 auto' }}>
      <header style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '2rem' }}>
        <div>
          <h1 style={{ fontSize: '2.5rem', background: 'linear-gradient(to right, #3b82f6, #8b5cf6)', WebkitBackgroundClip: 'text', color: 'transparent' }}>
            APEX-UPI TPAP Dashboard
          </h1>
          <p style={{ color: 'var(--text-muted)', marginTop: '0.5rem' }}>Real-time Discovery & Load Generator</p>
        </div>
        <div style={{ background: 'rgba(59, 130, 246, 0.1)', padding: '0.5rem 1rem', borderRadius: '20px', color: 'var(--accent-neon)', fontWeight: '600' }}>
          Webhook URL: /api/webhook/[tpapId]
        </div>
      </header>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 300px', gap: '2rem' }}>
        
        {/* Left Column: Stats & Logs */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: '2rem' }}>
          
          <div style={{ display: 'flex', gap: '1rem', alignItems: 'center' }}>
            <span style={{ color: 'var(--text-muted)', fontWeight: '600' }}>Filter View:</span>
            <select className="input-field" style={{ width: '250px' }} value={filterType} onChange={e => setFilterType(e.target.value)}>
              <option value="ALL">All Event Types</option>
              <option value="PAYMENT_PUSH">Payment Push</option>
              <option value="BALANCE_INQUIRY">Balance Inquiry</option>
              <option value="VPA_VERIFICATION">VPA Verification</option>
            </select>
          </div>

          {/* Stats Grid */}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: '1rem' }}>
            <div className="glass-panel" style={{ padding: '1.5rem', textAlign: 'center' }}>
              <div style={{ color: 'var(--text-muted)', fontSize: '0.9rem', marginBottom: '0.5rem' }}>Total Events</div>
              <div style={{ fontSize: '2.5rem', fontWeight: '700' }}>{currentStats.total}</div>
            </div>
            <div className="glass-panel" style={{ padding: '1.5rem', textAlign: 'center' }}>
              <div style={{ color: 'var(--text-muted)', fontSize: '0.9rem', marginBottom: '0.5rem' }}>Successful</div>
              <div style={{ fontSize: '2.5rem', fontWeight: '700', color: 'var(--success)' }}>{currentStats.successful}</div>
            </div>
            <div className="glass-panel" style={{ padding: '1.5rem', textAlign: 'center' }}>
              <div style={{ color: 'var(--text-muted)', fontSize: '0.9rem', marginBottom: '0.5rem' }}>Timeouts</div>
              <div style={{ fontSize: '2.5rem', fontWeight: '700', color: 'var(--warning)' }}>{currentStats.timeout}</div>
            </div>
            <div className="glass-panel" style={{ padding: '1.5rem', textAlign: 'center' }}>
              <div style={{ color: 'var(--text-muted)', fontSize: '0.9rem', marginBottom: '0.5rem' }}>Failed</div>
              <div style={{ fontSize: '2.5rem', fontWeight: '700', color: 'var(--danger)' }}>{currentStats.failed}</div>
            </div>
          </div>

          {/* Logs Terminal */}
          <div className="glass-panel" style={{ padding: '1.5rem', flex: '1', display: 'flex', flexDirection: 'column' }}>
            <h2 style={{ fontSize: '1.25rem', marginBottom: '1rem', borderBottom: '1px solid var(--glass-border)', paddingBottom: '0.5rem' }}>Real-time Webhook Logs</h2>
            <div style={{ overflowY: 'auto', maxHeight: '500px', paddingRight: '10px' }}>
              {filteredLogs.length === 0 ? (
                <div style={{ color: 'var(--text-muted)', textAlign: 'center', padding: '2rem' }}>Waiting for incoming webhooks...</div>
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                  {filteredLogs.map((log) => {
                    const statusStr = log.payload?.status || log.payload?.result || log.payload?.txnStatus || 'UNKNOWN';
                    return (
                      <div key={log.id} className="animate-fade-in" style={{ background: 'rgba(15,23,42,0.8)', padding: '1rem', borderRadius: '8px', borderLeft: `4px solid ${getStatusColor(statusStr)}` }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '0.5rem', fontSize: '0.85rem' }}>
                          <span style={{ color: 'var(--text-muted)' }}>{new Date(log.timestamp).toLocaleTimeString()} | TPAP: {log.tpapId} | Type: {log.payload?.eventType || 'UNKNOWN'}</span>
                          <span style={{ fontWeight: '600', color: getStatusColor(statusStr) }}>{statusStr}</span>
                        </div>
                        <pre style={{ margin: 0, fontSize: '0.8rem', color: '#e2e8f0', whiteSpace: 'pre-wrap', wordBreak: 'break-all' }}>
                          {JSON.stringify(log.payload, null, 2)}
                        </pre>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          </div>

        </div>

        {/* Right Column: Load Generator */}
        <div className="glass-panel" style={{ padding: '1.5rem', height: 'fit-content' }}>
          <h2 style={{ fontSize: '1.25rem', marginBottom: '1.5rem', display: 'flex', alignItems: 'center', gap: '10px' }}>
            <span style={{ display: 'inline-block', width: '8px', height: '8px', background: 'var(--accent-neon)', borderRadius: '50%', boxShadow: '0 0 10px var(--accent-neon)' }}></span>
            Load Generator
          </h2>
          
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
            <div>
              <label style={{ display: 'block', fontSize: '0.85rem', color: 'var(--text-muted)', marginBottom: '0.4rem' }}>Target IP</label>
              <input 
                type="text" 
                className="input-field" 
                style={{ width: '100%' }}
                value={targetIp}
                onChange={e => setTargetIp(e.target.value)}
                placeholder="192.168.29.203:8080"
              />
            </div>
            
            <div>
              <label style={{ display: 'block', fontSize: '0.85rem', color: 'var(--text-muted)', marginBottom: '0.4rem' }}>Request Type</label>
              <select className="input-field" style={{ width: '100%' }} value={type} onChange={e => setType(e.target.value)}>
                <option value="MIXED">Mixed / Randomised</option>
                <option value="PAYMENT">Payment</option>
                <option value="BALANCE">Balance Enquiry</option>
                <option value="VPA_LOOKUP">VPA Lookup</option>
              </select>
            </div>

            <div>
              <label style={{ display: 'block', fontSize: '0.85rem', color: 'var(--text-muted)', marginBottom: '0.4rem' }}>Volume</label>
              <select className="input-field" style={{ width: '100%' }} value={count} onChange={e => setCount(e.target.value)}>
                <option value="1">1 Request</option>
                <option value="10">10 Requests</option>
                <option value="50">50 Requests</option>
                <option value="100">100 Requests</option>
              </select>
            </div>

            <button 
              className="button-primary" 
              style={{ marginTop: '1rem', opacity: loading ? 0.7 : 1 }}
              onClick={fireLoad}
              disabled={loading}
            >
              {loading ? 'Firing...' : `Fire ${count} Request(s)`}
            </button>

            {resultMsg && (
              <div style={{ marginTop: '1rem', padding: '0.75rem', background: 'rgba(255,255,255,0.05)', borderRadius: '8px', fontSize: '0.85rem', color: 'var(--text-muted)' }}>
                {resultMsg}
              </div>
            )}
          </div>
        </div>

      </div>
    </div>
  );
}

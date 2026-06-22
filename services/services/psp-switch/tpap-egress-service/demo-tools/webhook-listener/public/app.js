/* ═══════════════════════════════════════════════ */
/*  TPAP Webhook Listener — Client Application    */
/* ═══════════════════════════════════════════════ */

(function () {
  'use strict';

  // ── State ──
  let events = [];
  let activeFilter = 'ALL';
  let sseConnected = false;

  // ── DOM Refs ──
  const eventList = document.getElementById('eventList');
  const emptyState = document.getElementById('emptyState');
  const totalBadge = document.getElementById('totalBadge');
  const countVpa = document.getElementById('countVpa');
  const countBalance = document.getElementById('countBalance');
  const countPayment = document.getElementById('countPayment');
  const sseStatusEl = document.getElementById('sseStatus');
  const toastContainer = document.getElementById('toastContainer');
  const liveIndicator = document.getElementById('liveIndicator');

  // ── Init ──
  async function init() {
    await loadHistory();
    setupSSE();
    setupFilters();
    setupClearButton();
  }

  // ── Load existing history ──
  async function loadHistory() {
    try {
      const res = await fetch('/api/history');
      const data = await res.json();
      events = data.events || [];
      renderAll();
    } catch (err) {
      console.error('Failed to load history:', err);
    }
  }

  // ── SSE Connection ──
  function setupSSE() {
    const evtSource = new EventSource('/api/events');

    evtSource.onopen = () => {
      sseConnected = true;
      updateSSEStatus(true);
    };

    evtSource.onmessage = (msg) => {
      try {
        const data = JSON.parse(msg.data);

        if (data.type === 'connected') {
          sseConnected = true;
          updateSSEStatus(true);
          return;
        }

        if (data.type === 'clear') {
          events = [];
          renderAll();
          showToast('History cleared remotely', 'info');
          return;
        }

        // New webhook event
        if (data.id && data.body) {
          events.unshift(data);
          renderAll();

          // Flash notification
          const eventType = data.body?.eventType || 'UNKNOWN';
          showToast(`Received ${eventType} — #${data.id}`, 'success');
        }
      } catch (e) {
        console.error('SSE parse error:', e);
      }
    };

    evtSource.onerror = () => {
      sseConnected = false;
      updateSSEStatus(false);
    };
  }

  function updateSSEStatus(connected) {
    const dot = sseStatusEl.querySelector('.dot');
    dot.className = connected ? 'dot dot-success' : 'dot dot-disconnected';
    liveIndicator.style.display = connected ? 'flex' : 'none';
  }

  // ── Filters ──
  function setupFilters() {
    const filterBtns = document.querySelectorAll('.filter-btn');
    filterBtns.forEach((btn) => {
      btn.addEventListener('click', () => {
        filterBtns.forEach((b) => b.classList.remove('active'));
        btn.classList.add('active');
        activeFilter = btn.dataset.filter;
        renderEventList();
      });
    });
  }

  // ── Clear Button ──
  function setupClearButton() {
    document.getElementById('btnClear').addEventListener('click', async () => {
      if (!confirm('Clear all webhook history?')) return;
      try {
        await fetch('/api/history', { method: 'DELETE' });
        events = [];
        renderAll();
        showToast('History cleared', 'info');
      } catch (err) {
        console.error('Failed to clear:', err);
      }
    });
  }

  // ── Rendering ──
  function renderAll() {
    updateStats();
    renderEventList();
  }

  function updateStats() {
    const counts = { VPA_VERIFICATION: 0, BALANCE_INQUIRY: 0, PAYMENT_PUSH: 0 };
    events.forEach((e) => {
      const t = e.body?.eventType;
      if (counts[t] !== undefined) counts[t]++;
    });

    totalBadge.textContent = events.length;
    countVpa.textContent = counts.VPA_VERIFICATION;
    countBalance.textContent = counts.BALANCE_INQUIRY;
    countPayment.textContent = counts.PAYMENT_PUSH;
  }

  function renderEventList() {
    const filtered =
      activeFilter === 'ALL'
        ? events
        : events.filter((e) => e.body?.eventType === activeFilter);

    if (filtered.length === 0) {
      eventList.innerHTML = '';
      eventList.appendChild(createEmptyState());
      return;
    }

    eventList.innerHTML = filtered.map((e) => createEventRow(e)).join('');

    // Attach expand handlers
    eventList.querySelectorAll('.event-row-header').forEach((header) => {
      header.addEventListener('click', () => {
        const row = header.closest('.event-row');
        row.classList.toggle('expanded');
      });
    });
  }

  function createEmptyState() {
    const div = document.createElement('div');
    div.className = 'empty-state';
    div.innerHTML = `
      <div class="empty-icon">
        <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
          <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/>
          <path d="M13.73 21a2 2 0 0 1-3.46 0"/>
        </svg>
      </div>
      <p>Waiting for webhook events...</p>
      <p class="empty-sub">Start the egress service and publish events from the Kafka Publisher</p>
    `;
    return div;
  }

  function createEventRow(event) {
    const body = event.body || {};
    const eventType = body.eventType || 'UNKNOWN';
    const eventId = body.eventId || 'N/A';
    const txnId = body.txnId || '—';
    const tpapId = body.tpapId || '—';
    const time = formatTime(event.receivedAt);
    const seq = event.id || '?';

    const payloadHtml = syntaxHighlight(JSON.stringify(body, null, 2));

    return `
      <div class="event-row" data-id="${seq}">
        <div class="event-row-header">
          <span class="event-seq">#${seq}</span>
          <span class="event-type-badge badge-${eventType}">${eventType}</span>
          <span class="event-id" title="${eventId}">${eventId}</span>
          <span class="event-txn" title="${txnId}">${txnId}</span>
          <span class="event-tpap">${tpapId}</span>
          <span class="event-time">${time}</span>
          <svg class="event-expand-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="6 9 12 15 18 9"/></svg>
        </div>
        <div class="event-body">
          <div class="payload-label">Webhook Payload</div>
          <div class="payload-json">${payloadHtml}</div>
          <div class="payload-label" style="margin-top: 12px;">Request Headers</div>
          <div class="payload-json">${syntaxHighlight(JSON.stringify(event.headers || {}, null, 2))}</div>
        </div>
      </div>
    `;
  }

  // ── JSON Syntax Highlighting ──
  function syntaxHighlight(json) {
    return json.replace(
      /("(\\u[a-zA-Z0-9]{4}|\\[^u]|[^\\"])*"(\s*:)?|\b(true|false|null)\b|-?\d+(?:\.\d*)?(?:[eE][+-]?\d+)?)/g,
      (match) => {
        let cls = 'json-number';
        if (/^"/.test(match)) {
          if (/:$/.test(match)) {
            cls = 'json-key';
          } else {
            cls = 'json-string';
          }
        } else if (/true|false/.test(match)) {
          cls = 'json-bool';
        } else if (/null/.test(match)) {
          cls = 'json-null';
        }
        return `<span class="${cls}">${match}</span>`;
      }
    );
  }

  // ── Helpers ──
  function formatTime(isoString) {
    if (!isoString) return '—';
    const d = new Date(isoString);
    return d.toLocaleTimeString('en-IN', {
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      hour12: false,
    });
  }

  function showToast(message, type) {
    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;
    toast.innerHTML = `<span>${message}</span>`;
    toastContainer.appendChild(toast);

    setTimeout(() => {
      toast.style.animation = 'toastIn 0.3s ease reverse forwards';
      setTimeout(() => toast.remove(), 300);
    }, 3000);
  }

  // ── Boot ──
  init();
})();

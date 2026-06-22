/* ═══════════════════════════════════════════════ */
/*  TPAP Kafka Publisher — Client Application     */
/* ═══════════════════════════════════════════════ */

(function () {
  'use strict';

  // ── State ──
  let templates = {};
  let currentType = 'VPA_VERIFICATION';
  let logEntries = [];

  // ── DOM Refs ──
  const editor = document.getElementById('jsonEditor');
  const lineNumbers = document.getElementById('lineNumbers');
  const jsonStatus = document.getElementById('jsonStatus');
  const charCount = document.getElementById('charCount');
  const btnPublish = document.getElementById('btnPublish');
  const publishBtnContent = document.getElementById('publishBtnContent');
  const publishSpinner = document.getElementById('publishSpinner');
  const btnRegenerate = document.getElementById('btnRegenerate');
  const btnFormat = document.getElementById('btnFormat');
  const logContainer = document.getElementById('logEntries');
  const toastContainer = document.getElementById('toastContainer');
  const kafkaStatusEl = document.getElementById('kafkaStatus');
  const counterBadge = document.querySelector('.counter-badge');

  // ── Init ──
  async function init() {
    await loadTemplates();
    setupTabs();
    setupEditor();
    setupButtons();
    pollStatus();
    setInterval(pollStatus, 5000);
  }

  // ── Load Templates ──
  async function loadTemplates() {
    try {
      const res = await fetch('/api/templates');
      templates = await res.json();
      setEditorContent(templates[currentType]);
    } catch (err) {
      console.error('Failed to load templates:', err);
      showToast('Failed to load templates', 'error');
    }
  }

  // ── Tab Management ──
  function setupTabs() {
    const tabs = document.querySelectorAll('.tab');
    tabs.forEach((tab) => {
      tab.addEventListener('click', () => {
        tabs.forEach((t) => t.classList.remove('active'));
        tab.classList.add('active');
        currentType = tab.dataset.type;
        if (templates[currentType]) {
          setEditorContent(templates[currentType]);
        }
      });
    });
  }

  // ── Editor ──
  function setupEditor() {
    editor.addEventListener('input', () => {
      updateLineNumbers();
      validateJson();
      updateCharCount();
    });

    editor.addEventListener('scroll', () => {
      lineNumbers.scrollTop = editor.scrollTop;
    });

    editor.addEventListener('keydown', (e) => {
      if (e.key === 'Tab') {
        e.preventDefault();
        const start = editor.selectionStart;
        const end = editor.selectionEnd;
        editor.value = editor.value.substring(0, start) + '  ' + editor.value.substring(end);
        editor.selectionStart = editor.selectionEnd = start + 2;
        editor.dispatchEvent(new Event('input'));
      }
    });
  }

  function setEditorContent(obj) {
    editor.value = JSON.stringify(obj, null, 2);
    updateLineNumbers();
    validateJson();
    updateCharCount();
  }

  function updateLineNumbers() {
    const lines = editor.value.split('\n').length;
    let html = '';
    for (let i = 1; i <= lines; i++) {
      html += `<span>${i}</span>`;
    }
    lineNumbers.innerHTML = html;
  }

  function validateJson() {
    try {
      JSON.parse(editor.value);
      jsonStatus.innerHTML = '<span class="dot dot-success"></span> Valid JSON';
      jsonStatus.style.color = '';
      return true;
    } catch (e) {
      jsonStatus.innerHTML = `<span class="dot dot-disconnected"></span> ${e.message.substring(0, 50)}`;
      jsonStatus.style.color = 'var(--error)';
      return false;
    }
  }

  function updateCharCount() {
    charCount.textContent = `${editor.value.length} chars`;
  }

  // ── Buttons ──
  function setupButtons() {
    btnPublish.addEventListener('click', publishEvent);

    btnRegenerate.addEventListener('click', async () => {
      await loadTemplates();
      showToast('IDs regenerated', 'success');
    });

    btnFormat.addEventListener('click', () => {
      try {
        const parsed = JSON.parse(editor.value);
        setEditorContent(parsed);
        showToast('JSON formatted', 'success');
      } catch (e) {
        showToast('Cannot format — invalid JSON', 'error');
      }
    });
  }

  // ── Publish ──
  async function publishEvent() {
    if (!validateJson()) {
      showToast('Cannot publish — invalid JSON payload', 'error');
      return;
    }

    const payload = JSON.parse(editor.value);
    setPublishing(true);

    try {
      const res = await fetch('/api/publish', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });

      const data = await res.json();

      if (res.ok && data.success) {
        addLogEntry(payload, 'success');
        counterBadge.textContent = data.publishCount;
        showToast(`Published ${payload.eventType} → ${data.topic}`, 'success');

        // Regenerate IDs for next publish
        if (templates[currentType]) {
          const fresh = await fetch('/api/templates').then(r => r.json());
          templates = fresh;
          setEditorContent(templates[currentType]);
        }
      } else {
        addLogEntry(payload, 'error', data.error);
        showToast(`Publish failed: ${data.error}`, 'error');
      }
    } catch (err) {
      addLogEntry(payload, 'error', err.message);
      showToast(`Network error: ${err.message}`, 'error');
    } finally {
      setPublishing(false);
    }
  }

  function setPublishing(active) {
    btnPublish.disabled = active;
    publishBtnContent.style.display = active ? 'none' : 'flex';
    publishSpinner.style.display = active ? 'flex' : 'none';
  }

  // ── Status Polling ──
  async function pollStatus() {
    try {
      const res = await fetch('/api/status');
      const data = await res.json();
      const dot = kafkaStatusEl.querySelector('.dot');

      if (data.kafkaConnected) {
        dot.className = 'dot dot-success';
      } else {
        dot.className = 'dot dot-disconnected';
      }

      counterBadge.textContent = data.publishCount;
    } catch {
      // ignore
    }
  }

  // ── Log ──
  function addLogEntry(payload, status, errorMsg) {
    const entry = {
      eventType: payload.eventType || 'UNKNOWN',
      eventId: payload.eventId || 'N/A',
      timestamp: new Date().toLocaleTimeString(),
      status,
      error: errorMsg || null,
    };

    logEntries.unshift(entry);
    renderLog();
  }

  function renderLog() {
    if (logEntries.length === 0) {
      logContainer.innerHTML = '<div class="log-empty">No events published yet. Select a payload and hit Publish.</div>';
      return;
    }

    logContainer.innerHTML = logEntries
      .map(
        (e) => `
      <div class="log-entry">
        <span class="log-type-badge badge-${e.eventType}">${e.eventType}</span>
        <span class="log-id">${e.eventId}</span>
        <span class="log-status log-status-${e.status}">${e.status === 'success' ? '✓ SENT' : '✗ FAIL'}</span>
        <span class="log-time">${e.timestamp}</span>
      </div>`
      )
      .join('');
  }

  // ── Toast ──
  function showToast(message, type) {
    const icon = type === 'success' ? '✓' : '✗';
    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;
    toast.innerHTML = `
      <span class="toast-icon">${icon}</span>
      <span>${message}</span>
    `;
    toastContainer.appendChild(toast);

    setTimeout(() => {
      toast.classList.add('toast-exit');
      setTimeout(() => toast.remove(), 300);
    }, 3500);
  }

  // ── Boot ──
  init();
})();

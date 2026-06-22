document.addEventListener('DOMContentLoaded', () => {
    // API Endpoints
    const API_BASE = 'http://localhost:9090';

    // UI Elements
    const eventLog = document.getElementById('eventLog');
    const btnClear = document.getElementById('btnClear');
    
    // Balance Enquiry Elements
    const btnBalEnq = document.getElementById('btnBalEnq');
    const balPayerAddr = document.getElementById('balPayerAddr');
    const balDeviceId = document.getElementById('balDeviceId');

    // Payment Elements
    const btnPay = document.getElementById('btnPay');
    const payPayerAddr = document.getElementById('payPayerAddr');
    const payPayeeAddr = document.getElementById('payPayeeAddr');
    const payAmount = document.getElementById('payAmount');

    // Helper to add event to UI
    function appendEvent(data) {
        const item = document.createElement('div');
        
        // Determine topic styling
        let topicClass = '';
        if (data.topic.includes('inbound')) topicClass = 'topic-inbound';
        else if (data.topic.includes('cbs.request')) topicClass = 'topic-cbs-req';
        else if (data.topic.includes('cbs.response')) topicClass = 'topic-cbs-res';
        else if (data.topic.includes('npci.response')) topicClass = 'topic-npci-res';

        item.className = `event-item ${topicClass}`;
        
        const date = new Date(parseInt(data.timestamp)).toLocaleTimeString();
        
        let formattedPayload = data.payload;
        try {
            // Try to pretty print if it's JSON
            const jsonObj = JSON.parse(data.payload);
            formattedPayload = JSON.stringify(jsonObj, null, 2);
        } catch(e) {
            // If it's XML, simple formatting
            if (typeof formattedPayload === 'string' && formattedPayload.startsWith('<')) {
                formattedPayload = formattedPayload.replace(/></g, '>\n<');
            }
        }

        item.innerHTML = `
            <div class="event-meta">
                <span class="event-topic">${data.topic}</span>
                <span class="event-time">${date}</span>
            </div>
            <div class="event-payload">${escapeHtml(formattedPayload)}</div>
        `;
        
        eventLog.prepend(item);
    }

    function escapeHtml(unsafe) {
        return (unsafe || '').toString()
             .replace(/&/g, "&amp;")
             .replace(/</g, "&lt;")
             .replace(/>/g, "&gt;")
             .replace(/"/g, "&quot;")
             .replace(/'/g, "&#039;");
    }

    // Connect SSE
    const eventSource = new EventSource(`${API_BASE}/api/stream`);
    
    eventSource.addEventListener('message', (e) => {
        try {
            const data = JSON.parse(e.data);
            appendEvent(data);
        } catch (err) {
            console.error('Error parsing SSE data', err);
        }
    });

    eventSource.onerror = (err) => {
        console.error('SSE Error', err);
    };

    // Actions
    btnClear.addEventListener('click', () => {
        eventLog.innerHTML = '';
    });

    btnBalEnq.addEventListener('click', async () => {
        try {
            btnBalEnq.disabled = true;
            btnBalEnq.innerText = 'Sending...';
            
            const req = {
                payerAddress: balPayerAddr.value,
                deviceId: balDeviceId.value
            };
            
            await fetch(`${API_BASE}/api/simulate/reqBalEnq`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(req)
            });
            
        } catch (error) {
            alert('Error sending Balance Enquiry: ' + error.message);
        } finally {
            btnBalEnq.disabled = false;
            btnBalEnq.innerText = 'Send ReqBalEnq';
        }
    });

    btnPay.addEventListener('click', async () => {
        try {
            btnPay.disabled = true;
            btnPay.innerText = 'Sending...';
            
            const req = {
                payerAddress: payPayerAddr.value,
                payeeAddress: payPayeeAddr.value,
                amount: payAmount.value
            };
            
            await fetch(`${API_BASE}/api/simulate/reqPay`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(req)
            });
            
        } catch (error) {
            alert('Error sending Payment: ' + error.message);
        } finally {
            btnPay.disabled = false;
            btnPay.innerText = 'Send ReqPay';
        }
    });
});

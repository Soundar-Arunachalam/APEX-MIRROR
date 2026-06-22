document.getElementById('sendBtn').addEventListener('click', async () => {
    const txnType = document.getElementById('txnType').value;
    const payload = document.getElementById('payload').value;
    const responseArea = document.getElementById('responseArea');
    const responseContent = document.getElementById('responseContent');
    try {
        const res = await fetch('/api/send', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ txnType, payload })
        });
        const data = await res.json();
        responseContent.textContent = JSON.stringify(data, null, 2);
        responseArea.classList.remove('hidden');
    } catch (err) {
        responseContent.textContent = 'Error: ' + err.message;
        responseArea.classList.remove('hidden');
    }
});

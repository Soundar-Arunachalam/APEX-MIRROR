document.getElementById('refreshBtn').addEventListener('click', async () => {
    const responseArea = document.getElementById('responseArea');
    const responseContent = document.getElementById('responseContent');
    try {
        const res = await fetch('/api/status');
        const data = await res.json();
        responseContent.textContent = JSON.stringify(data, null, 2);
        responseArea.classList.remove('hidden');
    } catch (err) {
        responseContent.textContent = 'Error: ' + err.message;
        responseArea.classList.remove('hidden');
    }
});
document.getElementById('refreshBtn').click();

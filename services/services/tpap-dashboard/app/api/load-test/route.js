import { NextResponse } from 'next/server';
import { randomUUID } from 'crypto';

function generateTxnId() {
  return `PHONEPE-${randomUUID()}`;
}

export async function POST(request) {
  try {
    const { targetIp = '192.168.29.203:8080', count = 1, type = 'PAYMENT' } = await request.json();
    
    const types = ['PAYMENT', 'BALANCE', 'VPA_LOOKUP'];
    const promises = [];
    let lastTxnId = null;

    for (let i = 0; i < count; i++) {
      const currentType = type === 'MIXED' ? types[Math.floor(Math.random() * types.length)] : type;
      
      let path = '';
      let payload = {};
      
      const isDuplicate = Math.random() < 0.1 && lastTxnId !== null;
      const isInvalid = Math.random() < 0.1;
      
      const txnId = isDuplicate ? lastTxnId : generateTxnId();
      lastTxnId = txnId;

      if (currentType === 'PAYMENT') {
        path = '/tpap/api/v1/payment/initiate';
        payload = {
          txnId,
          payerVpa: 'alice@banka',
          payeeVpa: 'bob@bankb',
          amount: isInvalid ? 'INVALID_AMT' : '10.00',
          currency: 'INR',
          encryptedPin: 'xyz123',
          deviceFingerprint: 'dev123',
          txnType: 'PEER_TO_PEER'
        };
      } else if (currentType === 'BALANCE') {
        path = '/tpap/api/v1/balance/inquiry';
        payload = {
          txnId: isInvalid ? null : txnId,
          vpa: 'alice@banka',
          encryptedPin: 'xyz123',
          deviceFingerprint: 'dev123'
        };
      } else if (currentType === 'VPA_LOOKUP') {
        path = '/tpap/api/v1/vpa/lookup';
        payload = {
          txnId,
          requesterVpa: isInvalid ? 'invalid_vpa_format' : 'bob@bankb',
          phoneNumber: '9876543210'
        };
      }

      const targetUrl = `http://${targetIp}${path}`;
      
      const p = fetch(targetUrl, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-TPAP-ID': 'phonepe'
        },
        body: JSON.stringify(payload)
      }).then(r => r.json()).catch(e => ({ error: e.message }));
      
      promises.push(p);
    }

    const results = await Promise.all(promises);
    
    return NextResponse.json({
      message: `Fired ${count} ${type} requests to ${targetIp}`,
      sampleResult: results[0]
    });
  } catch (err) {
    return NextResponse.json({ error: err.message }, { status: 500 });
  }
}

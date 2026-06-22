INSERT INTO webhook_configs (tpap_id, event_type, url, active, created_at, updated_at) VALUES 
('phonepe', 'PAYMENT_PUSH', 'http://localhost:3000/api/webhook/phonepe', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('phonepe', 'BALANCE_INQUIRY', 'http://localhost:3000/api/webhook/phonepe', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('phonepe', 'VPA_VERIFICATION', 'http://localhost:3000/api/webhook/phonepe', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (tpap_id, event_type) DO UPDATE SET url = EXCLUDED.url;

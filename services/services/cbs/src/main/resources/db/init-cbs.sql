-- create mock accounts if they do not exist
INSERT INTO accounts (vpa, account_number, balance, status, pin_hash, version)
VALUES ('payer@bank', '1000000001', 5000.00, 'ACTIVE', 'hashed_pin', 0)
ON CONFLICT (vpa) DO NOTHING;

INSERT INTO accounts (vpa, account_number, balance, status, pin_hash, version)
VALUES ('payee@bank', '2000000002', 1000.00, 'ACTIVE', 'hashed_pin', 0)
ON CONFLICT (vpa) DO NOTHING;

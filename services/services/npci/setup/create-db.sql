CREATE DATABASE npci_db;
\c npci_db

CREATE TABLE IF NOT EXISTS vpa_registry (
    vpa VARCHAR(255) PRIMARY KEY,
    bank_code VARCHAR(50) NOT NULL,
    customer_name VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS bank_endpoints (
    bank_code VARCHAR(50) PRIMARY KEY,
    switch_base_url VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS transaction_log (
    txn_id VARCHAR(255) PRIMARY KEY,
    txn_type VARCHAR(50) NOT NULL,
    payer_vpa VARCHAR(255),
    payee_vpa VARCHAR(255),
    amount DECIMAL(15,2),
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    request_xml TEXT,
    response_xml TEXT
);

-- Seed Data
INSERT INTO vpa_registry (vpa, bank_code, customer_name) VALUES ('alice@banka', 'BANKA', 'Alice') ON CONFLICT DO NOTHING;
INSERT INTO vpa_registry (vpa, bank_code, customer_name) VALUES ('bob@bankb', 'BANKB', 'Bob') ON CONFLICT DO NOTHING;

INSERT INTO bank_endpoints (bank_code, switch_base_url) VALUES ('BANKA', 'http://localhost:8081') ON CONFLICT DO NOTHING;
INSERT INTO bank_endpoints (bank_code, switch_base_url) VALUES ('BANKB', 'http://localhost:8081') ON CONFLICT DO NOTHING;

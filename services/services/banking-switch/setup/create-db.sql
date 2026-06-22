CREATE DATABASE bankswitch_db;
\c bankswitch_db

CREATE TABLE transactions (
    txn_id VARCHAR(255) PRIMARY KEY,
    txn_type VARCHAR(50),
    state VARCHAR(50),
    payer_vpa VARCHAR(255),
    payee_vpa VARCHAR(255),
    amount DOUBLE PRECISION,
    xml_payload TEXT,
    created_at BIGINT,
    updated_at BIGINT
);

CREATE TABLE event_log (
    id SERIAL PRIMARY KEY,
    txn_id VARCHAR(255),
    event_type VARCHAR(50),
    event_data TEXT,
    timestamp BIGINT
);

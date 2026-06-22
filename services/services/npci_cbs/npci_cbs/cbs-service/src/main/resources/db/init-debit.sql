-- CBS DEBIT DATABASE SCHEMA
-- Tracks all debit operations (money leaving payer accounts)

CREATE TABLE IF NOT EXISTS accounts (
                                        vpa         VARCHAR(128) PRIMARY KEY,
    name        VARCHAR(128) NOT NULL,
    bank        VARCHAR(32) NOT NULL,
    balance     NUMERIC(15,2) NOT NULL DEFAULT 10000.00,
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW()
    );

INSERT INTO accounts (vpa, name, bank, balance) VALUES
                                                    ('alice@sbi',   'Alice',   'SBI',  10000.00),
                                                    ('rahul@sbi',   'Rahul',   'SBI',  25000.00),
                                                    ('priya@hdfc',  'Priya',   'HDFC', 15000.00),
                                                    ('bob@hdfc',    'Bob',     'HDFC', 10000.00),
                                                    ('kiran@axis',  'Kiran',   'AXIS', 20000.00),
                                                    ('deepa@icici', 'Deepa',   'ICICI',30000.00)
    ON CONFLICT (vpa) DO NOTHING;

CREATE TABLE IF NOT EXISTS debit_ledger (
                                            id            BIGSERIAL PRIMARY KEY,
                                            txn_id        VARCHAR(64) UNIQUE NOT NULL,
    rrn           VARCHAR(32),
    payer_vpa     VARCHAR(128) NOT NULL,
    payer_bank    VARCHAR(32) NOT NULL,
    amount        NUMERIC(15,2) NOT NULL,
    status        VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    reversal_reason TEXT,
    reversed_at   TIMESTAMP,
    created_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP NOT NULL DEFAULT NOW()
    );

CREATE TABLE IF NOT EXISTS reversal_ledger (
                                               id          BIGSERIAL PRIMARY KEY,
                                               txn_id      VARCHAR(64) UNIQUE NOT NULL,
    payer_vpa   VARCHAR(128) NOT NULL,
    amount      NUMERIC(15,2) NOT NULL,
    reason      TEXT,
    reversed_at TIMESTAMP NOT NULL DEFAULT NOW()
    );

CREATE INDEX IF NOT EXISTS idx_debit_txn_id ON debit_ledger(txn_id);
CREATE INDEX IF NOT EXISTS idx_debit_status ON debit_ledger(status);
CREATE INDEX IF NOT EXISTS idx_debit_payer ON debit_ledger(payer_vpa);
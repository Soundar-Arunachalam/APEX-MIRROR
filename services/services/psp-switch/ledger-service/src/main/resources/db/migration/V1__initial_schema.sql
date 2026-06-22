-- =================================================================
-- PSP Switch Ledger Service — Initial Schema
-- Version: V1
-- Compliant with UPI 2.0 specification field naming conventions
-- All monetary values: NUMERIC(15,2)
-- All VPAs: TEXT (encrypted at application layer)
-- All timestamps: TIMESTAMPTZ (UTC)
-- =================================================================

-- -----------------------------------------------------------------
-- TABLE: upi_transactions
-- System-of-record for every UPI transaction through the switch.
-- -----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS upi_transactions (
    -- Internal surrogate key
    id                  BIGSERIAL           PRIMARY KEY,

    -- PSP-generated Transaction ID (max 35 chars per UPI spec)
    tid                 VARCHAR(35)         NOT NULL UNIQUE,

    -- UPI Transaction Reference ID from TPAP
    txn_ref_id          VARCHAR(35)         NOT NULL,

    -- Retrieval Reference Number assigned by NPCI on SUCCESS
    rrn                 VARCHAR(12),

    -- Approval Reference Number (ARN) from NPCI acquirer
    approval_number     VARCHAR(12),

    -- Parties (AES-256 encrypted at application layer)
    payer_vpa           TEXT                NOT NULL,
    payee_vpa           TEXT                NOT NULL,
    payer_name          TEXT,
    payee_name          TEXT,

    -- Originating PSP / TPAP identifier
    psp_id              VARCHAR(20)         NOT NULL DEFAULT 'UNKNOWN',

    -- MCC — 0000 for P2P, populated for P2M
    merchant_code       VARCHAR(10),

    -- Financial
    amount              NUMERIC(15,2)       NOT NULL CHECK (amount >= 0),
    currency            CHAR(3)             NOT NULL DEFAULT 'INR',
    min_amount          NUMERIC(15,2),

    -- Transaction classification
    txn_type            VARCHAR(20)         NOT NULL DEFAULT 'PAY',
    flow_direction      VARCHAR(10)         NOT NULL DEFAULT 'SEND',
    upi_mode            VARCHAR(5),
    requires_passcode   BOOLEAN             NOT NULL DEFAULT FALSE,

    -- Status lifecycle
    -- PENDING → SUBMITTED → SUCCESS | FAILED | UNKNOWN | REVERSED | EXPIRED
    status              VARCHAR(20)         NOT NULL DEFAULT 'PENDING',
    npci_response_code  VARCHAR(4),
    npci_error_code     VARCHAR(64),
    failure_reason      VARCHAR(500),

    -- NPCI message correlation
    npci_msg_id         VARCHAR(35),
    npci_txn_id         VARCHAR(35),

    -- Merchant / sub-merchant refs (UPI 2.0 P2M)
    merchant_id         TEXT,                                   -- AES-256 encrypted
    sub_merchant_id     VARCHAR(50),
    merchant_txn_id     VARCHAR(50),

    -- Audit metadata
    source_service      VARCHAR(50),
    kafka_event_id      VARCHAR(100),                           -- idempotency key

    -- Timestamps (UTC)
    initiated_at        TIMESTAMPTZ         NOT NULL DEFAULT NOW(),
    submitted_at        TIMESTAMPTZ,
    completed_at        TIMESTAMPTZ,
    reversed_at         TIMESTAMPTZ,
    updated_at          TIMESTAMPTZ         NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE upi_transactions IS 'System-of-record for every UPI transaction through the PSP switch';
COMMENT ON COLUMN upi_transactions.payer_vpa IS 'AES-256 encrypted payer UPI VPA';
COMMENT ON COLUMN upi_transactions.payee_vpa IS 'AES-256 encrypted payee UPI VPA';
COMMENT ON COLUMN upi_transactions.merchant_id IS 'AES-256 encrypted merchant ID (UPI 2.0 P2M)';

-- -----------------------------------------------------------------
-- TABLE: txn_status_events
-- Immutable audit trail of every status transition.
-- Append-only — never update or delete rows.
-- -----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS txn_status_events (
    id              BIGSERIAL       PRIMARY KEY,
    tid             VARCHAR(35)     NOT NULL REFERENCES upi_transactions(tid) ON DELETE RESTRICT,
    from_status     VARCHAR(20),
    to_status       VARCHAR(20)     NOT NULL,
    source_service  VARCHAR(50)     NOT NULL,
    reason          VARCHAR(500),
    kafka_topic     VARCHAR(200),
    kafka_offset    BIGINT,
    event_payload   JSONB,
    occurred_at     TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE txn_status_events IS 'Immutable audit log of every transaction status transition';

-- -----------------------------------------------------------------
-- TABLE: ledger_entries
-- Double-entry bookkeeping: one DEBIT + one CREDIT per successful transaction.
-- -----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ledger_entries (
    id              BIGSERIAL       PRIMARY KEY,
    tid             VARCHAR(35)     NOT NULL REFERENCES upi_transactions(tid) ON DELETE RESTRICT,
    entry_type      VARCHAR(10)     NOT NULL CHECK (entry_type IN ('DEBIT', 'CREDIT', 'REVERSAL')),
    account_vpa     TEXT            NOT NULL,   -- AES-256 encrypted
    amount          NUMERIC(15,2)   NOT NULL CHECK (amount > 0),
    currency        CHAR(3)         NOT NULL DEFAULT 'INR',
    rrn             VARCHAR(12),
    approval_number VARCHAR(12),
    settlement_date DATE,
    batch_id        VARCHAR(50),
    settled         BOOLEAN         NOT NULL DEFAULT FALSE,
    recorded_at     TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE ledger_entries IS 'Double-entry financial ledger for all settled UPI transactions';

-- -----------------------------------------------------------------
-- Indexes for query performance
-- -----------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_upi_txn_ref          ON upi_transactions(txn_ref_id);
CREATE INDEX IF NOT EXISTS idx_upi_psp              ON upi_transactions(psp_id);
CREATE INDEX IF NOT EXISTS idx_upi_status           ON upi_transactions(status);
CREATE INDEX IF NOT EXISTS idx_upi_initiated_at     ON upi_transactions(initiated_at DESC);
CREATE INDEX IF NOT EXISTS idx_upi_rrn              ON upi_transactions(rrn) WHERE rrn IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_upi_completed_at     ON upi_transactions(completed_at DESC) WHERE completed_at IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_events_tid           ON txn_status_events(tid);
CREATE INDEX IF NOT EXISTS idx_events_occurred_at   ON txn_status_events(occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_ledger_tid           ON ledger_entries(tid);
CREATE INDEX IF NOT EXISTS idx_ledger_date          ON ledger_entries(settlement_date);
CREATE INDEX IF NOT EXISTS idx_ledger_settled       ON ledger_entries(settled) WHERE settled = FALSE;

-- -----------------------------------------------------------------
-- Trigger: auto-update updated_at on upi_transactions
-- -----------------------------------------------------------------
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_upi_txn_updated_at
    BEFORE UPDATE ON upi_transactions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

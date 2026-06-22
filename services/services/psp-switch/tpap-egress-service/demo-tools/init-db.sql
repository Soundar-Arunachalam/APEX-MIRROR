-- ═══════════════════════════════════════════════════════════════
-- TPAP Egress Service — Demo Seed Data
-- Seeds webhook_configs table so the egress service delivers
-- all events to the local webhook listener on port 3002.
-- ═══════════════════════════════════════════════════════════════

-- Create the webhook_configs table (matches JPA entity exactly)
CREATE TABLE IF NOT EXISTS webhook_configs (
    id          BIGSERIAL PRIMARY KEY,
    tpap_id     VARCHAR(50)  NOT NULL,
    event_type  VARCHAR(50)  NOT NULL,
    url         TEXT         NOT NULL,
    active      BOOLEAN      NOT NULL DEFAULT true,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (tpap_id, event_type)
);

-- Create the delivery_logs table (matches JPA entity exactly)
CREATE TABLE IF NOT EXISTS delivery_logs (
    id             BIGSERIAL PRIMARY KEY,
    event_id       VARCHAR(255) NOT NULL,
    txn_id         VARCHAR(100) NOT NULL,
    tpap_id        VARCHAR(50)  NOT NULL,
    event_type     VARCHAR(50)  NOT NULL,
    webhook_url    TEXT         NOT NULL,
    status         VARCHAR(20)  NOT NULL,
    http_status    INTEGER,
    attempt_number INTEGER      NOT NULL DEFAULT 1,
    error_message  TEXT,
    delivered_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- ───────────────────────────────────────────────────
-- Seed: DEMO_TPAP_001 → localhost:3002/webhook
-- (egress service runs natively on the host machine)
-- ───────────────────────────────────────────────────
INSERT INTO webhook_configs (tpap_id, event_type, url, active)
VALUES
    ('DEMO_TPAP_001', 'VPA_VERIFICATION', 'http://localhost:3002/webhook', true),
    ('DEMO_TPAP_001', 'BALANCE_INQUIRY',  'http://localhost:3002/webhook', true),
    ('DEMO_TPAP_001', 'PAYMENT_PUSH',     'http://localhost:3002/webhook', true)
ON CONFLICT (tpap_id, event_type) DO UPDATE
    SET url        = EXCLUDED.url,
        active     = EXCLUDED.active,
        updated_at = NOW();

CREATE SCHEMA IF NOT EXISTS reporting;

CREATE TABLE IF NOT EXISTS reporting.dim_instrument (
    instrument_key BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(64) NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    market VARCHAR(32) NOT NULL,
    asset_class VARCHAR(64) NOT NULL,
    base_currency CHAR(3),
    quote_currency CHAR(3),
    quantity_unit VARCHAR(32) NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE,
    UNIQUE (symbol, effective_from)
);

CREATE TABLE IF NOT EXISTS reporting.dim_client_segment (
    segment_key VARCHAR(80) PRIMARY KEY,
    label VARCHAR(120) NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE,
    privacy_policy_version VARCHAR(40) NOT NULL
);

CREATE TABLE IF NOT EXISTS reporting.fact_order_event (
    source_event_id VARCHAR(160) PRIMARY KEY,
    source_order_id VARCHAR(160) NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    event_time TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    instrument_key BIGINT NOT NULL REFERENCES reporting.dim_instrument(instrument_key),
    segment_key VARCHAR(80) REFERENCES reporting.dim_client_segment(segment_key),
    status VARCHAR(32),
    correction_of_event_id VARCHAR(160),
    is_reversal BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS reporting.fact_fill (
    fill_id VARCHAR(160) PRIMARY KEY,
    source_event_id VARCHAR(160) NOT NULL UNIQUE,
    source_order_id VARCHAR(160) NOT NULL,
    fill_event_time TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    instrument_key BIGINT NOT NULL REFERENCES reporting.dim_instrument(instrument_key),
    segment_key VARCHAR(80) REFERENCES reporting.dim_client_segment(segment_key),
    quantity NUMERIC(28, 10) NOT NULL,
    quantity_unit VARCHAR(32) NOT NULL,
    price NUMERIC(28, 10),
    notional NUMERIC(28, 10),
    trade_currency CHAR(3),
    correction_of_fill_id VARCHAR(160),
    is_reversal BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS reporting.agg_activity_period (
    activity_id BIGSERIAL PRIMARY KEY,
    period_start DATE NOT NULL,
    granularity VARCHAR(12) NOT NULL,
    instrument_key BIGINT NOT NULL REFERENCES reporting.dim_instrument(instrument_key),
    segment_key VARCHAR(80) REFERENCES reporting.dim_client_segment(segment_key),
    market VARCHAR(32) NOT NULL,
    asset_class VARCHAR(64) NOT NULL,
    order_count BIGINT NOT NULL DEFAULT 0,
    fill_count BIGINT NOT NULL DEFAULT 0,
    executed_quantity NUMERIC(28, 10),
    quantity_unit VARCHAR(32),
    traded_notional NUMERIC(28, 10),
    currency CHAR(3),
    definition_version VARCHAR(40) NOT NULL
);

CREATE TABLE IF NOT EXISTS reporting.reporting_watermark (
    source_stream VARCHAR(80) PRIMARY KEY,
    latest_event_time TIMESTAMPTZ,
    latest_source_event_id VARCHAR(160),
    last_successful_load_at TIMESTAMPTZ,
    reconciliation_status VARCHAR(32) NOT NULL,
    definition_version VARCHAR(40),
    warning TEXT
);

CREATE TABLE IF NOT EXISTS reporting.metric_definition (
    metric_key VARCHAR(80) NOT NULL,
    version VARCHAR(40) NOT NULL,
    event_date_rule TEXT NOT NULL,
    formula TEXT NOT NULL,
    unit_rule TEXT NOT NULL,
    currency_rule TEXT NOT NULL,
    approval_reference VARCHAR(160),
    effective_from DATE NOT NULL,
    effective_to DATE,
    PRIMARY KEY (metric_key, version)
);

CREATE TABLE IF NOT EXISTS reporting.reconciliation_run (
    run_id BIGSERIAL PRIMARY KEY,
    source_stream VARCHAR(80) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    status VARCHAR(32) NOT NULL,
    exception_count INTEGER NOT NULL DEFAULT 0,
    details TEXT
);

CREATE INDEX IF NOT EXISTS idx_fact_fill_event_time ON reporting.fact_fill(fill_event_time);
CREATE INDEX IF NOT EXISTS idx_fact_fill_instrument ON reporting.fact_fill(instrument_key, fill_event_time);
CREATE INDEX IF NOT EXISTS idx_fact_order_event_time ON reporting.fact_order_event(event_time);
CREATE INDEX IF NOT EXISTS idx_activity_period_filters ON reporting.agg_activity_period(period_start, market, asset_class);
CREATE UNIQUE INDEX IF NOT EXISTS uq_activity_period_dimensions
    ON reporting.agg_activity_period(
        period_start,
        granularity,
        instrument_key,
        COALESCE(segment_key, ''),
        COALESCE(currency, '')
    );

COMMENT ON SCHEMA reporting IS 'Analytics serving schema; no client identity or transactional records.';

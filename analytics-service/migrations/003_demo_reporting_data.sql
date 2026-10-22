BEGIN;

ALTER TABLE reporting.fact_order_event
    ADD COLUMN IF NOT EXISTS is_demo BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE reporting.fact_fill
    ADD COLUMN IF NOT EXISTS is_demo BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE reporting.agg_activity_period
    ADD COLUMN IF NOT EXISTS is_demo BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS reporting.fact_portfolio_performance (
    snapshot_id BIGSERIAL PRIMARY KEY,
    snapshot_date DATE NOT NULL,
    portfolio_value NUMERIC(18, 6) NOT NULL,
    sp500_value NUMERIC(18, 6) NOT NULL,
    dow_jones_value NUMERIC(18, 6) NOT NULL,
    nasdaq_100_value NUMERIC(18, 6) NOT NULL,
    russell_2000_value NUMERIC(18, 6) NOT NULL,
    valuation_currency CHAR(3) NOT NULL,
    definition_version VARCHAR(40) NOT NULL,
    is_demo BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (snapshot_date, is_demo)
);

CREATE TABLE IF NOT EXISTS reporting.fact_holding_snapshot (
    snapshot_id BIGSERIAL PRIMARY KEY,
    snapshot_date DATE NOT NULL,
    instrument_key BIGINT NOT NULL REFERENCES reporting.dim_instrument(instrument_key),
    market_value NUMERIC(28, 10) NOT NULL,
    percentage NUMERIC(10, 6) NOT NULL,
    currency CHAR(3) NOT NULL,
    definition_version VARCHAR(40) NOT NULL,
    is_demo BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (snapshot_date, instrument_key, is_demo)
);

CREATE INDEX IF NOT EXISTS idx_portfolio_performance_date
    ON reporting.fact_portfolio_performance(snapshot_date, is_demo);
CREATE INDEX IF NOT EXISTS idx_holding_snapshot_date
    ON reporting.fact_holding_snapshot(snapshot_date, is_demo);

DELETE FROM reporting.agg_activity_period WHERE is_demo = TRUE;
DELETE FROM reporting.fact_fill WHERE is_demo = TRUE;
DELETE FROM reporting.fact_order_event WHERE is_demo = TRUE;
DELETE FROM reporting.fact_holding_snapshot WHERE is_demo = TRUE;
DELETE FROM reporting.fact_portfolio_performance WHERE is_demo = TRUE;
DELETE FROM reporting.reporting_watermark WHERE source_stream = 'demo-fixture';
DELETE FROM reporting.metric_definition WHERE version = 'demo-2026-10-01';
DELETE FROM reporting.dim_client_segment WHERE segment_key = 'demo-internal';
DELETE FROM reporting.dim_instrument
WHERE effective_from = DATE '2025-10-01'
  AND symbol IN ('AAPL', 'MSFT', 'NVDA', 'AMZN', 'TSLA', 'VOD.L', 'SAP.DE');

INSERT INTO reporting.dim_instrument (
    symbol, display_name, market, asset_class, base_currency, quote_currency,
    quantity_unit, effective_from
)
VALUES
    ('AAPL', 'Apple Inc.', 'NASDAQ', 'Equity', 'USD', 'USD', 'shares', DATE '2025-10-01'),
    ('MSFT', 'Microsoft Corp.', 'NASDAQ', 'Equity', 'USD', 'USD', 'shares', DATE '2025-10-01'),
    ('NVDA', 'NVIDIA Corp.', 'NASDAQ', 'Equity', 'USD', 'USD', 'shares', DATE '2025-10-01'),
    ('AMZN', 'Amazon.com Inc.', 'NASDAQ', 'Equity', 'USD', 'USD', 'shares', DATE '2025-10-01'),
    ('TSLA', 'Tesla Inc.', 'NASDAQ', 'Equity', 'USD', 'USD', 'shares', DATE '2025-10-01'),
    ('VOD.L', 'Vodafone Group', 'LSE', 'Equity', 'USD', 'USD', 'shares', DATE '2025-10-01'),
    ('SAP.DE', 'SAP SE', 'XETRA', 'Equity', 'USD', 'USD', 'shares', DATE '2025-10-01')
ON CONFLICT (symbol, effective_from) DO UPDATE SET
    display_name = EXCLUDED.display_name,
    market = EXCLUDED.market,
    asset_class = EXCLUDED.asset_class,
    base_currency = EXCLUDED.base_currency,
    quote_currency = EXCLUDED.quote_currency,
    quantity_unit = EXCLUDED.quantity_unit;

INSERT INTO reporting.dim_client_segment (
    segment_key, label, effective_from, privacy_policy_version
)
VALUES ('demo-internal', 'Demonstration analyst scope', DATE '2025-10-01', 'demo-policy-v1')
ON CONFLICT (segment_key) DO UPDATE SET
    label = EXCLUDED.label,
    effective_from = EXCLUDED.effective_from,
    privacy_policy_version = EXCLUDED.privacy_policy_version;

INSERT INTO reporting.metric_definition (
    metric_key, version, event_date_rule, formula, unit_rule, currency_rule,
    approval_reference, effective_from
)
VALUES
    ('fills_booked', 'demo-2026-10-01', 'fill_event_time', 'Distinct durable fill events', 'count', 'not applicable', 'DEMO-ANALYTICS-001', DATE '2025-10-01'),
    ('orders_filled', 'demo-2026-10-01', 'fill_event_time', 'Distinct orders with a booked fill', 'count', 'not applicable', 'DEMO-ANALYTICS-001', DATE '2025-10-01'),
    ('executed_quantity', 'demo-2026-10-01', 'fill_event_time', 'Sum of booked fill quantity', 'instrument quantity unit', 'not applicable', 'DEMO-ANALYTICS-001', DATE '2025-10-01'),
    ('traded_notional', 'demo-2026-10-01', 'fill_event_time', 'Sum of booked fill notional', 'currency amount', 'kept separate by trade currency', 'DEMO-ANALYTICS-001', DATE '2025-10-01')
ON CONFLICT (metric_key, version) DO UPDATE SET
    event_date_rule = EXCLUDED.event_date_rule,
    formula = EXCLUDED.formula,
    unit_rule = EXCLUDED.unit_rule,
    currency_rule = EXCLUDED.currency_rule,
    approval_reference = EXCLUDED.approval_reference,
    effective_from = EXCLUDED.effective_from;

WITH demo_days AS (
    SELECT generate_series(DATE '2026-09-01', DATE '2026-10-31', INTERVAL '1 day')::date AS day
), demo_instruments AS (
    SELECT instrument_key, symbol, market, asset_class, quantity_unit
    FROM reporting.dim_instrument
    WHERE effective_from = DATE '2025-10-01'
      AND symbol IN ('AAPL', 'MSFT', 'NVDA', 'AMZN', 'TSLA', 'VOD.L', 'SAP.DE')
)
INSERT INTO reporting.fact_order_event (
    source_event_id, source_order_id, event_type, event_time, received_at,
    instrument_key, segment_key, status, is_demo
)
SELECT
    'demo-order-event-' || demo_instruments.symbol || '-' || to_char(demo_days.day, 'YYYYMMDD'),
    'demo-order-' || demo_instruments.symbol || '-' || to_char(demo_days.day, 'YYYYMMDD'),
    'ORDER_FILLED',
    demo_days.day + TIME '16:00',
    demo_days.day + TIME '16:05',
    demo_instruments.instrument_key,
    'demo-internal',
    'FILLED',
    TRUE
FROM demo_days
CROSS JOIN demo_instruments;

WITH demo_days AS (
    SELECT generate_series(DATE '2026-09-01', DATE '2026-10-31', INTERVAL '1 day')::date AS day
), demo_instruments AS (
    SELECT instrument_key, symbol, market, asset_class, quantity_unit
    FROM reporting.dim_instrument
    WHERE effective_from = DATE '2025-10-01'
      AND symbol IN ('AAPL', 'MSFT', 'NVDA', 'AMZN', 'TSLA', 'VOD.L', 'SAP.DE')
)
INSERT INTO reporting.fact_fill (
    fill_id, source_event_id, source_order_id, fill_event_time, received_at,
    instrument_key, segment_key, quantity, quantity_unit, price, notional,
    trade_currency, is_demo
)
SELECT
    'demo-fill-' || demo_instruments.symbol || '-' || to_char(demo_days.day, 'YYYYMMDD'),
    'demo-fill-event-' || demo_instruments.symbol || '-' || to_char(demo_days.day, 'YYYYMMDD'),
    'demo-order-' || demo_instruments.symbol || '-' || to_char(demo_days.day, 'YYYYMMDD'),
    demo_days.day + TIME '16:00',
    demo_days.day + TIME '16:05',
    demo_instruments.instrument_key,
    'demo-internal',
    10 + (EXTRACT(DAY FROM demo_days.day)::integer % 7),
    demo_instruments.quantity_unit,
    CASE demo_instruments.symbol
        WHEN 'AAPL' THEN 180
        WHEN 'MSFT' THEN 420
        WHEN 'NVDA' THEN 135
        WHEN 'AMZN' THEN 190
        WHEN 'TSLA' THEN 250
        WHEN 'VOD.L' THEN 9
        ELSE 260
    END,
    (10 + (EXTRACT(DAY FROM demo_days.day)::integer % 7)) * CASE demo_instruments.symbol
        WHEN 'AAPL' THEN 180
        WHEN 'MSFT' THEN 420
        WHEN 'NVDA' THEN 135
        WHEN 'AMZN' THEN 190
        WHEN 'TSLA' THEN 250
        WHEN 'VOD.L' THEN 9
        ELSE 260
    END,
    'USD',
    TRUE
FROM demo_days
CROSS JOIN demo_instruments;

INSERT INTO reporting.agg_activity_period (
    period_start, granularity, instrument_key, segment_key, market, asset_class,
    order_count, fill_count, executed_quantity, quantity_unit, traded_notional,
    currency, definition_version, is_demo
)
SELECT
    f.fill_event_time::date,
    'day',
    f.instrument_key,
    f.segment_key,
    i.market,
    i.asset_class,
    COUNT(DISTINCT f.source_order_id),
    COUNT(DISTINCT f.fill_id),
    SUM(f.quantity),
    f.quantity_unit,
    SUM(f.notional),
    f.trade_currency,
    'demo-2026-10-01',
    TRUE
FROM reporting.fact_fill f
JOIN reporting.dim_instrument i ON i.instrument_key = f.instrument_key
WHERE f.is_demo = TRUE
GROUP BY f.fill_event_time::date, f.instrument_key, f.segment_key, i.market,
         i.asset_class, f.quantity_unit, f.trade_currency;

INSERT INTO reporting.fact_portfolio_performance (
    snapshot_date, portfolio_value, sp500_value, dow_jones_value,
    nasdaq_100_value, russell_2000_value, valuation_currency,
    definition_version, is_demo
)
SELECT
    demo_days.day,
    ROUND(100 + ((demo_days.day - DATE '2025-10-22') % 43) * 0.23, 4),
    ROUND(100 + ((demo_days.day - DATE '2025-10-22') % 37) * 0.19, 4),
    ROUND(100 + ((demo_days.day - DATE '2025-10-22') % 31) * 0.16, 4),
    ROUND(100 + ((demo_days.day - DATE '2025-10-22') % 29) * 0.28, 4),
    ROUND(100 + ((demo_days.day - DATE '2025-10-22') % 47) * 0.12, 4),
    'USD',
    'demo-2026-10-01',
    TRUE
FROM (
    SELECT generate_series(DATE '2025-10-22', DATE '2026-10-22', INTERVAL '1 day')::date AS day
) demo_days;

INSERT INTO reporting.fact_holding_snapshot (
    snapshot_date, instrument_key, market_value, percentage, currency,
    definition_version, is_demo
)
SELECT
    DATE '2026-10-22',
    i.instrument_key,
    holding_values.market_value,
    holding_values.percentage,
    'USD',
    'demo-2026-10-01',
    TRUE
FROM (
    VALUES
        ('AAPL', 18000.00::numeric, 27.27::numeric),
        ('MSFT', 15000.00::numeric, 22.73::numeric),
        ('NVDA', 12000.00::numeric, 18.18::numeric),
        ('AMZN', 9000.00::numeric, 13.64::numeric),
        ('TSLA', 7000.00::numeric, 10.61::numeric),
        ('VOD.L', 3000.00::numeric, 4.55::numeric),
        ('SAP.DE', 2000.00::numeric, 3.03::numeric)
) AS holding_values(symbol, market_value, percentage)
JOIN reporting.dim_instrument i
    ON i.symbol = holding_values.symbol
 AND i.effective_from = DATE '2025-10-01';

INSERT INTO reporting.reporting_watermark (
    source_stream, latest_event_time, latest_source_event_id,
    last_successful_load_at, reconciliation_status, definition_version, warning
)
VALUES (
    'demo-fixture',
    TIMESTAMPTZ '2026-10-22 16:00:00+00',
    'demo-fill-event-SAP.DE-20261031',
    TIMESTAMPTZ '2026-10-22 16:05:00+00',
    'CURRENT',
    'demo-2026-10-01',
    'Demonstration data; not authoritative reporting.'
);

COMMIT;

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
    AND symbol IN ('AAPL', 'MSFT', 'NVDA', 'AMZN', 'TSLA', 'GOOGL', 'JPM');

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
    ('GOOGL', 'Alphabet Inc. Class A', 'NASDAQ', 'Equity', 'USD', 'USD', 'shares', DATE '2025-10-01'),
    ('JPM', 'JPMorgan Chase & Co.', 'NYSE', 'Equity', 'USD', 'USD', 'shares', DATE '2025-10-01')
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
    SELECT day::date AS day,
           ROW_NUMBER() OVER (ORDER BY day)::integer AS day_number
    FROM generate_series(DATE '2025-10-23', DATE '2026-10-22', INTERVAL '1 day') AS days(day)
    WHERE EXTRACT(ISODOW FROM day) < 6
), demo_instruments AS (
    SELECT instrument_key, symbol, market, asset_class, quantity_unit,
           ROW_NUMBER() OVER (ORDER BY symbol)::integer AS instrument_number
    FROM reporting.dim_instrument
    WHERE effective_from = DATE '2025-10-01'
      AND symbol IN ('AAPL', 'MSFT', 'NVDA', 'AMZN', 'TSLA', 'GOOGL', 'JPM')
), active_orders AS (
    SELECT d.day, d.day_number, i.instrument_key, i.symbol, i.market,
           i.asset_class, i.instrument_number
    FROM demo_days d
    CROSS JOIN demo_instruments i
    WHERE MOD(d.day_number * 7 + i.instrument_number * 11, 10) NOT IN (0, 1, 2)
)
INSERT INTO reporting.fact_order_event (
    source_event_id, source_order_id, event_type, event_time, received_at,
    instrument_key, segment_key, status, is_demo
)
SELECT
    'demo-order-event-' || active_orders.symbol || '-' || to_char(active_orders.day, 'YYYYMMDD'),
    'demo-order-' || active_orders.symbol || '-' || to_char(active_orders.day, 'YYYYMMDD'),
    'ORDER_FILLED',
    active_orders.day + TIME '20:00',
    active_orders.day + TIME '20:07',
    active_orders.instrument_key,
    'demo-internal',
    'FILLED',
    TRUE
FROM active_orders;

WITH demo_days AS (
    SELECT day::date AS day,
           ROW_NUMBER() OVER (ORDER BY day)::integer AS day_number
    FROM generate_series(DATE '2025-10-23', DATE '2026-10-22', INTERVAL '1 day') AS days(day)
    WHERE EXTRACT(ISODOW FROM day) < 6
), demo_instruments AS (
    SELECT instrument_key, symbol, market, asset_class, quantity_unit,
           ROW_NUMBER() OVER (ORDER BY symbol)::integer AS instrument_number
    FROM reporting.dim_instrument
    WHERE effective_from = DATE '2025-10-01'
      AND symbol IN ('AAPL', 'MSFT', 'NVDA', 'AMZN', 'TSLA', 'GOOGL', 'JPM')
), active_orders AS (
    SELECT d.day, d.day_number, i.instrument_key, i.symbol,
           i.quantity_unit, i.instrument_number,
           1 + MOD(d.day_number * 3 + i.instrument_number * 5, 3) AS fill_count
    FROM demo_days d
    CROSS JOIN demo_instruments i
    WHERE MOD(d.day_number * 7 + i.instrument_number * 11, 10) NOT IN (0, 1, 2)
)
INSERT INTO reporting.fact_fill (
    fill_id, source_event_id, source_order_id, fill_event_time, received_at,
    instrument_key, segment_key, quantity, quantity_unit, price, notional,
    trade_currency, is_demo
)
SELECT
    'demo-fill-' || active_orders.symbol || '-' || to_char(active_orders.day, 'YYYYMMDD') || '-' || fills.fill_number,
    'demo-fill-event-' || active_orders.symbol || '-' || to_char(active_orders.day, 'YYYYMMDD') || '-' || fills.fill_number,
    'demo-order-' || active_orders.symbol || '-' || to_char(active_orders.day, 'YYYYMMDD'),
    active_orders.day + TIME '19:30' + (fills.fill_number * INTERVAL '7 minutes'),
    active_orders.day + TIME '20:07' + (fills.fill_number * INTERVAL '3 minutes'),
    active_orders.instrument_key,
    'demo-internal',
    5 + MOD(active_orders.day_number * 13 + active_orders.instrument_number * 17 + fills.fill_number * 7, 36),
    active_orders.quantity_unit,
    ROUND(((CASE active_orders.symbol
        WHEN 'AAPL' THEN 225
        WHEN 'MSFT' THEN 510
        WHEN 'NVDA' THEN 180
        WHEN 'AMZN' THEN 220
        WHEN 'TSLA' THEN 330
        WHEN 'GOOGL' THEN 250
        ELSE 295
    END) * (1 + 0.055 * SIN(active_orders.day_number * 0.071 + active_orders.instrument_number)
              + 0.018 * SIN(active_orders.day_number * 0.31 + fills.fill_number * 0.8)))::numeric, 2),
    (5 + MOD(active_orders.day_number * 13 + active_orders.instrument_number * 17 + fills.fill_number * 7, 36))
        * ROUND(((CASE active_orders.symbol
            WHEN 'AAPL' THEN 225
            WHEN 'MSFT' THEN 510
            WHEN 'NVDA' THEN 180
            WHEN 'AMZN' THEN 220
            WHEN 'TSLA' THEN 330
            WHEN 'GOOGL' THEN 250
            ELSE 295
        END) * (1 + 0.055 * SIN(active_orders.day_number * 0.071 + active_orders.instrument_number)
                  + 0.018 * SIN(active_orders.day_number * 0.31 + fills.fill_number * 0.8)))::numeric, 2),
    'USD',
    TRUE
FROM active_orders
CROSS JOIN LATERAL generate_series(1, active_orders.fill_count) AS fills(fill_number);

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
WITH business_days AS (
    SELECT day::date AS day,
           ROW_NUMBER() OVER (ORDER BY day)::integer AS day_number
    FROM generate_series(DATE '2025-10-23', DATE '2026-10-22', INTERVAL '1 day') AS days(day)
    WHERE EXTRACT(ISODOW FROM day) < 6
), daily_returns AS (
    SELECT business_days.day,
           series.series_key,
           series.drift + series.volatility * (uniforms.a + uniforms.b + uniforms.c - 1.5) AS daily_return
    FROM business_days
    CROSS JOIN (VALUES
        ('portfolio'::text, 0.00018::numeric, 0.011::numeric),
        ('sp500'::text, 0.00015::numeric, 0.008::numeric),
        ('dow_jones'::text, 0.00014::numeric, 0.0075::numeric),
        ('nasdaq_100'::text, 0.00022::numeric, 0.013::numeric),
        ('russell_2000'::text, 0.00012::numeric, 0.011::numeric)
    ) AS series(series_key, drift, volatility)
    CROSS JOIN LATERAL (
        SELECT decode(md5(business_days.day::text || ':' || series.series_key || ':a'), 'hex') AS hash_a,
               decode(md5(business_days.day::text || ':' || series.series_key || ':b'), 'hex') AS hash_b,
               decode(md5(business_days.day::text || ':' || series.series_key || ':c'), 'hex') AS hash_c
    ) AS hashes
    CROSS JOIN LATERAL (
        SELECT (get_byte(hash_a, 0)::numeric * 16777216 + get_byte(hash_a, 1) * 65536
                + get_byte(hash_a, 2) * 256 + get_byte(hash_a, 3)) / 4294967295 AS a,
               (get_byte(hash_b, 0)::numeric * 16777216 + get_byte(hash_b, 1) * 65536
                + get_byte(hash_b, 2) * 256 + get_byte(hash_b, 3)) / 4294967295 AS b,
               (get_byte(hash_c, 0)::numeric * 16777216 + get_byte(hash_c, 1) * 65536
                + get_byte(hash_c, 2) * 256 + get_byte(hash_c, 3)) / 4294967295 AS c
    ) AS uniforms
), indexed_values AS (
    SELECT day,
           series_key,
           100 * EXP(COALESCE(SUM(LN(1 + daily_return)) OVER (
               PARTITION BY series_key ORDER BY day ROWS BETWEEN UNBOUNDED PRECEDING AND 1 PRECEDING), 0)) AS indexed_value
    FROM daily_returns
), performance_values AS (
    SELECT day,
           MAX(indexed_value) FILTER (WHERE series_key = 'portfolio') AS portfolio_value,
           MAX(indexed_value) FILTER (WHERE series_key = 'sp500') AS sp500_value,
           MAX(indexed_value) FILTER (WHERE series_key = 'dow_jones') AS dow_jones_value,
           MAX(indexed_value) FILTER (WHERE series_key = 'nasdaq_100') AS nasdaq_100_value,
           MAX(indexed_value) FILTER (WHERE series_key = 'russell_2000') AS russell_2000_value
    FROM indexed_values
    GROUP BY day
)
SELECT
    performance_values.day,
    ROUND(performance_values.portfolio_value::numeric, 6),
    ROUND(performance_values.sp500_value::numeric, 6),
    ROUND(performance_values.dow_jones_value::numeric, 6),
    ROUND(performance_values.nasdaq_100_value::numeric, 6),
    ROUND(performance_values.russell_2000_value::numeric, 6),
    'USD',
    'demo-2026-10-01',
    TRUE
FROM performance_values;

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
    SELECT symbol, market_value,
           ROUND(market_value / SUM(market_value) OVER () * 100, 2) AS percentage
    FROM (VALUES
        ('AAPL', 21500.00::numeric),
        ('MSFT', 17000.00::numeric),
        ('NVDA', 13200.00::numeric),
        ('AMZN', 9800.00::numeric),
        ('TSLA', 7500.00::numeric),
        ('GOOGL', 6700.00::numeric),
        ('JPM', 5300.00::numeric)
    ) AS raw_holdings(symbol, market_value)
) AS holding_values
JOIN reporting.dim_instrument i
    ON i.symbol = holding_values.symbol
 AND i.effective_from = DATE '2025-10-01';

INSERT INTO reporting.reporting_watermark (
    source_stream, latest_event_time, latest_source_event_id,
    last_successful_load_at, reconciliation_status, definition_version, warning
)
VALUES (
    'demo-fixture',
    TIMESTAMPTZ '2026-10-22 20:00:00+00',
    'demo-fixture-through-20261022',
    TIMESTAMPTZ '2026-10-22 20:10:00+00',
    'CURRENT',
    'demo-2026-10-01',
    'Demonstration data; not authoritative reporting.'
);

COMMIT;

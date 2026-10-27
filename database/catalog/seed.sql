\set ON_ERROR_STOP on

BEGIN;

CREATE TEMP TABLE catalog_documents ON COMMIT DROP AS
SELECT 'US'::text AS market, pg_read_file('/catalog/us-v1.json')::jsonb AS document
UNION ALL
SELECT 'CRYPTO', pg_read_file('/catalog/crypto-v1.json')::jsonb
UNION ALL
SELECT 'FX', pg_read_file('/catalog/fx-v1.json')::jsonb;

DO $$
BEGIN
    IF (SELECT count(*) FROM catalog_documents) <> 3
        OR EXISTS (
            SELECT 1
            FROM catalog_documents
            WHERE document ->> 'market' IS DISTINCT FROM market
                OR jsonb_typeof(document -> 'symbols') IS DISTINCT FROM 'array'
                OR CASE
                    WHEN jsonb_typeof(document -> 'symbols') = 'array'
                    THEN jsonb_array_length(document -> 'symbols') = 0
                    ELSE true
                END
        ) THEN
        RAISE EXCEPTION 'Expected non-empty US, CRYPTO, and FX catalog manifests';
    END IF;
END;
$$;

CREATE TEMP TABLE catalog_symbols ON COMMIT DROP AS
SELECT
    market,
    entries.symbol ->> 'symbol' AS ticker,
    entries.symbol ->> 'name' AS instrument_name,
    entries.symbol ->> 'currency' AS currency,
    CASE
        WHEN market IN ('CRYPTO', 'FX') THEN 'OTC'
        ELSE entries.symbol ->> 'exchange'
    END AS exchange,
    entries.symbol ->> 'type' AS instrument_type
FROM catalog_documents
CROSS JOIN LATERAL jsonb_array_elements(document -> 'symbols') AS entries(symbol);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM catalog_symbols
        WHERE market IS NULL OR market NOT IN ('US', 'CRYPTO', 'FX')
            OR ticker IS NULL OR instrument_name IS NULL OR currency IS NULL OR exchange IS NULL
            OR instrument_type IS NULL
            OR instrument_type NOT IN ('equity', 'etf', 'crypto', 'fx')
            OR (market = 'US' AND instrument_type NOT IN ('equity', 'etf'))
            OR (market = 'CRYPTO' AND instrument_type <> 'crypto')
            OR (market = 'FX' AND instrument_type <> 'fx')
    ) THEN
        RAISE EXCEPTION 'Catalog manifests contain an incomplete or unsupported instrument';
    END IF;

    IF EXISTS (
        SELECT ticker
        FROM catalog_symbols
        GROUP BY ticker
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'Catalog manifests contain duplicate ticker symbols';
    END IF;

    IF EXISTS (
        SELECT required.ticker
        FROM (VALUES ('IWM'), ('QQQ')) AS required(ticker)
        WHERE NOT EXISTS (
            SELECT 1
            FROM catalog_symbols
            WHERE market = 'US'
                AND catalog_symbols.ticker = required.ticker
                AND instrument_type = 'etf'
        )
    ) THEN
        RAISE EXCEPTION 'The local US catalog must retain IWM and QQQ as ETFs';
    END IF;
END;
$$;

INSERT INTO instruments (
    instrument_name,
    ticker,
    currency,
    asset_class,
    security_type,
    exchange,
    is_active
)
SELECT
    instrument_name,
    ticker,
    currency,
    CASE instrument_type
        WHEN 'equity' THEN 'Equity'
        WHEN 'etf' THEN 'ETF'
        WHEN 'crypto' THEN 'Cryptocurrency'
        WHEN 'fx' THEN 'Foreign Exchange'
    END,
    CASE instrument_type
        WHEN 'equity' THEN 'COMMON_STOCK'
        WHEN 'etf' THEN 'ETF'
        WHEN 'crypto' THEN 'CRYPTO'
        WHEN 'fx' THEN 'FX_PAIR'
    END,
    exchange,
    true
FROM catalog_symbols
ON CONFLICT (ticker) DO UPDATE
    SET exchange = EXCLUDED.exchange
    WHERE instruments.exchange IS DISTINCT FROM EXCLUDED.exchange;

-- Provision policy for explicitly declared USD catalog instruments without spending
-- provider requests during startup. Every order still requires a fresh validated quote.
WITH seeded_policies AS (
    INSERT INTO instrument_trading_policies (
        instrument_id,
        policy_version,
        venue,
        quantity_unit,
        price_unit,
        currency,
        quantity_step,
        currency_scale,
        max_quantity,
        max_notional,
        quote_max_age_seconds,
        future_skew_seconds,
        retry_seconds,
        max_attempts,
        session,
        enabled,
        raw_price_unit,
        price_multiplier,
        provider_symbol,
        buy_allowed,
        sell_allowed,
        validated_at,
        revision
    )
    SELECT
        i.instrument_id,
        'manifest-provider-v1',
        s.exchange,
        'shares',
        'USD',
        'USD',
        1,
        2,
        10000,
        1000000,
        1800,
        2,
        120,
        3,
        'PROVIDER_OPEN',
        true,
        'USD',
        1,
        i.ticker,
        true,
        true,
        NULL,
        1
    FROM catalog_symbols s
    JOIN instruments i ON i.ticker = s.ticker
    WHERE s.currency = 'USD'
    ON CONFLICT (instrument_id) DO NOTHING
    RETURNING instrument_id, policy_version, venue, provider_symbol
)
INSERT INTO instrument_policy_audit (
    audit_id,
    instrument_id,
    actor,
    actor_type,
    reason,
    before_values,
    after_values,
    recorded_at
)
SELECT
    gen_random_uuid(),
    instrument_id,
    NULL,
    'SYSTEM',
    'Provisioned from the local catalog manifest; each order requires a fresh provider quote.',
    NULL,
    jsonb_build_object(
        'policyVersion', policy_version,
        'venue', venue,
        'providerSymbol', provider_symbol,
        'enabled', true,
        'buyAllowed', true,
        'sellAllowed', true,
        'validatedAt', NULL
    ),
    clock_timestamp()
FROM seeded_policies;

COMMIT;

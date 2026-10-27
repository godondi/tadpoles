-- Requires migration 009. Current quote-age policy ceiling: 1200 seconds.
-- Does not cache quotes, change existing policy values, or override stale-quote rejection.
-- Change individual limits through audited Operations after provider validation.

BEGIN;

ALTER TABLE instrument_trading_policies
    DROP CONSTRAINT instrument_trading_policies_quote_max_age_seconds_check;

ALTER TABLE instrument_trading_policies
    ADD CONSTRAINT instrument_trading_policies_quote_max_age_seconds_check CHECK (quote_max_age_seconds BETWEEN 1 AND 1200);

INSERT INTO schema_migrations (version)
    VALUES (10);

COMMIT;

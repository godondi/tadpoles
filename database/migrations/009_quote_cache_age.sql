-- Requires migration 008. Historical step: allow quote-age policies up to 600 seconds.
-- This is a CHECK constraint change, not a cache table or scheduled refresh.
-- Existing per-instrument ages are preserved. Keep for older-volume upgrade history.

BEGIN;

ALTER TABLE instrument_trading_policies
    DROP CONSTRAINT instrument_trading_policies_quote_max_age_seconds_check;

ALTER TABLE instrument_trading_policies
    ADD CONSTRAINT instrument_trading_policies_quote_max_age_seconds_check CHECK (quote_max_age_seconds BETWEEN 1 AND 600);

INSERT INTO schema_migrations (version)
    VALUES (9);

COMMIT;

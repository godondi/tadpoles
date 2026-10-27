-- Extend the configurable quote age policy from 20 to 30 minutes.
-- Only instruments at the previous default are widened; shorter custom policies remain intact.
BEGIN;

ALTER TABLE instrument_trading_policies
    DROP CONSTRAINT instrument_trading_policies_quote_max_age_seconds_check;

ALTER TABLE instrument_trading_policies
    ADD CONSTRAINT instrument_trading_policies_quote_max_age_seconds_check
    CHECK (quote_max_age_seconds BETWEEN 1 AND 1800);

UPDATE instrument_trading_policies
SET quote_max_age_seconds = 1800
WHERE quote_max_age_seconds = 1200;

INSERT INTO schema_migrations(version) VALUES (21)
ON CONFLICT DO NOTHING;

COMMIT;

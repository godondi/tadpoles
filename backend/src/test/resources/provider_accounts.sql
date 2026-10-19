-- Test-only accounts. Production quotes never read prices from this file.
BEGIN;
INSERT INTO instruments(instrument_name,ticker,currency,asset_class,security_type) VALUES('Apple test identity','AAPL','USD','EQUITY','STOCK') ON CONFLICT(ticker) DO NOTHING;
INSERT INTO instrument_trading_policies(instrument_id,policy_version,venue,quantity_unit,price_unit,currency,quantity_step,currency_scale,max_quantity,max_notional,quote_max_age_seconds,future_skew_seconds,retry_seconds,max_attempts,session,enabled,raw_price_unit,price_multiplier,provider_symbol,buy_allowed,sell_allowed,validated_at)
SELECT instrument_id,'test-provider-v1','NASDAQ','shares','USD','USD',1,2,10000,1000000,30,2,120,3,'PROVIDER_OPEN',true,'USD',1,'AAPL',true,true,now() FROM instruments WHERE ticker='AAPL' ON CONFLICT DO NOTHING;
WITH openings AS (
 INSERT INTO cash_journal(posting_id,client_id,currency,delta,setup_reference,posting_group,actor,recorded_at)
 SELECT gen_random_uuid(),client_id,'USD',10000,'test-opening:'||client_id,gen_random_uuid(),'test setup',now()
 FROM clients JOIN users USING(user_id) WHERE username IN ('demo.client','demo.client2')
 ON CONFLICT(setup_reference) DO NOTHING RETURNING *
)
UPDATE client_cash_accounts c SET posted=c.posted+o.delta,version=c.version+1 FROM openings o WHERE c.client_id=o.client_id AND c.currency=o.currency;
COMMIT;

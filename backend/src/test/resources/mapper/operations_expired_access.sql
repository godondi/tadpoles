UPDATE users
SET trade_access_until = CURRENT_TIMESTAMP - INTERVAL '1' DAY
WHERE user_id = 14;

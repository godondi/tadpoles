# Trade Acceptance Rules

## What changed

Trade submission now enforces the firm trading rules before an order is accepted.

`POST /api/clients/{clientId}/trades` already rejected invalid trade types and inactive instruments. The remaining gap was that buy orders could still be accepted without enough cash, and sell orders could still be accepted without enough holdings.

## Implementation

I reused the existing fill-time business logic instead of duplicating it.

### Shared rule helper

Added `TradingRuleSupport` and `TradingRuleEvaluation` in `service.impl`.

That helper now contains the common rule calculation for:

- inactive instrument rejection
- required cash for buy orders
- required holdings for sell orders
- resulting cash / holding balances

### Acceptance-time validation

`ClientTradeServiceImpl.createTrade(...)` now:

1. loads the client
2. loads the instrument
3. loads the client’s latest holding for that instrument
4. evaluates the trade rules before inserting the trade row

If a rule fails, the trade is rejected with the same `IllegalArgumentException` pattern already used by the service layer.

### Fill-time validation kept in place

`OrderFillServiceImpl` now uses the same shared helper for execution-time checks.

This keeps the fill path protected as a second safety net if the client’s balance, holdings, or instrument tradability changes after submission but before execution.

## Why it was done this way

The requirement says the order must be checked **before it is accepted**, so the validation had to move into the create-trade flow.

The shared helper avoids maintaining two separate versions of the same cash / holdings / tradability rules.

## Additional mapper support

Added a read-only `ClientHoldingMapper.getLatestHolding(...)` query so submission-time validation can inspect the latest holding without using the locking fill-time query.

## Tests added first

Updated tests to cover:

- trade creation succeeds when the client passes the rules
- trade creation fails when the instrument is inactive
- trade creation fails when cash is insufficient
- trade creation fails when holdings are insufficient
- controller returns `400 Bad Request` when acceptance-time trading rules fail
- mapper returns the latest holding for submission-time validation

## Documentation

Updated `docs/api.yaml` so the trade-submission `400` response now explicitly covers inactive instruments and failed trading-rule checks.

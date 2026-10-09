# Launch Asset Class Pricing

## What changed

Order execution now routes market pricing through a dedicated normalized quote service that supports the launch asset classes:

- equities
- foreign exchange
- crypto assets

## Implementation

### Execution quote routing

Added `ExecutionQuoteService` and `ExecutionQuoteServiceImpl`.

That service now:

1. inspects the instrument `assetClass` and `securityType`
2. classifies the instrument into one of the supported launch classes
3. fetches the live quote through the existing Fauxnance quote integration
4. returns a normalized internal quote model for execution

The current launch-class mapping supports common aliases such as:

- `Equity`, `Stock`, `Share`
- `FX`, `Forex`, `Currency Pair`
- `Crypto`, `Cryptocurrency`, `Token`

Unsupported classes are rejected before fill execution.

### Fill path

`OrderFillServiceImpl` no longer talks directly to `FauxnanceQuoteService`.

Instead, it now uses the normalized execution-quote service and continues to:

- fill buys at the live ask
- fill sells at the live bid
- reject when the live quote does not satisfy the order limit

## Why this was done this way

The requirement is not just “use a quote”; it is “use current market quotes across the launch classes the firm supports.”

The routing service keeps that asset-class decision in one place instead of scattering `assetClass` logic inside order execution.

It also gives the platform a clean seam for future divergence if equities, FX, and crypto later need different upstream data providers.

## Tests added first

Added `ExecutionQuoteServiceImplTest` to prove:

- equities route through the supported path
- FX routes through the supported path
- crypto routes through the supported path
- unsupported classes are rejected
- stale quotes are rejected

Updated fill tests so `OrderFillServiceImpl` consumes the normalized execution-quote service instead of a raw quote provider.

## Documentation

Updated `docs/api.yaml` so the fill endpoint now explicitly states that quote-based execution applies to the supported launch classes and can reject unsupported asset classes.

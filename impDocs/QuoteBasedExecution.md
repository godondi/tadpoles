# Quote-Based Execution

## What changed

Order fills are now priced against the current market quote at execution time.

Before this change, `OrderFillServiceImpl` used the submitted trade price or an optional fill-request price directly as the execution price. That meant the platform could execute an order without checking the live market quote.

## Implementation

### Live quote lookup during fill

`OrderFillServiceImpl` now calls `FauxnanceQuoteService` during `fillTrade(...)` using the instrument ticker.

### Execution rule used

I implemented the quote rule as:

- **BUY** orders fill at the current **ask**
- **SELL** orders fill at the current **bid**
- the stored trade price remains the order’s limit price
- `FillOrderRequest.price`, when provided, acts as an optional limit-price override for that execution attempt

If the current quote does not satisfy the limit:

- BUY rejects when `ask > limit`
- SELL rejects when `bid < limit`

### Quote quality checks

The fill also rejects when:

- the instrument has no ticker
- the quote is marked stale
- the required bid/ask side is missing or non-positive

### Existing trading-rule checks kept

After the live quote determines the execution price, the existing trading-rule evaluation still runs for:

- instrument active status
- sufficient cash for buys
- sufficient holdings for sells

That means the live quote now sets the execution price, and the existing cash/holding logic still determines whether the client can actually complete the fill at that price.

## Tests added first

Updated `OrderFillServiceImplTest` to cover:

- BUY fill succeeds using the live ask
- SELL fill succeeds using the live bid
- BUY fill rejects when current ask exceeds the order limit
- SELL fill rejects when current bid is below the order limit
- fill rejects when the required quote side is unavailable

## Documentation

Updated `docs/api.yaml` so the fill endpoint now documents:

- quote-based rejection as a `400`
- quote-unavailable behavior as a `503`
- `FillOrderRequest.price` as an optional limit override rather than the persisted execution price

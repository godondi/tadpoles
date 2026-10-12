# Supported Instrument Submission

## What changed

Trade submission now rejects inactive instruments before the order is accepted.

The `POST /api/clients/{clientId}/trades` flow already validated client ownership, trade type, quantity, price, and instrument existence. The missing gap was that an inactive instrument could still be submitted and would only fail later during fill.

## Implementation

I kept the current service-layer pattern in place and added the support check in `ClientTradeServiceImpl.createTrade(...)` immediately after loading the instrument through `InstrumentService`.

If `instrument.isActive` is `false`, the service now throws:

- `IllegalArgumentException("Inactive instruments cannot be traded.")`

That keeps the error handling aligned with the rest of the trade validation rules and preserves the existing `400 Bad Request` API behavior through the global exception handler.

## Why this was done this way

This moves the business-rule enforcement to the point where the client submits the order, which is where the requirement applies.

I intentionally kept the existing inactive-instrument check in `OrderFillServiceImpl` as well. That second check still matters if an order was submitted while the instrument was active but becomes inactive before execution.

## Tests added first

Following the existing TDD pattern:

- `ClientTradeServiceImplTest` now proves trade creation fails for an inactive instrument and does not persist the trade
- `ClientTradeControllerTest` now proves the API returns `400 Bad Request` with the expected validation message

## Documentation

Updated `docs/api.yaml` so trade submission explicitly documents the `400` response for inactive or unsupported instruments.

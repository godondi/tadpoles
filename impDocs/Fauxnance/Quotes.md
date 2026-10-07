# Fauxnance Quote Implementation

## Summary

This change implements the latest-quote endpoint backed by the external Fauxnance API.

Implemented endpoint:

- `GET /quotes/{symbol}`

The controller also accepts:

- `GET /api/quotes/{symbol}`

That mirrors the same dual-routing approach used by the candle endpoint so the codebase stays consistent with the rest of the backend while still honoring the external Fauxnance contract.

---

## Why this design was chosen

### 1. The quote endpoint is an adapter, not a persistence feature

The request explicitly said the quote data does **not** need to be stored in a database and does **not** need MyBatis or repository classes.

That means the implementation should stay lightweight and focused on the HTTP contract:

- validate input
- call Fauxnance
- map the response into backend DTOs
- return the contract shape to the caller

No schema changes or local persistence layer were added.

---

### 2. The implementation follows the same design as `GET /candles/{symbol}`

The candle endpoint already established the pattern for Fauxnance-backed market-data features:

- controller layer for request handling
- service layer for outbound HTTP logic
- domain objects for response parsing
- DTOs for response serialization
- focused tests at controller and service level

I reused that exact structure for quotes so the market-data endpoints remain visually and behaviorally aligned.

---

### 3. The code matches the published API contract

The root API contract defines a quote response with:

- `data.symbol`
- `data.price`
- `data.bid`
- `data.ask`
- `data.spreadBps`
- `data.currency`
- `data.change`
- `data.changePercent`
- `data.previousClose`
- `data.asOf`
- `data.marketState`
- `meta.asOf`
- `meta.disclaimer`
- `meta.symbol`
- `meta.source`
- `meta.spreadSource`
- `meta.stale`

The new backend classes were created to mirror that envelope directly instead of inventing a new response shape.

---

## What was added

### Controller

- `backend/src/main/java/com/neueda/leap/controller/FauxnanceQuoteController.java`

### Service

- `backend/src/main/java/com/neueda/leap/service/FauxnanceQuoteService.java`
- `backend/src/main/java/com/neueda/leap/service/impl/FauxnanceQuoteServiceImpl.java`

### Domain objects

- `backend/src/main/java/com/neueda/leap/domain/FauxnanceQuote.java`
- `backend/src/main/java/com/neueda/leap/domain/FauxnanceQuoteMeta.java`
- `backend/src/main/java/com/neueda/leap/domain/FauxnanceQuoteResponse.java`

### DTOs

- `backend/src/main/java/com/neueda/leap/dto/FauxnanceQuoteDto.java`
- `backend/src/main/java/com/neueda/leap/dto/FauxnanceQuoteMetaDto.java`
- `backend/src/main/java/com/neueda/leap/dto/FauxnanceQuoteResponseDto.java`

### Tests

- `backend/src/test/java/com/neueda/leap/controller/FauxnanceQuoteControllerTest.java`
- `backend/src/test/java/com/neueda/leap/service/impl/FauxnanceQuoteServiceImplTest.java`

### API documentation

- `docs/api.yaml`

---

## TDD process followed

I implemented this endpoint in a test-first order.

### Tests added first

1. `FauxnanceQuoteControllerTest`
   - successful JSON response for `GET /quotes/{symbol}`
   - validation error from the service maps to `400`

2. `FauxnanceQuoteServiceImplTest`
   - forwards the symbol to Fauxnance
   - sends the required `X-Api-Key` header
   - normalizes symbols to uppercase
   - deserializes the quote envelope into domain objects
   - maps upstream `404` responses to backend `404` responses

The tests established the intended behavior before the implementation existed, which is the core TDD loop.

---

## How the implementation works

### 1. Controller layer

`FauxnanceQuoteController` is intentionally thin.

It accepts:

- `symbol` as a path variable

It delegates directly to the service and returns `FauxnanceQuoteResponseDto`.

---

### 2. Service layer

`FauxnanceQuoteServiceImpl` is responsible for:

- validating that `symbol` is present
- normalizing the symbol to uppercase
- reading the Fauxnance base URL and API key from configuration
- building the outbound `/quotes/{symbol}` request
- attaching the `X-Api-Key` header
- calling Fauxnance through `RestTemplate`
- converting upstream HTTP errors into backend errors
- applying safe defaults when optional metadata is missing

### Defaults used

If the upstream payload omits some metadata, the service fills in safe values where appropriate:

- disclaimer defaults to `Educational data. Not for investment use.`
- spread source defaults to `modelled`
- stale defaults to `false`

That keeps the response stable and contract-friendly.

---

### 3. Domain and DTO mapping

The domain layer mirrors the Fauxnance response directly:

- `FauxnanceQuote` for the quote payload
- `FauxnanceQuoteMeta` for the envelope metadata
- `FauxnanceQuoteResponse` for the complete response

DTO records map those domain objects to JSON-ready response objects without exposing the service layer directly.

---

## Why `RestTemplate` was used

The existing project already uses Spring Web and `RestTemplate` fits the current testing approach well.

It also works cleanly with `MockRestServiceServer`, which made the service easy to verify in isolation without needing a running Fauxnance service during tests.

---

## Result

The backend now supports a quote endpoint that:

- follows the same structure as candles
- stays database-free
- matches the published contract
- is covered by controller and service tests
- is documented in the project OpenAPI spec



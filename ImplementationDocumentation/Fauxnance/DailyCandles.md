# Fauxnance Daily Candles Implementation

## Summary

This change implements the new candle retrieval endpoint backed by the external Fauxnance API.

Implemented endpoint:

- `GET /candles/{symbol}`

The controller also accepts:

- `GET /api/candles/{symbol}`

That dual mapping keeps the backend aligned with the project’s `/api` convention while also honoring the exact path requested from the Fauxnance contract.

---

## Why this design was chosen

## 1. `api-1.yaml` was treated as the source contract

The root-level `api-1.yaml` already described the candle endpoint in detail, including:

- path parameter: `symbol`
- query parameters: `from`, `to`, `interval`
- response envelope: `data` + `meta`
- candle row fields such as `adjclose`, `volume`, and `synthetic`

Because that contract already existed, the implementation was built to mirror its success payload rather than invent a new response shape.

---

## 2. No database or MyBatis layer was added intentionally

The request explicitly said candle data does not need to be stored locally yet.

Because of that, the implementation does **not** add:

- repository classes
- MyBatis mapper classes
- schema changes
- persistence entities tied to a table

Instead, the service acts as an adapter/proxy to Fauxnance.

That keeps the slice small and focused while leaving room to introduce persistence later without changing the controller contract.

---

## 3. The service keeps all query parameters flexible

The service method accepts:

- `symbol`
- `from`
- `to`
- `interval`

This was a deliberate choice so the endpoint does not hardcode today’s constraints into the web layer.

Right now the implementation only supports `1d`, because that is what the Fauxnance contract currently allows, but the method signature is already prepared for future interval expansion.

---

## 4. DTOs were added to match the existing codebase pattern

The existing backend style is:

- controller returns DTOs
- service returns domain objects
- response records map from domain objects

I kept that same pattern here, even though the endpoint is backed by an external service rather than the database.

That consistency makes the code easier to maintain beside the existing `Client`, `Advisor`, and `Instrument` features.

---

## TDD process followed

I implemented this test-first.

### Tests added first

1. `backend/src/test/java/com/neueda/leap/controller/FauxnanceDailyCandleControllerTest.java`
   - successful JSON response for `GET /candles/{symbol}`
   - invalid date range returns `400`

2. `backend/src/test/java/com/neueda/leap/service/impl/FauxnanceDailyCandleServiceImplTest.java`
   - forwards `symbol`, `from`, `to`, and `interval` to Fauxnance
   - sends the required `X-Api-Key` header
   - deserializes the Fauxnance response envelope into domain objects
   - defaults the interval to `1d` when omitted
   - rejects an inverted date range
   - maps Fauxnance `404` responses into backend `404` responses

### Initial red run

After adding the tests, I ran the candle-focused test slice before implementation.

That first run failed because the new candle controller/domain/service behavior did not exist yet, which confirmed the TDD starting point.

---

## Production files added

### Controller

- `backend/src/main/java/com/neueda/leap/controller/FauxnanceDailyCandleController.java`

### Domain models

- `backend/src/main/java/com/neueda/leap/domain/FauxnanceDailyCandle.java`
- `backend/src/main/java/com/neueda/leap/domain/FauxnanceDailyCandleData.java`
- `backend/src/main/java/com/neueda/leap/domain/FauxnanceDailyCandleResponse.java`
- `backend/src/main/java/com/neueda/leap/domain/FauxnanceMarketDataMeta.java`

### DTOs

- `backend/src/main/java/com/neueda/leap/dto/FauxnanceDailyCandleDto.java`
- `backend/src/main/java/com/neueda/leap/dto/FauxnanceDailyCandleDataDto.java`
- `backend/src/main/java/com/neueda/leap/dto/FauxnanceDailyCandleResponseDto.java`
- `backend/src/main/java/com/neueda/leap/dto/FauxnanceMarketDataMetaDto.java`

### Service

- `backend/src/main/java/com/neueda/leap/service/FauxnanceDailyCandleService.java`
- `backend/src/main/java/com/neueda/leap/service/impl/FauxnanceDailyCandleServiceImpl.java`

---

## Files updated

- `backend/src/main/resources/application.yml`
- `docs/api.yaml`
- `impDocs/Fauxnance/DailyCandles.md`

---

## How the implementation works

## 1. Controller layer

`FauxnanceDailyCandleController` handles the HTTP request and accepts:

- `symbol` as a path variable
- `from` as an optional ISO date query parameter
- `to` as an optional ISO date query parameter
- `interval` as an optional query parameter

The controller stays thin and delegates directly to the service.

It then maps the returned domain object into `FauxnanceDailyCandleResponseDto`.

---

## 2. Service layer

`FauxnanceDailyCandleServiceImpl` is responsible for:

- validating that `symbol` is present
- normalizing `symbol` to uppercase
- validating that `from <= to`
- validating that the requested range does not exceed ten years when both dates are provided
- enforcing the currently supported interval (`1d`)
- building the outbound Fauxnance URL with optional query parameters
- attaching the `X-Api-Key` header
- calling Fauxnance through Spring’s `RestTemplate`
- translating upstream HTTP failures into backend exceptions

### Why `RestTemplate` was used here

The project already includes Spring Web, and `RestTemplate` works very well with `MockRestServiceServer`, which made the service easy to test in isolation.

This produced a clean TDD loop for the outbound HTTP behavior without needing a running Fauxnance dependency during tests.

---

## 3. Response mapping

The success payload preserves the Fauxnance response envelope:

- `data.symbol`
- `data.interval`
- `data.currency`
- `data.candles[]`
- `meta.asOf`
- `meta.disclaimer`
- `meta.symbol`
- `meta.source`
- optional metadata such as `stale`, `partial`, and `availableFrom`

This keeps the backend response familiar for any consumer already looking at `api-1.yaml`.

---

## Fauxnance configuration

The service reads its runtime configuration from `backend/src/main/resources/application.yml` using environment-backed properties:

- `fauxnance.base-url` -> `${FAUX_SERVER_URL}`
- `fauxnance.api-key` -> `${FAUX_API_KEY}`
- `fauxnance.default-interval` -> `${FAUX_DEFAULT_INTERVAL}`
- `fauxnance.connect-timeout-seconds`
- `fauxnance.read-timeout-seconds`

### Existing workspace inputs used

I used the existing configuration inputs already present in the workspace:

- `.env.template`
- `actual_api_key`

The template already defines:

- `FAUX_SERVER_URL`
- `FAUX_API_KEY`

The actual key file contains the current dev key value that can be copied into `.env` for local runs.

### Why the key was not hardcoded

The endpoint needs the real key to call Fauxnance, but hardcoding it into source code or checked-in YAML would make secret handling worse.

Reading it from `FAUX_API_KEY` keeps the service usable in:

- local `.env`
- Docker Compose
- CI/CD secret injection

while still matching the workspace setup you described.

---

## API documentation updates

I updated `docs/api.yaml` to include the candle endpoint in the project-facing docs.

Added documentation includes:

- new `Market Data` tag
- `GET /candles/{symbol}` path
- query parameters `from`, `to`, and `interval`
- success schema for the Fauxnance candle envelope
- error response references for `400`, `401`, `404`, and `503`

### Why `docs/api.yaml` was updated separately from `api-1.yaml`

`api-1.yaml` already contained the Fauxnance contract details.

`docs/api.yaml` is the project’s application-facing API documentation, so it needed to be updated as the authoritative docs for this backend implementation.

---

## Error handling behavior

The implementation reuses the existing global exception strategy.

Current behavior:

- validation errors -> `400`
- Fauxnance symbol-not-found -> `404`
- missing Fauxnance API key configuration -> `500`
- unusable/empty upstream response -> `503`
- other upstream HTTP failures -> propagated using the upstream status where possible

This keeps the endpoint aligned with how the rest of the backend already reports errors.

---

## Verification performed

Validated with the new focused test suite:

```cmd
cd /d C:\Users\Administrator\Documents\tadpoles\backend
mvn -q -Dtest=FauxnanceDailyCandleControllerTest,FauxnanceDailyCandleServiceImplTest test
```

Recommended broader regression check:

```cmd
cd /d C:\Users\Administrator\Documents\tadpoles\backend
mvn -q test
```

---

## Result

The backend now has a tested, configurable Fauxnance daily-candle endpoint that:

- does not require local persistence
- accepts flexible query parameters
- reads Fauxnance configuration from environment-backed properties
- follows existing Tadpoles controller/service/DTO conventions
- is documented in the project API docs
- was implemented in TDD style


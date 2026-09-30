# ModelPortfolio and ModelPortfolioHolding Backend Implementation

## Overview

This document explains what was implemented for `ModelPortfolio` and `ModelPortfolioHolding`, why it was implemented that way, how the work followed the existing backend patterns, and what was refactored during review.

## Goal

Generate the backend layers for:
- `ModelPortfolio`
- `ModelPortfolioHolding`

while following the structure already used in:
- `controller`
- `domain`
- `dto`
- `mapper`
- `service`
- test packages

and update `docs/api.yaml` so the contract reflects the implementation and includes a fuller error surface.

---

## How I approached it

## 1. Pattern review first

Before adding any implementation, I reviewed the current backend style.

The most relevant existing patterns were:
- `AdvisorController` + `AdvisorServiceImpl` for a top-level resource
- `ClientHoldingController` + `ClientHoldingServiceImpl` for a nested resource with CRUD-style methods
- `ClientHoldingMapper` / `AdvisorMapper` for MyBatis annotation-driven persistence
- existing DTO record patterns using `fromEntity(...)`
- controller/service/mapper test patterns already in `src/test`

### Conclusion from the review

The project is using:
- Spring Boot controllers
- service interfaces + service implementations
- MyBatis `@Mapper` interfaces with inline SQL
- record-based request/response DTOs
- exception-driven validation and `GlobalExceptionHandler`
- focused tests by layer

That meant the most consistent design was:
- `ModelPortfolio` as a top-level resource
- `ModelPortfolioHolding` as a nested resource under a specific model portfolio

---

## 2. TDD first

I created tests before implementing the production code.

### Added controller tests
- `backend/src/test/java/com/neueda/leap/controller/ModelPortfolioControllerTest.java`
- `backend/src/test/java/com/neueda/leap/controller/ModelPortfolioHoldingControllerTest.java`

These verify:
- list endpoints return JSON payloads in the same style as the existing controllers
- create endpoints return `201 Created`
- get endpoints return `200 OK`
- update endpoints return `200 OK`
- invalid IDs are translated into `400 Bad Request`

### Added service tests
- `backend/src/test/java/com/neueda/leap/service/impl/ModelPortfolioServiceImplTest.java`
- `backend/src/test/java/com/neueda/leap/service/impl/ModelPortfolioHoldingServiceImplTest.java`

These verify:
- validation rules
- not-found behavior
- insert/update behavior
- dependency usage
- response retrieval after persistence

### Added mapper tests
- `backend/src/test/java/com/neueda/leap/mapper/ModelPortfolioMapperTest.java`
- `backend/src/test/java/com/neueda/leap/mapper/ModelPortfolioHoldingMapperTest.java`

These verify:
- list queries
- single-row queries
- insert behavior
- update behavior

### Red phase verification

I ran the new targeted tests before implementation.

As expected, the build failed because the new classes did not exist yet. That confirmed the TDD red phase was real and not a false green.

---

## 3. What I implemented

## DTOs

Added:
- `backend/src/main/java/com/neueda/leap/dto/CreateModelPortfolioRequestDto.java`
- `backend/src/main/java/com/neueda/leap/dto/UpdateModelPortfolioRequestDto.java`
- `backend/src/main/java/com/neueda/leap/dto/ModelPortfolioResponseDto.java`
- `backend/src/main/java/com/neueda/leap/dto/ModelPortfolioListResponseDto.java`
- `backend/src/main/java/com/neueda/leap/dto/CreateModelPortfolioHoldingRequestDto.java`
- `backend/src/main/java/com/neueda/leap/dto/UpdateModelPortfolioHoldingRequestDto.java`
- `backend/src/main/java/com/neueda/leap/dto/ModelPortfolioHoldingResponseDto.java`
- `backend/src/main/java/com/neueda/leap/dto/ModelPortfolioHoldingListResponseDto.java`

### Why this structure

This mirrors the current codebase style:
- request DTOs are small records
- response DTOs expose `fromEntity(...)`
- list response DTOs wrap collections under a named property

This is the same pattern already used for `Advisor`, `Client`, `ClientHolding`, and `Instrument`.

---

## Services

Added:
- `backend/src/main/java/com/neueda/leap/service/ModelPortfolioService.java`
- `backend/src/main/java/com/neueda/leap/service/ModelPortfolioHoldingService.java`
- `backend/src/main/java/com/neueda/leap/service/impl/ModelPortfolioServiceImpl.java`
- `backend/src/main/java/com/neueda/leap/service/impl/ModelPortfolioHoldingServiceImpl.java`

### `ModelPortfolioServiceImpl`

Supports:
- `getModelPortfolio(Integer id)`
- `listModelPortfolios()`
- `createModelPortfolio(CreateModelPortfolioRequestDto request)`
- `updateModelPortfolio(Integer id, UpdateModelPortfolioRequestDto request)`

### Validation rules implemented
- portfolio ID must be positive
- request object must be present
- `modelName` is required on create
- `modelName` cannot be blank on update
- `createdByUserId` must be positive when provided
- at least one update field must be supplied
- `isActive` defaults to `true` on create

### Why it was done this way

This follows the same validation flow already used in classes like:
- `AdvisorServiceImpl`
- `ClientServiceImpl`
- `ClientHoldingServiceImpl`

The style in this codebase is to:
- validate input in service layer
- create a domain object
- persist using mapper
- fetch the saved entity again and return it

I kept that same flow for consistency.

### `ModelPortfolioHoldingServiceImpl`

Supports:
- `listModelPortfolioHoldings(Integer modelPortfolioId)`
- `getModelPortfolioHolding(Integer modelPortfolioId, Integer instrumentId)`
- `createModelPortfolioHolding(Integer modelPortfolioId, CreateModelPortfolioHoldingRequestDto request)`
- `updateModelPortfolioHolding(Integer modelPortfolioId, Integer instrumentId, UpdateModelPortfolioHoldingRequestDto request)`

### Validation rules implemented
- portfolio ID must be positive
- instrument ID must be positive
- request object must be present
- target weight is required
- target weight must be between `0` and `100`
- at least one update field must be provided

### Additional dependency checks

For holdings I intentionally validated related resources by calling:
- `modelPortfolioService.getModelPortfolio(...)`
- `instrumentService.getInstrument(...)` on create

### Why this makes sense

This matches the existing nested-resource style in `ClientHoldingServiceImpl`, which validates the parent resource and related resource before insert/update. It helps surface a clean `404`/`400` path through the existing exception handler approach.

---

## Mappers

Added:
- `backend/src/main/java/com/neueda/leap/mapper/ModelPortfolioMapper.java`
- `backend/src/main/java/com/neueda/leap/mapper/ModelPortfolioHoldingMapper.java`

### Why MyBatis mappers were used

The backend is already using annotation-based MyBatis mappers instead of JPA repositories, so the new code follows that exact persistence approach.

### `ModelPortfolioMapper`

Added methods for:
- get by ID
- list all
- insert with generated key
- update using a dynamic SQL provider

### `ModelPortfolioHoldingMapper`

Added methods for:
- list by `modelPortfolioId`
- get by composite key (`modelPortfolioId`, `instrumentId`)
- insert
- update using a dynamic SQL provider

### Why the SQL provider pattern was used

Existing update mappers such as `AdvisorMapper` and `ClientHoldingMapper` already use `@UpdateProvider` with conditional field inclusion.

I reused that same approach instead of introducing a different persistence style.

---

## Controllers

Added:
- `backend/src/main/java/com/neueda/leap/controller/ModelPortfolioController.java`
- `backend/src/main/java/com/neueda/leap/controller/ModelPortfolioHoldingController.java`

### Exposed endpoints

#### Model portfolios
- `GET /api/model-portfolios`
- `POST /api/model-portfolios`
- `GET /api/model-portfolios/{modelPortfolioId}`
- `PATCH /api/model-portfolios/{modelPortfolioId}`

#### Model portfolio holdings
- `GET /api/model-portfolios/{modelPortfolioId}/holdings`
- `POST /api/model-portfolios/{modelPortfolioId}/holdings`
- `GET /api/model-portfolios/{modelPortfolioId}/holdings/{instrumentId}`
- `PATCH /api/model-portfolios/{modelPortfolioId}/holdings/{instrumentId}`

### Why these endpoints were chosen

This was an intentional consistency decision.

The previous OpenAPI draft for holdings suggested a bulk `PUT` replacement model for `/model-portfolios/{portfolioId}/holdings`, but the current Java backend patterns are much more CRUD-oriented and resource-specific.

Because the rest of the application already uses:
- `GET` list
- `POST` create
- `GET` single
- `PATCH` update

for both top-level and nested resources, I implemented the same pattern here.

That keeps the new code easier to understand for anyone already working in this codebase.

---

## Exceptions and error handling

Added:
- `backend/src/main/java/com/neueda/leap/exception/ModelPortfolioNotFoundException.java`
- `backend/src/main/java/com/neueda/leap/exception/ModelPortfolioHoldingNotFoundException.java`

Updated:
- `backend/src/main/java/com/neueda/leap/exception/GlobalExceptionHandler.java`

### Why this was needed

The project already maps domain-specific not-found exceptions to `404 Not Found` centrally.

To preserve that behavior and avoid controller-level error handling, I added the two new exceptions and registered them in `GlobalExceptionHandler`.

This keeps the new feature aligned with the existing exception architecture.

---

## Test resources and fixture refactor

Updated:
- `backend/src/test/resources/mapper/schema.sql`

Added:
- `backend/src/test/resources/mapper/model_portfolio_data.sql`

### Why the schema changed

The mapper test schema did not yet include `model_portfolio_holdings`, so mapper tests for the new resource could not run without it.

I added the table definition to the mapper schema used by MyBatis tests.

### Important refactor during review

Initially I extended the shared `mapper/data.sql` with:
- a second instrument
- a `model_portfolio_holdings` row

That made the new mapper tests pass, but it unintentionally broke the existing `InstrumentMapperTest`, because that test assumed only one instrument existed in the shared fixture set.

### Refactor applied

To fix that correctly, I:
- restored the shared `mapper/data.sql` back to its original assumptions
- created a dedicated fixture file: `mapper/model_portfolio_data.sql`
- updated the new portfolio mapper tests to use the dedicated fixture data instead of the shared one

### Why this refactor was the right fix

This avoided changing unrelated test expectations and improved isolation.

That is a better long-term testing design because the new tests no longer leak their data assumptions into unrelated test suites.

---

## OpenAPI changes

Updated:
- `docs/api.yaml`

### What changed

I aligned the OpenAPI definition with the implemented backend:

#### Model portfolio paths
- `GET /model-portfolios`
- `POST /model-portfolios`
- `GET /model-portfolios/{modelPortfolioId}`
- `PATCH /model-portfolios/{modelPortfolioId}`

#### Model portfolio holding paths
- `GET /model-portfolios/{modelPortfolioId}/holdings`
- `POST /model-portfolios/{modelPortfolioId}/holdings`
- `GET /model-portfolios/{modelPortfolioId}/holdings/{instrumentId}`
- `PATCH /model-portfolios/{modelPortfolioId}/holdings/{instrumentId}`

### Added/updated schema definitions
- `ModelPortfolioSummary`
- `ModelPortfolioListResponse`
- `CreateModelPortfolioRequest`
- `UpdateModelPortfolioRequest`
- `ModelPortfolioHoldingSummary`
- `ModelPortfolioHoldingListResponse`
- `CreateModelPortfolioHoldingRequest`
- `UpdateModelPortfolioHoldingRequest`
- `ModelPortfolioId` path parameter

### Error responses expanded

For the relevant new/updated endpoints I added the fuller error surface you requested, including combinations of:
- `200`
- `201`
- `400`
- `401`
- `403`
- `404`
- `409`
- `429`
- `500`

### Why the API contract was refactored

The previous contract described a holdings replacement workflow (`PUT` with a bulk list). That did not match the backend layer pattern already used elsewhere in the Java code.

Since the request explicitly asked to copy the patterns and structure of the current implementation, I refactored the OpenAPI contract to describe the CRUD-oriented resource pattern actually implemented.

---

## Does this follow the current pattern?

Yes.

### It matches the current backend style in these ways
- controllers are thin and delegate to services
- services own validation and orchestration
- mappers use annotation-based MyBatis SQL
- response DTOs use `fromEntity(...)`
- update operations use partial update semantics with `PATCH`
- not-found behavior is exception-based and centrally handled
- tests are separated by layer: controller, service, mapper

### Why this makes sense architecturally

`ModelPortfolio` behaves like a top-level business resource, similar to `Advisor` or `Instrument`.

`ModelPortfolioHolding` behaves like a nested child resource keyed by:
- parent portfolio ID
- instrument ID

That maps naturally to the same nested style already used for `ClientHolding`.

---

## Files added

### Main code
- `backend/src/main/java/com/neueda/leap/controller/ModelPortfolioController.java`
- `backend/src/main/java/com/neueda/leap/controller/ModelPortfolioHoldingController.java`
- `backend/src/main/java/com/neueda/leap/dto/CreateModelPortfolioRequestDto.java`
- `backend/src/main/java/com/neueda/leap/dto/UpdateModelPortfolioRequestDto.java`
- `backend/src/main/java/com/neueda/leap/dto/ModelPortfolioResponseDto.java`
- `backend/src/main/java/com/neueda/leap/dto/ModelPortfolioListResponseDto.java`
- `backend/src/main/java/com/neueda/leap/dto/CreateModelPortfolioHoldingRequestDto.java`
- `backend/src/main/java/com/neueda/leap/dto/UpdateModelPortfolioHoldingRequestDto.java`
- `backend/src/main/java/com/neueda/leap/dto/ModelPortfolioHoldingResponseDto.java`
- `backend/src/main/java/com/neueda/leap/dto/ModelPortfolioHoldingListResponseDto.java`
- `backend/src/main/java/com/neueda/leap/exception/ModelPortfolioNotFoundException.java`
- `backend/src/main/java/com/neueda/leap/exception/ModelPortfolioHoldingNotFoundException.java`
- `backend/src/main/java/com/neueda/leap/mapper/ModelPortfolioMapper.java`
- `backend/src/main/java/com/neueda/leap/mapper/ModelPortfolioHoldingMapper.java`
- `backend/src/main/java/com/neueda/leap/service/ModelPortfolioService.java`
- `backend/src/main/java/com/neueda/leap/service/ModelPortfolioHoldingService.java`
- `backend/src/main/java/com/neueda/leap/service/impl/ModelPortfolioServiceImpl.java`
- `backend/src/main/java/com/neueda/leap/service/impl/ModelPortfolioHoldingServiceImpl.java`

### Tests
- `backend/src/test/java/com/neueda/leap/controller/ModelPortfolioControllerTest.java`
- `backend/src/test/java/com/neueda/leap/controller/ModelPortfolioHoldingControllerTest.java`
- `backend/src/test/java/com/neueda/leap/service/impl/ModelPortfolioServiceImplTest.java`
- `backend/src/test/java/com/neueda/leap/service/impl/ModelPortfolioHoldingServiceImplTest.java`
- `backend/src/test/java/com/neueda/leap/mapper/ModelPortfolioMapperTest.java`
- `backend/src/test/java/com/neueda/leap/mapper/ModelPortfolioHoldingMapperTest.java`
- `backend/src/test/resources/mapper/model_portfolio_data.sql`

### Updated files
- `backend/src/main/java/com/neueda/leap/exception/GlobalExceptionHandler.java`
- `backend/src/test/resources/mapper/schema.sql`
- `backend/src/test/resources/mapper/data.sql`
- `docs/api.yaml`

---

## Validation and test results

## Targeted TDD verification

I ran the new portfolio-focused test suite first.

Result:
- red phase confirmed before implementation
- green phase confirmed after implementation

## Full backend regression suite

I then ran the full backend Maven test suite.

Final result:
- `141` tests run
- `0` failures
- `0` errors

---

## Final notes

### Intentional refactor called out explicitly

I explicitly refactored the mapper test data strategy after the full regression run surfaced a shared-fixture problem.

That refactor improved test isolation and prevented the new feature from changing assumptions in unrelated tests.

### Why this solution is maintainable

This implementation is maintainable because it does not introduce a new architectural pattern. It extends the current backend in the same way the existing resources are already structured, which lowers surprise for future contributors.


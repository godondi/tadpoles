# Advisor Backend Implementation

## What was implemented

This change adds the full backend slice for `Advisor`, following the same project structure already used by `Client`, `Instrument`, `ClientHolding`, and `ClientTrade`.

Implemented layers:

- domain-backed REST controller
- request and response DTOs
- MyBatis mapper
- service interface
- service implementation
- not-found exception handling
- controller, service, and mapper tests
- OpenAPI updates for advisor endpoints

It also adds the advisor-to-clients query that the existing OpenAPI contract already described but the backend did not yet support.

---

## TDD-first approach

I built this test-first before adding the production code.

### Tests added first

1. `AdvisorControllerTest`
   - list advisors
   - create advisor
   - get advisor
   - update advisor
   - list advisor clients
   - invalid advisor id returns `400`

2. `AdvisorServiceImplTest`
   - advisor lookup
   - advisor list
   - create advisor with trimmed name
   - update advisor
   - list advisor clients
   - not-found behavior
   - input validation

3. `AdvisorMapperTest`
   - select by id
   - list
   - insert with generated id
   - partial update

4. `ClientMapperTest`
   - added coverage for `listClientsByAdvisor(...)`

I ran the new advisor-focused tests before implementation so they failed on missing classes and methods, then implemented the backend until those tests passed.

---

## Pattern review and why this design matches the codebase

Before implementing the advisor slice, I reviewed the existing `Client`, `Instrument`, `ClientHolding`, and `ClientTrade` code.

The advisor work intentionally mirrors those patterns:

- **Controller** returns DTOs, not raw domain entities
- **Service** owns validation and not-found behavior
- **Mapper** uses MyBatis annotations and dynamic update SQL providers
- **DTOs** are Java records with `fromEntity(...)` helpers on response records
- **Exceptions** are translated centrally in `GlobalExceptionHandler`

That kept the new code consistent with the current implementation style instead of introducing a different architecture only for advisors.

---

## Files added

### Production

- `backend/src/main/java/com/neueda/leap/controller/AdvisorController.java`
- `backend/src/main/java/com/neueda/leap/dto/AdvisorListResponseDto.java`
- `backend/src/main/java/com/neueda/leap/dto/AdvisorResponseDto.java`
- `backend/src/main/java/com/neueda/leap/dto/CreateAdvisorRequestDto.java`
- `backend/src/main/java/com/neueda/leap/dto/UpdateAdvisorRequestDto.java`
- `backend/src/main/java/com/neueda/leap/exception/AdvisorNotFoundException.java`
- `backend/src/main/java/com/neueda/leap/mapper/AdvisorMapper.java`
- `backend/src/main/java/com/neueda/leap/service/AdvisorService.java`
- `backend/src/main/java/com/neueda/leap/service/impl/AdvisorServiceImpl.java`

### Tests

- `backend/src/test/java/com/neueda/leap/controller/AdvisorControllerTest.java`
- `backend/src/test/java/com/neueda/leap/mapper/AdvisorMapperTest.java`
- `backend/src/test/java/com/neueda/leap/service/impl/AdvisorServiceImplTest.java`

### Documentation

- `impDocs/Advisor.md`

---

## Files updated

- `backend/src/main/java/com/neueda/leap/mapper/ClientMapper.java`
- `backend/src/main/java/com/neueda/leap/exception/GlobalExceptionHandler.java`
- `backend/src/test/java/com/neueda/leap/mapper/ClientMapperTest.java`
- `backend/src/test/java/com/neueda/leap/service/impl/OrderFillServiceIntegrationTest.java`
- `docs/api.yaml`

---

## Advisor endpoints implemented

Base path:

- `/api/advisors`

Endpoints added:

- `GET /api/advisors`
- `POST /api/advisors`
- `GET /api/advisors/{advisorId}`
- `PATCH /api/advisors/{advisorId}`
- `GET /api/advisors/{advisorId}/clients`

### Why these endpoints

These are the advisor-facing backend operations already implied by the OpenAPI contract and by the data model:

- fetch advisors
- create advisors
- update advisor metadata
- retrieve one advisor
- retrieve the advisor's assigned clients

---

## Service behavior and validation

### `AdvisorServiceImpl`

The service is responsible for all business-side validation, matching the existing project pattern.

Implemented validation:

- advisor id must be a positive integer
- create request is required
- update request is required
- advisor name is required on create
- advisor name cannot be blank on update
- user id is optional, but when provided it must be positive
- update requires at least one field

### Not-found behavior

`AdvisorServiceImpl#getAdvisor(...)` throws `AdvisorNotFoundException` when the record does not exist.

The same not-found behavior is reused by:

- `updateAdvisor(...)`
- `listAdvisorClients(...)`

This keeps missing-advisor handling consistent across all advisor endpoints.

---

## Mapper behavior

### `AdvisorMapper`

The mapper follows the same annotation-driven style as the existing mappers:

- `getAdvisor(...)`
- `listAdvisors()`
- `insertAdvisor(...)`
- `updateAdvisor(...)`

`updateAdvisor(...)` uses a dynamic SQL provider so patch-style updates only write the supplied fields, which matches the current `ClientMapper`, `InstrumentMapper`, and `ClientTradeMapper` approach.

### `ClientMapper` extension

I added:

- `listClientsByAdvisor(Integer advisorId)`

I used `ClientMapper` for this instead of duplicating client-query SQL inside `AdvisorMapper`.

### Why that choice makes sense

The query returns `Client` rows, so keeping that SQL in the client mapper avoids splitting responsibility for the same entity across multiple mappers.

---

## Controller behavior

`AdvisorController` maps the service layer onto REST endpoints and returns DTOs rather than domain objects.

For advisor-client listing, it reuses the existing `ClientListResponseDto`, which keeps response formatting aligned with the rest of the backend and avoids duplicating client response models.

---

## Exception handling

I extended `GlobalExceptionHandler` to translate `AdvisorNotFoundException` into a `404` response.

That matches the existing handling style for:

- `ClientNotFoundException`
- `InstrumentNotFoundException`
- `ClientHoldingNotFoundException`
- `ClientTradeNotFoundException`

---

## OpenAPI changes

I updated `docs/api.yaml` in two ways.

### 1. Advisor path responses were completed

For advisor endpoints, I added the documented response set for success and common failures, including:

- `200`
- `201`
- `400`
- `401`
- `403`
- `404`
- `409`
- `429`
- `500`

### 2. Shared error responses now have a response body schema

I added an `ApiErrorResponse` schema that matches the backend error payload shape:

- `timestamp`
- `status`
- `error`
- `message`
- `path`

I then wired that schema into the shared response components:

- `BadRequest`
- `Unauthorized`
- `Forbidden`
- `NotFound`
- `Conflict`
- `TooManyRequests`
- `InternalServerError`

### Why this was done

Before this change, the contract described several errors only as plain descriptions. The new schema makes the advisor API contract more useful for frontend work, client generation, and test expectations because consumers can now see the actual error payload shape.

---

## Refactors and incidental fixes

I made one small validation-related test environment fix while running the full backend suite:

- `OrderFillServiceIntegrationTest` now provides a `jwt.secret` test property

### Why this was changed

The integration test loads the full Spring Boot context, which includes `SecurityConfig`. Without a JWT secret property, the context could not start, which blocked full-suite verification. This change is test-only and does not alter production behavior.

---

## Why the final implementation is safe and consistent

The advisor code was kept intentionally close to the existing project conventions:

- same package layout
- same DTO style
- same mapper style
- same service validation style
- same exception translation pattern
- same test structure

That reduces cognitive overhead for future developers and keeps advisor support easy to maintain beside the existing client and instrument code.

---

## Verification

Validated with:

```cmd
cd /d C:\Users\Administrator\Desktop\tadpoles\backend
mvn -q test
```

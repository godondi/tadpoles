# GET `/api/client/{id}` End-to-End Implementation Notes

This document explains the design and implementation of the first backend endpoint in the Spring Boot application.

## Goal

Implement a working end-to-end backend flow for:

- `GET /api/client/{id}`

with this layer sequence:

1. request enters the controller
2. controller calls `getClient(id)` in the service layer
3. service calls the MyBatis mapper
4. mapper loads the `Client` entity from the database
5. entity is returned upward through the layers
6. controller maps the entity to a DTO
7. Jackson serializes the DTO into JSON

---

## Why this endpoint was chosen first

`GET /api/client/{id}` is a good starter endpoint because it exercises the full application stack without introducing write-path complexity.

It verifies:

- Spring Boot controller wiring
- service orchestration
- MyBatis mapper integration
- domain entity reuse
- DTO mapping
- JSON serialization
- basic error handling
- test structure for future endpoints

This makes it a strong template for additional read endpoints.

---

## Existing project context that influenced the design

The current backend already contained domain/entity classes such as:

- `com.neueda.leap.domain.Client`
- `com.neueda.leap.domain.Advisors`
- `com.neueda.leap.domain.Users`

The architecture docs also established these expectations:

- controller -> service -> mapper -> entity
- MyBatis is used instead of repository objects
- DTOs should be used for API responses
- the schema already defines the `clients` table and its columns

Because of that, the implementation was intentionally kept aligned with the existing `Client` entity instead of creating a duplicate persistence model.

---

## Files added or changed

### Updated foundation files

- `backend/pom.xml`
- `backend/src/main/java/com/neueda/leap/Main.java`

### Runtime configuration

- `backend/src/main/resources/application.yml`

### Endpoint implementation files

- `backend/src/main/java/com/neueda/leap/controller/ClientController.java`
- `backend/src/main/java/com/neueda/leap/service/ClientService.java`
- `backend/src/main/java/com/neueda/leap/service/impl/ClientServiceImpl.java`
- `backend/src/main/java/com/neueda/leap/mapper/ClientMapper.java`
- `backend/src/main/java/com/neueda/leap/dto/ClientResponseDto.java`
- `backend/src/main/java/com/neueda/leap/exception/ClientNotFoundException.java`
- `backend/src/main/java/com/neueda/leap/exception/ApiErrorResponse.java`
- `backend/src/main/java/com/neueda/leap/exception/GlobalExceptionHandler.java`

### Tests

- `backend/src/test/java/com/neueda/leap/service/impl/ClientServiceImplTest.java`
- `backend/src/test/java/com/neueda/leap/controller/ClientControllerTest.java`

---

## Design decisions and rationale

## 1. Spring Boot was added before the endpoint itself

### Decision
Convert the backend module into a real Spring Boot application before adding controller/service/mapper classes.

### Why
Without Spring Boot infrastructure, the endpoint could not be wired or tested properly.

The backend originally only had:

- plain Maven setup
- a placeholder `Main` class
- domain classes

It did **not** yet have:

- Spring MVC
- dependency injection
- Jackson HTTP serialization
- MyBatis Spring integration
- test support for MVC endpoints

### Result
The `pom.xml` was updated to include:

- `spring-boot-starter-web`
- `mybatis-spring-boot-starter`
- `postgresql`
- `spring-boot-starter-test`

and `Main.java` was converted into a `@SpringBootApplication`.

---

## 2. The implementation reuses the existing `Client` entity

### Decision
Use the existing `com.neueda.leap.domain.Client` class as the entity returned by the mapper.

### Why
This matches the desired flow exactly:

- mapper returns entity
- entity moves up through the service layer
- controller maps entity to DTO

It also avoids introducing unnecessary `EntityRecord` classes for a simple first endpoint.

### Result
The MyBatis mapper returns `Client` directly.

---

## 3. The mapper uses annotation-based SQL

### Decision
Implement the mapper with an inline `@Select` query rather than an XML mapper file.

### Why
For one endpoint, annotation-based SQL is the smallest clean implementation.

It keeps all required pieces visible in one place:

- interface
- SQL
- return type

This is a good fit for a starter endpoint.

If the mapper layer becomes larger later, XML mappers can still be introduced without changing the service/controller contract.

---

## 4. The service layer keeps business responsibility

### Decision
The service performs:

- input validation
- not-found handling
- delegation to the mapper

### Why
The controller should stay thin and focused on HTTP concerns.

Putting validation and existence checks in the service means:

- future callers can reuse the logic
- business rules do not leak into the web layer
- tests stay simpler and more targeted

### Result
`ClientServiceImpl#getClient(Integer id)`:

- rejects non-positive IDs
- calls `ClientMapper#getClient(id)`
- throws `ClientNotFoundException` if the row is missing
- returns the `Client` entity otherwise

---

## 5. The controller returns a DTO instead of the entity

### Decision
The controller maps `Client` into `ClientResponseDto`.

### Why
This preserves a clean separation between:

- persistence/domain shape
- API response shape

Even though the entity could technically be serialized directly, returning a DTO is safer because it:

- avoids leaking internal fields accidentally
- allows response shaping independent of database changes
- matches the architecture already shown in the UML

### Result
The response DTO only exposes the client summary fields:

- `clientId`
- `clientName`
- `advisorId`
- `modelPortfolioId`

This aligns with the API documentation’s client summary concept.

---

## 6. Both singular and plural routes are accepted

### Decision
The controller method accepts both:

- `/api/client/{id}`
- `/api/clients/{id}`

### Why
The explicit request for this task used the singular path, while the API starter docs already leaned toward plural naming.

Supporting both paths:

- satisfies the requested endpoint exactly
- keeps compatibility with the existing API direction
- avoids forcing a naming decision too early

This is a pragmatic bridge for an early-stage codebase.

---

## 7. Focused exception handling was added

### Decision
Add a small global exception handler for:

- invalid client IDs -> `400 Bad Request`
- missing clients -> `404 Not Found`

### Why
This makes the first endpoint production-shaped rather than only demo-shaped.

It also establishes a reusable error response pattern for future endpoints.

### Result
`GlobalExceptionHandler` returns a consistent JSON structure with:

- timestamp
- HTTP status
- error name
- message
- request path

---

## 8. MyBatis runtime config was kept minimal

### Decision
Use a minimal `application.yml` with datasource placeholders and MyBatis camel-case mapping.

### Why
The mapper returns an entity with Java-style field names like:

- `clientId`
- `clientName`
- `modelPortfolioId`

while the database uses snake_case columns like:

- `client_id`
- `client_name`
- `model_portfolio_id`

Turning on `map-underscore-to-camel-case` keeps mapper code simpler and reduces repetitive field mapping.

---

## 9. Tests were added at two levels

### Decision
Add:

- one service test
- one controller test

### Why
That gives good confidence without overbuilding the first slice.

#### Service test checks
- success path
- not-found path
- invalid ID path

#### Controller test checks
- HTTP 200 response and JSON body
- HTTP 400 response shape for invalid input

This verifies both business behavior and web serialization behavior.

---

## Request flow walkthrough

## 1. HTTP request enters controller

Example request:

`GET /api/client/7`

Spring routes the request to `ClientController#getClient(Integer id)`.

## 2. Controller delegates to service

The controller does not talk to the database directly.

It calls:

- `clientService.getClient(id)`

## 3. Service validates and delegates to mapper

The service:

- checks that `id` is positive
- calls `clientMapper.getClient(id)`

## 4. MyBatis fetches the entity

The mapper runs SQL against the `clients` table and hydrates a `Client` object.

## 5. Service handles not-found logic

If MyBatis returns `null`, the service throws `ClientNotFoundException`.

Otherwise it returns the populated `Client` entity.

## 6. Controller maps entity to DTO

The controller calls:

- `ClientResponseDto.fromEntity(client)`

This creates the API response object.

## 7. Jackson serializes the DTO

Because the controller is annotated with `@RestController`, Spring uses Jackson to serialize the DTO into JSON automatically.

Example response:

```json
{
  "clientId": 7,
  "clientName": "Alice Investor",
  "advisorId": 3,
  "modelPortfolioId": 5
}
```

---

## Why this implementation is a good starter pattern

This first endpoint creates a reusable pattern for the rest of the backend:

- controller stays thin
- service owns business logic
- mapper owns SQL
- domain entity is the persistence object
- DTO owns response shape
- exceptions are translated consistently
- tests verify both service and HTTP layers

That means future endpoints like:

- `GET /api/clients/{id}/holdings`
- `GET /api/clients/{id}/trades`
- `POST /api/advisors/{advisorId}/clients/{clientId}/trade-suggestions`

can follow the same approach.

---

## Tradeoffs that were intentionally accepted

### Kept simple
- no auth/security layer yet
- no database integration test yet
- no XML mapper files yet
- no complex DTO mapping library

### Added anyway because they pay off immediately
- service interface
- custom not-found exception
- global exception handler
- DTO-based response
- controller and service tests

This keeps the implementation small, but not fragile.

---

## Suggested next endpoint after this one

The next natural step would be one of:

1. `GET /api/clients/{id}/holdings`
2. `GET /api/clients/{id}/trades`
3. `POST /api/advisors/{advisorId}/clients/{clientId}/trade-suggestions`

Those would extend the same controller -> service -> mapper -> entity -> DTO pattern established here.



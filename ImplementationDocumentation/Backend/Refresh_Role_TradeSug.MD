# RefreshToken, Role, and TradeSuggestion Backend Implementation

## Summary
This change set adds backend layers for `RefreshToken`, `Role`, and `TradeSuggestion` by following the existing project structure and patterns already used across the Spring Boot + MyBatis backend.

The implementation was done **test-first**:
1. create failing mapper/service/controller tests,
2. implement DTOs, mappers, services, controllers, and exceptions,
3. update the OpenAPI contract,
4. run targeted tests,
5. run the full backend suite and fix any regression uncovered.

---

## What I implemented

### 1. New controllers
Added:
- `backend/src/main/java/com/neueda/leap/controller/RefreshTokenController.java`
- `backend/src/main/java/com/neueda/leap/controller/RoleController.java`
- `backend/src/main/java/com/neueda/leap/controller/TradeSuggestionController.java`

#### Why this structure
I matched the existing controller pattern already used by classes like:
- `AdvisorController`
- `AppUserController`
- `ClientTradeController`

That means:
- controllers stay thin,
- request/response mapping happens through DTOs,
- validation/business rules live in services,
- role checks reuse `SecurityRoleSupport`.

#### Endpoints added / wired
- `POST /api/auth/refresh`
- `POST /api/auth/logout`
- `GET /api/users/{userId}/refresh-tokens`
- `GET /api/refresh-tokens/{refreshTokenId}`
- `POST /api/users/{userId}/refresh-tokens`
- `GET /api/roles`
- `GET /api/roles/{roleId}`
- `POST /api/advisors/{advisorId}/clients/{clientId}/trade-suggestions`
- `GET /api/clients/{clientId}/trade-suggestions`
- `GET /api/trade-suggestions/{suggestionId}`
- `PATCH /api/trade-suggestions/{suggestionId}`

---

### 2. New DTOs
Added request/response DTOs so the new layers follow the current record-based DTO style:

#### Role DTOs
- `RoleResponseDto`
- `RoleListResponseDto`

#### TradeSuggestion DTOs
- `CreateTradeSuggestionRequestDto`
- `UpdateTradeSuggestionRequestDto`
- `TradeSuggestionResponseDto`
- `TradeSuggestionListResponseDto`

#### RefreshToken DTOs
- `RefreshTokenRequestDto`
- `CreateRefreshTokenRequestDto`
- `AuthTokensResponseDto`
- `IssuedRefreshTokenResponseDto`
- `RefreshTokenResponseDto`
- `RefreshTokenListResponseDto`

#### Why this way
This mirrors the pattern already established by files such as:
- `AdvisorResponseDto`
- `AdvisorListResponseDto`
- `CreateAdvisorRequestDto`
- `AppUserResponseDto`

I kept response DTOs responsible for converting from domain entities through static `fromEntity(...)` or `fromEntities(...)` methods, because that is the dominant existing style in the codebase.

---

### 3. New mappers
Added:
- `backend/src/main/java/com/neueda/leap/mapper/RefreshTokenMapper.java`
- `backend/src/main/java/com/neueda/leap/mapper/RoleMapper.java`
- `backend/src/main/java/com/neueda/leap/mapper/TradeSuggestionMapper.java`

#### Why this structure
These follow the current MyBatis annotation-based approach:
- `@Select`
- `@Insert`
- `@Options(useGeneratedKeys = true, ...)`
- `@UpdateProvider` when partial updates are required

This is consistent with existing mappers such as:
- `AdvisorMapper`
- `AppUserMapper`
- `ClientTradeMapper`

#### Behavior implemented
- `RoleMapper`: fetch by id, list all roles
- `TradeSuggestionMapper`: list by client, fetch by suggestion id, insert, partial update
- `RefreshTokenMapper`: fetch by id, fetch by token hash, list by user, insert, revoke

---

### 4. New services
Added interfaces:
- `backend/src/main/java/com/neueda/leap/service/RefreshTokenService.java`
- `backend/src/main/java/com/neueda/leap/service/RoleService.java`
- `backend/src/main/java/com/neueda/leap/service/TradeSuggestionService.java`

Added implementations:
- `backend/src/main/java/com/neueda/leap/service/impl/RefreshTokenServiceImpl.java`
- `backend/src/main/java/com/neueda/leap/service/impl/RoleServiceImpl.java`
- `backend/src/main/java/com/neueda/leap/service/impl/TradeSuggestionServiceImpl.java`

#### Why this structure
I copied the existing service pattern:
- interface in `service/`
- concrete business logic in `service/impl/`
- validation done before mapper calls
- use of domain exceptions for not-found resources

This mirrors services like:
- `AdvisorServiceImpl`
- `AppUserServiceImpl`
- `ClientTradeServiceImpl`

---

## Business rules implemented

### Role
- Role id must be a positive integer.
- `getRole(id)` throws `RoleNotFoundException` when missing.
- `listRoles()` returns all roles ordered by `role_id`.

### TradeSuggestion
- Advisor id, client id, instrument id, and suggestion id must be positive.
- `tradeType` must be `BUY` or `SELL`.
- `quantity` must be greater than zero.
- `proposedPrice`, when provided, must be greater than zero.
- When creating a suggestion, the selected client must belong to the advisor making the suggestion.
- New suggestions are normalized to:
  - trimmed notes
  - uppercase trade type
  - status `SUGGESTED`
- Status updates only allow client decision-style transitions:
  - `VIEWED`
  - `ACCEPTED`
  - `REJECTED`
  - `EXPIRED`
- Missing suggestions raise `TradeSuggestionNotFoundException`.

### RefreshToken
- Refresh token id and user id must be positive.
- Raw refresh tokens are never stored directly.
- Stored values use SHA-256 hashing.
- `issueRefreshToken(...)`:
  - validates the user exists,
  - creates an opaque raw token,
  - stores only the hash,
  - returns the raw token once in the response.
- `refreshAccessToken(...)`:
  - validates token presence,
  - hashes inbound token and looks it up,
  - rejects missing, revoked, or expired tokens,
  - revokes the current token,
  - rotates to a brand-new refresh token,
  - returns a new access token payload plus the rotated refresh token.
- `revokeRefreshToken(...)`:
  - resolves the token by hash,
  - revokes it if still active,
  - safely no-ops if already revoked.

#### Note on access-token generation
The current codebase does not yet expose a real JWT issuing/authentication service implementation in this backend module. To stay consistent with the request and keep the solution self-contained, I returned an opaque access-token string from `RefreshTokenServiceImpl` rather than inventing a wider auth subsystem that does not currently exist in the workspace.

That keeps the implementation aligned with the existing backend state while still providing the full refresh-token lifecycle and controller/service layers the request asked for.

---

## Exceptions added
Added:
- `RoleNotFoundException`
- `TradeSuggestionNotFoundException`
- `RefreshTokenNotFoundException`

Updated:
- `backend/src/main/java/com/neueda/leap/exception/GlobalExceptionHandler.java`

#### Why
The rest of the project uses resource-specific exceptions mapped to standard API error payloads. I extended that same pattern so the new resources return the same `ApiErrorResponse` contract as existing endpoints.

---

## OpenAPI updates
Updated:
- `docs/api.yaml`

### Added/expanded documentation for
- role endpoints
- refresh-token admin endpoints
- auth refresh/logout response coverage
- trade suggestion endpoint response/error coverage
- new schemas for role and refresh-token payloads
- new path parameters:
  - `RoleId`
  - `RefreshTokenId`

### Error responses included
I explicitly added standard error coverage where applicable, including:
- `200`
- `201`
- `204`
- `400`
- `401`
- `404`
- `409` where already part of surrounding resource patterns
- `429`
- `500`

This was done because the request specifically called out broad HTTP error documentation such as `429`, `200`, and `401`.

---

## TDD workflow used

### Step 1: create tests first
I added tests before the production code for all three areas.

#### Mapper tests
- `RoleMapperTest`
- `TradeSuggestionMapperTest`
- `RefreshTokenMapperTest`

#### Service tests
- `RoleServiceImplTest`
- `TradeSuggestionServiceImplTest`
- `RefreshTokenServiceImplTest`

#### Controller tests
- `RoleControllerTest`
- `TradeSuggestionControllerTest`
- `RefreshTokenControllerTest`

These tests validated:
- response JSON structure,
- role-based access control behavior,
- validation and normalization,
- not-found behavior,
- insert/update/revoke flows,
- refresh-token rotation behavior.

### Step 2: implement to satisfy tests
After the failing tests established the intended behavior, I added the production classes and wired them until the targeted suite passed.

### Step 3: broader regression pass
After targeted success, I ran the full backend suite. That uncovered a shared H2 test-fixture issue unrelated to business logic, which I fixed.

---

## Refactor explicitly performed
I did perform a small test-fixture refactor during review:

### Shared mapper schema cleanup hardening
Updated:
- `backend/src/test/resources/mapper/schema.sql`
- `backend/src/test/resources/mapper/revised_schema.sql`
- `backend/src/test/resources/mapper/revised_data.sql`

#### Why
When I ran the full backend test suite, H2 failed to drop `clients` in one mapper suite because tables introduced by newer tests (notably `trade_suggestions`) could still exist in reused test contexts.

#### What changed
- Added missing drop ordering for newer tables like `trade_suggestions` and `refresh_tokens`.
- Extended revised mapper fixtures to support the new mapper tests.

#### Why this refactor makes sense
This change improves test isolation and keeps the entire mapper suite stable, not just the new tests added for this feature.

---

## Test evidence
### Targeted TDD suite
Passed after implementation:
- `RoleMapperTest`
- `TradeSuggestionMapperTest`
- `RefreshTokenMapperTest`
- `RoleServiceImplTest`
- `TradeSuggestionServiceImplTest`
- `RefreshTokenServiceImplTest`
- `RoleControllerTest`
- `TradeSuggestionControllerTest`
- `RefreshTokenControllerTest`

### Full backend suite
Passed:
- `mvn test`
- Result: **227 tests, 0 failures, 0 errors**

---

## Files added

### Controllers
- `backend/src/main/java/com/neueda/leap/controller/RefreshTokenController.java`
- `backend/src/main/java/com/neueda/leap/controller/RoleController.java`
- `backend/src/main/java/com/neueda/leap/controller/TradeSuggestionController.java`

### DTOs
- `backend/src/main/java/com/neueda/leap/dto/AuthTokensResponseDto.java`
- `backend/src/main/java/com/neueda/leap/dto/CreateRefreshTokenRequestDto.java`
- `backend/src/main/java/com/neueda/leap/dto/CreateTradeSuggestionRequestDto.java`
- `backend/src/main/java/com/neueda/leap/dto/IssuedRefreshTokenResponseDto.java`
- `backend/src/main/java/com/neueda/leap/dto/RefreshTokenListResponseDto.java`
- `backend/src/main/java/com/neueda/leap/dto/RefreshTokenRequestDto.java`
- `backend/src/main/java/com/neueda/leap/dto/RefreshTokenResponseDto.java`
- `backend/src/main/java/com/neueda/leap/dto/RoleListResponseDto.java`
- `backend/src/main/java/com/neueda/leap/dto/RoleResponseDto.java`
- `backend/src/main/java/com/neueda/leap/dto/TradeSuggestionListResponseDto.java`
- `backend/src/main/java/com/neueda/leap/dto/TradeSuggestionResponseDto.java`
- `backend/src/main/java/com/neueda/leap/dto/UpdateTradeSuggestionRequestDto.java`

### Exceptions
- `backend/src/main/java/com/neueda/leap/exception/RefreshTokenNotFoundException.java`
- `backend/src/main/java/com/neueda/leap/exception/RoleNotFoundException.java`
- `backend/src/main/java/com/neueda/leap/exception/TradeSuggestionNotFoundException.java`

### Mappers
- `backend/src/main/java/com/neueda/leap/mapper/RefreshTokenMapper.java`
- `backend/src/main/java/com/neueda/leap/mapper/RoleMapper.java`
- `backend/src/main/java/com/neueda/leap/mapper/TradeSuggestionMapper.java`

### Services
- `backend/src/main/java/com/neueda/leap/service/RefreshTokenService.java`
- `backend/src/main/java/com/neueda/leap/service/RoleService.java`
- `backend/src/main/java/com/neueda/leap/service/TradeSuggestionService.java`
- `backend/src/main/java/com/neueda/leap/service/impl/RefreshTokenServiceImpl.java`
- `backend/src/main/java/com/neueda/leap/service/impl/RoleServiceImpl.java`
- `backend/src/main/java/com/neueda/leap/service/impl/TradeSuggestionServiceImpl.java`

### Tests
- `backend/src/test/java/com/neueda/leap/controller/RefreshTokenControllerTest.java`
- `backend/src/test/java/com/neueda/leap/controller/RoleControllerTest.java`
- `backend/src/test/java/com/neueda/leap/controller/TradeSuggestionControllerTest.java`
- `backend/src/test/java/com/neueda/leap/mapper/RefreshTokenMapperTest.java`
- `backend/src/test/java/com/neueda/leap/mapper/RoleMapperTest.java`
- `backend/src/test/java/com/neueda/leap/mapper/TradeSuggestionMapperTest.java`
- `backend/src/test/java/com/neueda/leap/service/impl/RefreshTokenServiceImplTest.java`
- `backend/src/test/java/com/neueda/leap/service/impl/RoleServiceImplTest.java`
- `backend/src/test/java/com/neueda/leap/service/impl/TradeSuggestionServiceImplTest.java`

---

## Files updated
- `backend/src/main/java/com/neueda/leap/exception/GlobalExceptionHandler.java`
- `backend/src/test/resources/mapper/schema.sql`
- `backend/src/test/resources/mapper/revised_schema.sql`
- `backend/src/test/resources/mapper/revised_data.sql`
- `docs/api.yaml`

---

## Final outcome
The backend now has working, tested layers for:
- `RefreshToken`
- `Role`
- `TradeSuggestion`

The implementation follows the project’s current structure and conventions, the OpenAPI contract has been updated, and the full backend suite passes cleanly.


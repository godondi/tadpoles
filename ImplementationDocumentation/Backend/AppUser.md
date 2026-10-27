# AppUser Backend Implementation

## What was implemented

This change adds the backend slice for `AppUser` around the existing `/api/users` contract.

Implemented pieces:

- `AppUserController`
- `AppUserService` and `AppUserServiceImpl`
- `AppUserMapper`
- request and response DTOs
- `AppUserNotFoundException`
- controller, service, and mapper tests

The implementation covers:

- `GET /api/users/me`
- `GET /api/users`
- `GET /api/users/{userId}`
- `PATCH /api/users/{userId}`
- `PUT /api/users/{userId}/roles`

---

## TDD-first workflow

I added tests before the production classes:

1. `AppUserControllerTest`
2. `AppUserServiceImplTest`
3. `AppUserMapperTest`

The first red run failed on the missing controller, mapper, service, DTO, and exception classes. I then implemented the production layer until those tests passed.

---

## Why this follows the existing pattern

I reviewed the current `Client`, `Advisor`, `Instrument`, and `ModelPortfolio` implementations first.

The AppUser work follows the same shape:

- controller returns DTOs
- service owns validation and not-found rules
- mapper owns SQL
- response DTO maps from the domain object
- exceptions are translated centrally

The only meaningful difference is that AppUser also has role-assignment behavior, so the service enriches the user aggregate with role names loaded from `user_roles`.

---

## Main design choices

### 1. Keep AppUser as the aggregate boundary

I kept user profile updates and role replacement in the same service and mapper area instead of splitting roles into a second service.

That matches the API contract, where role assignment is part of user management rather than a separate resource.

### 2. Enrich the domain with role and linkage fields

`AppUser` now carries:

- `roles`
- `advisorId`
- `clientId`

This keeps the service-to-controller flow aligned with the rest of the codebase, where services return domain objects and response DTOs map from those domain objects.

### 3. Preserve the current schema reality

The current schema directly links users to advisors through `advisors.user_id`, but it does **not** currently have a direct client-user foreign key.

Because of that:

- `advisorId` can be resolved directly
- `clientId` remains nullable

That matches the existing OpenAPI schema, which already marks `clientId` as nullable.

---

## Validation and behavior

### Profile update validation

`AppUserServiceImpl` validates:

- user id must be positive
- username is required for lookup by username
- update request is required
- display name cannot be blank
- email cannot be blank
- update requires at least one field

### Role replacement validation

`setUserRoles(...)` validates:

- request is required
- at least one role must be supplied
- role names cannot be blank
- role names are normalized to uppercase
- unknown roles are rejected before any database writes

### Update behavior

User updates refresh `updated_at` in SQL using `CURRENT_TIMESTAMP`.

That was a deliberate small improvement because the `users` table already tracks update timestamps and this is the natural place to keep them correct.

---

## Mapper behavior

`AppUserMapper` provides:

- `getUser(...)`
- `getUserByUsername(...)`
- `listUsers()`
- `listUserRoles(...)`
- `listExistingRoleNames()`
- `updateUser(...)`
- `deleteUserRoles(...)`
- `insertUserRole(...)`

I kept role SQL inside `AppUserMapper` because roles are being managed as part of the user aggregate in this implementation.

---

## Refactors

I made two shared refactors while implementing AppUser because they improved consistency across the new admin/audit endpoints.

### 1. Added `SecurityRoleSupport`

This helper centralizes:

- missing-token checks
- role-claim evaluation
- role-required error creation

I also updated `ClientController` to use it instead of keeping a duplicated inline role helper.

### 2. Extended `GlobalExceptionHandler` for `ResponseStatusException`

That change makes authorization failures use the same `ApiErrorResponse` shape as the rest of the API error handling.

This was worth doing because AppUser and AuditLog endpoints both needed explicit auth-related errors.

---

## API documentation changes

I updated `docs/api.yaml` for the user endpoints so the contract now lists the broader error set used by the platform, including statuses such as:

- `200`
- `400`
- `401`
- `404`
- `409`
- `429`
- `500`

---

## Files added

- `backend/src/main/java/com/neueda/leap/controller/AppUserController.java`
- `backend/src/main/java/com/neueda/leap/dto/AppUserListResponseDto.java`
- `backend/src/main/java/com/neueda/leap/dto/AppUserResponseDto.java`
- `backend/src/main/java/com/neueda/leap/dto/SetAppUserRolesRequestDto.java`
- `backend/src/main/java/com/neueda/leap/dto/UpdateAppUserRequestDto.java`
- `backend/src/main/java/com/neueda/leap/exception/AppUserNotFoundException.java`
- `backend/src/main/java/com/neueda/leap/mapper/AppUserMapper.java`
- `backend/src/main/java/com/neueda/leap/service/AppUserService.java`
- `backend/src/main/java/com/neueda/leap/service/impl/AppUserServiceImpl.java`
- `backend/src/test/java/com/neueda/leap/controller/AppUserControllerTest.java`
- `backend/src/test/java/com/neueda/leap/mapper/AppUserMapperTest.java`
- `backend/src/test/java/com/neueda/leap/service/impl/AppUserServiceImplTest.java`

---

## Files updated

- `backend/src/main/java/com/neueda/leap/domain/AppUser.java`
- `backend/src/main/java/com/neueda/leap/controller/ClientController.java`
- `backend/src/main/java/com/neueda/leap/controller/SecurityRoleSupport.java`
- `backend/src/main/java/com/neueda/leap/exception/GlobalExceptionHandler.java`
- `docs/api.yaml`

---

## Verification

Validated with:

```cmd
cd /d C:\Users\Administrator\Desktop\tadpoles\backend
mvn -q test
```

# AuditLog Backend Implementation

## What was implemented

This change adds the backend slice for `AuditLog` and wires it to the existing `/api/audit-logs` contract.

Implemented pieces:

- `AuditLogController`
- `AuditLogService` and `AuditLogServiceImpl`
- `AuditLogMapper`
- response DTOs
- `AuditLogNotFoundException`
- controller, service, and mapper tests

The public API remains intentionally read-only:

- `GET /api/audit-logs`

---

## Why the AuditLog slice is read-only

That matches both the data model and the business meaning of audit history.

Audit log rows should describe system actions; they should not be edited through normal CRUD endpoints. Because of that, I implemented:

- list behavior for the public endpoint
- direct lookup support in the mapper/service for consistency and future extensibility

but I did **not** add create or update endpoints.

---

## TDD-first workflow

I added tests first:

1. `AuditLogControllerTest`
2. `AuditLogServiceImplTest`
3. `AuditLogMapperTest`

Those tests covered:

- authorized list access
- unauthorized access handling
- mapper ordering
- not-found behavior for direct lookup

---

## Pattern fit

I mirrored the same controller/service/mapper layering already used by the rest of the backend:

- controller delegates
- service validates ids and not-found cases
- mapper owns SQL
- DTOs shape the REST response

Even though the endpoint set is smaller than `Client` or `Instrument`, the structure is still the same.

---

## Behavior

### Auth handling

`AuditLogController` requires an authenticated JWT and checks for:

- `ADMIN`
- `AUDITOR`

That logic reuses the shared `SecurityRoleSupport` helper introduced during this implementation round.

### Query behavior

`AuditLogMapper` returns entries ordered by:

1. `created_at DESC`
2. `audit_log_id DESC`

That makes newest events surface first, which is the natural default for an audit feed.

### Direct lookup

`AuditLogServiceImpl#getAuditLog(...)` validates:

- audit log id must be positive
- missing rows raise `AuditLogNotFoundException`

This method is not exposed as a controller route yet, but it keeps the backend slice internally complete and consistent with the pattern used elsewhere.

---

## API documentation changes

I updated `docs/api.yaml` for `/audit-logs` so the endpoint now documents the broader operational error set, including:

- `200`
- `401`
- `429`
- `500`

---

## Shared refactors used here

This slice benefits from two shared changes made during the same implementation:

### 1. `SecurityRoleSupport`

This removed repeated JWT role-parsing logic and made the controller code smaller and more consistent.

### 2. `GlobalExceptionHandler` support for `ResponseStatusException`

This ensures audit authorization failures return the same structured `ApiErrorResponse` payload used by the rest of the API.

---

## Files added

- `backend/src/main/java/com/neueda/leap/controller/AuditLogController.java`
- `backend/src/main/java/com/neueda/leap/dto/AuditLogListResponseDto.java`
- `backend/src/main/java/com/neueda/leap/dto/AuditLogResponseDto.java`
- `backend/src/main/java/com/neueda/leap/exception/AuditLogNotFoundException.java`
- `backend/src/main/java/com/neueda/leap/mapper/AuditLogMapper.java`
- `backend/src/main/java/com/neueda/leap/service/AuditLogService.java`
- `backend/src/main/java/com/neueda/leap/service/impl/AuditLogServiceImpl.java`
- `backend/src/test/java/com/neueda/leap/controller/AuditLogControllerTest.java`
- `backend/src/test/java/com/neueda/leap/mapper/AuditLogMapperTest.java`
- `backend/src/test/java/com/neueda/leap/service/impl/AuditLogServiceImplTest.java`

---

## Files updated

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

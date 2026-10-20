# ClientSubscription Backend Implementation

## What was implemented

This change adds the backend slice for `ClientSubscription` using the existing nested-client API shape.

Implemented pieces:

- `ClientSubscriptionController`
- `ClientSubscriptionService` and `ClientSubscriptionServiceImpl`
- `ClientSubscriptionMapper`
- request and response DTOs
- `ClientSubscriptionNotFoundException`
- controller, service, and mapper tests

The implemented API is:

- `GET /api/clients/{clientId}/subscriptions`
- `POST /api/clients/{clientId}/subscriptions`

---

## TDD-first workflow

I added tests before implementing the production code:

1. `ClientSubscriptionControllerTest`
2. `ClientSubscriptionServiceImplTest`
3. `ClientSubscriptionMapperTest`

Those tests covered:

- listing a client’s subscriptions
- creating a subscription
- defaulting behavior on create
- validation failures
- not-found behavior for direct lookup

---

## Why this follows the current pattern

I treated subscriptions the same way the codebase already treats holdings and trades:

- nested under a client path
- validated in the service layer
- stored through a dedicated mapper
- returned through response DTOs

The service also includes `getClientSubscription(...)` even though there is no public `GET /subscriptions/{subscriptionId}` endpoint yet.

I added that on purpose because:

1. it matches the existing pattern used by holdings and trades
2. it lets create return the fully persisted row after insert

---

## Service behavior

`ClientSubscriptionServiceImpl` validates:

- client id must be positive
- subscription id must be positive
- request is required
- model portfolio id must be positive
- subscription date is required
- approved-by user id is optional, but if supplied it must be positive

### Reference validation

To stay consistent with the rest of the backend, the service reuses existing services instead of duplicating foreign-key checks:

- `ClientService`
- `ModelPortfolioService`
- `AppUserService`

That means the subscription layer inherits the same not-found and input validation behavior those services already define.

### Defaulting behavior

New subscriptions are created with:

- `status = ACTIVE`
- `endedDate = null`

That matches the schema default and the business meaning of a newly created subscription.

---

## Mapper behavior

`ClientSubscriptionMapper` provides:

- `listClientSubscriptions(...)`
- `getClientSubscription(...)`
- `insertClientSubscription(...)`

Rows are listed in:

1. `subscribed_date DESC`
2. `subscription_id DESC`

order so the newest subscription records appear first.

---

## API documentation changes

I updated `docs/api.yaml` for the subscription endpoints so they now document the broader response/error surface, including statuses such as:

- `200`
- `201`
- `400`
- `401`
- `404`
- `409`
- `429`
- `500`

---

## Test resource refactor

While wiring the new mapper tests, I added a dedicated revised mapper schema/data pair and also updated the shared mapper schema cleanup order.

### Why this refactor was needed

The new mapper tests introduced additional tables:

- `roles`
- `user_roles`
- `client_subscriptions`
- `audit_logs`

Without updating the drop order, later mapper tests could fail because H2 still had dependent foreign-key tables in place.

This was a test-only refactor to keep the full suite stable and isolated.

---

## Files added

- `backend/src/main/java/com/neueda/leap/controller/ClientSubscriptionController.java`
- `backend/src/main/java/com/neueda/leap/dto/ClientSubscriptionListResponseDto.java`
- `backend/src/main/java/com/neueda/leap/dto/ClientSubscriptionResponseDto.java`
- `backend/src/main/java/com/neueda/leap/dto/CreateClientSubscriptionRequestDto.java`
- `backend/src/main/java/com/neueda/leap/exception/ClientSubscriptionNotFoundException.java`
- `backend/src/main/java/com/neueda/leap/mapper/ClientSubscriptionMapper.java`
- `backend/src/main/java/com/neueda/leap/service/ClientSubscriptionService.java`
- `backend/src/main/java/com/neueda/leap/service/impl/ClientSubscriptionServiceImpl.java`
- `backend/src/test/java/com/neueda/leap/controller/ClientSubscriptionControllerTest.java`
- `backend/src/test/java/com/neueda/leap/mapper/ClientSubscriptionMapperTest.java`
- `backend/src/test/java/com/neueda/leap/service/impl/ClientSubscriptionServiceImplTest.java`
- `backend/src/test/resources/mapper/revised_schema.sql`
- `backend/src/test/resources/mapper/revised_data.sql`

---

## Files updated

- `backend/src/test/resources/mapper/schema.sql`
- `docs/api.yaml`

---

## Verification

Validated with:

```cmd
cd /d C:\Users\Administrator\Desktop\tadpoles\backend
mvn -q test
```

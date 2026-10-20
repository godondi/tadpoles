# Auth Refactor Procedure

## Goal
Implement a user signup flow where a user can:

1. create credentials with `email + password`
2. complete required non-automatic profile fields
3. create the linked client record
4. continue using the existing login/token flow with only small backend changes

This is feasible in the current project.

---

## Current State Assessment

### What already exists in the backend
The backend already has most of the core pieces needed for this:

- `POST /auth/login` in `backend/src/main/java/com/neueda/leap/controller/AuthController.java`
- anonymous client registration route `POST /auth/register/client` in the same controller
- password hashing via `BCryptPasswordEncoder` in:
  - `backend/src/main/java/com/neueda/leap/service/impl/AuthServiceImpl.java`
  - `backend/src/main/java/com/neueda/leap/service/impl/UserServiceImpl.java`
- JWT-based stateless auth in `backend/src/main/java/com/neueda/leap/config/SecurityConfig.java`
- `users`, `user_roles`, and `clients` tables already present in:
  - `database/schema/tadpoles_schema.sql`
- refresh token support already exists under:
  - `backend/src/main/java/com/neueda/leap/controller/RefreshTokenController.java`
  - `backend/src/main/java/com/neueda/leap/service/impl/RefreshTokenServiceImpl.java`

### Important conclusion
This means the project is **not starting from zero**.

The backend already supports:
- DB-backed login
- BCrypt password verification
- self-registration for clients
- automatic creation of a linked `client` row

What is missing is the richer onboarding flow for collecting the rest of the user/client attributes after initial signup.

---

## Answer to the question

### Is it possible?
Yes.

### Can it keep the current auth logic?
Yes.

The safest path is to **extend the current registration flow**, not replace it.

Keep:
- existing `POST /auth/login`
- existing JWT creation flow in `AuthServiceImpl`
- existing BCrypt password hashing
- existing `CLIENT` role assignment logic

Refactor only:
- registration DTOs
- onboarding persistence model
- self-service profile completion endpoints
- frontend signup/onboarding screens

---

## Recommended Design

## Preferred flow: staged onboarding

### Stage 1: credential creation
Public endpoint:
- `POST /auth/signup`

Request payload example:

```json
{
  "email": "jane@example.com",
  "password": "StrongPassword123!",
  "displayName": "Jane Doe"
}
```

Stage 1 responsibilities:
- validate email format
- validate password strength
- normalize email
- optionally derive username from email, or stop using separate username for clients
- hash password using BCrypt
- create row in `users`
- assign `CLIENT` role in `user_roles`
- mark onboarding incomplete

### Stage 2: onboarding/profile completion
Authenticated endpoint:
- `POST /api/client-onboarding/profile`
  or
- `PUT /api/users/me/onboarding`

Request payload example:

```json
{
  "clientName": "Jane Doe",
  "phone": "+1-555-222-1111",
  "dateOfBirth": "1992-03-15",
  "addressLine1": "100 Main Street",
  "addressLine2": "Unit 9",
  "city": "New York",
  "state": "NY",
  "postalCode": "10001",
  "country": "United States",
  "employmentStatus": "Employed",
  "netWorth": 250000,
  "riskTolerance": "Moderate",
  "investmentObjective": "Long-term growth",
  "preferredContactMethod": "Email",
  "paperlessStatements": true,
  "marketingOptIn": false
}
```

Stage 2 responsibilities:
- validate required profile fields
- create or update the richer client profile data
- create or finalize the `clients` row if not yet created
- optionally assign default `model_portfolio_id`
- optionally assign default advisor later through internal workflow
- mark onboarding complete

---

## Why staged onboarding is better than one giant signup request

### Benefits
- smaller public attack surface
- cleaner validation boundaries
- easier UX: user can create an account first, then complete onboarding
- avoids requiring all KYC/business fields before account creation succeeds
- better fit for future “save progress / continue later” behavior
- easier to test

### Why not keep everything in one request?
You could, but it becomes harder to:
- distinguish auth fields from domain fields
- reuse the endpoint later
- support partial onboarding
- maintain clean service boundaries

---

## Data Model Recommendation

## Existing schema today
Current schema is lean:
- `users` holds auth/core identity
- `clients` holds brokerage/client linkage

### Current mismatch
The frontend mock profile currently includes many fields like:
- phone
- address
- city/state/zip
- date of birth
- employment status
- net worth
- risk tolerance
- investment objective
- paperless statements
- marketing opt-in

Those fields do **not** currently exist in `database/schema/tadpoles_schema.sql`.

## Recommended schema approach

### Option A — recommended: add `client_profiles`
Add a new table such as:

```sql
CREATE TABLE client_profiles (
    client_profile_id           SERIAL PRIMARY KEY,
    user_id                     INTEGER NOT NULL UNIQUE REFERENCES users(user_id) ON DELETE CASCADE,
    client_id                   INTEGER UNIQUE REFERENCES clients(client_id) ON DELETE CASCADE,
    phone                       TEXT,
    date_of_birth               DATE,
    address_line_1              TEXT,
    address_line_2              TEXT,
    city                        TEXT,
    state                       TEXT,
    postal_code                 TEXT,
    country                     TEXT,
    employment_status           TEXT,
    net_worth                   NUMERIC(18,2),
    risk_tolerance              TEXT,
    investment_objective        TEXT,
    preferred_contact_method    TEXT,
    paperless_statements        BOOLEAN NOT NULL DEFAULT TRUE,
    marketing_opt_in            BOOLEAN NOT NULL DEFAULT FALSE,
    onboarding_complete         BOOLEAN NOT NULL DEFAULT FALSE,
    created_at                  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

### Why this is the best fit here
- keeps `users` focused on auth + identity
- keeps `clients` focused on brokerage/account linkage
- prevents the `clients` table from becoming a catch-all profile store
- easier to evolve into KYC/compliance workflows later

### Option B — possible but less clean
Put many of these fields directly on `clients` or `users`.

I do **not** recommend that for this project unless you intentionally want a flatter schema.

---

## Auth Logic Recommendation

## Keep the current login flow
The current login logic in `AuthServiceImpl` should remain the main sign-in path:
- look up user in DB
- check enabled flag
- compare plaintext password to BCrypt hash with `matches(...)`
- issue JWT

That logic is already correct conceptually.

## Password policy
Use BCrypt only for user passwords.

### Why
BCrypt already provides:
- hashing
- salting
- adaptive work factor

### Rule
- never store plaintext passwords
- never store reversible encrypted passwords
- do not replace password storage with plain SHA-256

### Current code status
Already compliant:
- `UserServiceImpl#createUserRecord(...)` uses `passwordEncoder.encode(password)`
- `AuthServiceImpl#login(...)` uses `passwordEncoder.matches(...)`

So the password requirement is already satisfied.

---

## Small Backend Refactor Plan

## Phase 1 — keep current behavior, rename/clarify the public signup path

### Goal
Make the public client signup flow explicit and easier to extend.

### Suggested change
Either:
- keep `POST /auth/register/client` and enhance it

or
- add `POST /auth/signup` as a clearer alias for client self-registration

### Recommendation
Prefer:
- `POST /auth/signup`

and keep:
- `POST /auth/register/client`

as a temporary compatibility alias if the route is already referenced elsewhere.

### Files to update
- `backend/src/main/java/com/neueda/leap/controller/AuthController.java`
- `backend/src/main/java/com/neueda/leap/config/SecurityConfig.java`
- `backend/src/test/java/com/neueda/leap/controller/AuthControllerTest.java`

---

## Phase 2 — separate auth registration DTO from profile onboarding DTO

### Current DTO
`ClientRegistrationRequestDto` currently contains:
- `username`
- `password`
- `email`
- `displayName`
- `clientName`

### Recommended split
Create:

#### `SignupRequestDto`
Fields:
- `email`
- `password`
- `displayName`

Optional:
- `username` only if you still want a separate login name

#### `CompleteClientOnboardingRequestDto`
Fields:
- `clientName`
- phone/address/dob/etc.
- business suitability / investment profile fields

### Why split them
- credential creation is not the same thing as full client onboarding
- easier frontend stepper/wizard implementation
- cleaner validation in services

### Files to add/update
- new DTOs under `backend/src/main/java/com/neueda/leap/dto/`
- update `UserService.java`
- update `UserServiceImpl.java`

---

## Phase 3 — introduce onboarding state

### Recommendation
Track whether the client finished onboarding.

Possible storage locations:
- on `users`
- on `client_profiles`

### Better choice
Store it on `client_profiles` as `onboarding_complete`

### Why
The flag belongs to client profile completion, not pure authentication.

---

## Phase 4 — self-service profile completion endpoint

### Why it is needed
Current user update endpoint:
- `PATCH /api/users/{userId}`
- is admin-only

That is not appropriate for a newly signed-up client filling their own profile.

### Recommended endpoint
Add one of:
- `PUT /api/users/me/onboarding`
- `POST /api/client-onboarding/profile`

### Behavior
- requires authenticated JWT
- resolves current user from token
- verifies user has `CLIENT` role
- creates/updates profile data
- links to `clients`
- marks onboarding complete

### Files to extend
- `backend/src/main/java/com/neueda/leap/controller/AppUserController.java`
  or create a dedicated onboarding controller
- `backend/src/main/java/com/neueda/leap/controller/SecurityRoleSupport.java`
- add new service interface/impl if onboarding deserves its own service

### Recommendation
A dedicated service is cleaner:
- `ClientOnboardingService`
- `ClientOnboardingServiceImpl`

---

## Phase 5 — extend persistence layer consistently

### Current registration path already uses
- `UserMapper`
- `ClientMapper`

### Recommended additions
Add:
- `ClientProfile` domain model
- `ClientProfileMapper`
- tests for mapper methods

### Files likely needed
- `backend/src/main/java/com/neueda/leap/domain/ClientProfile.java`
- `backend/src/main/java/com/neueda/leap/mapper/ClientProfileMapper.java`
- corresponding DTOs
- service classes

### Keep style consistent
This repository already uses:
- domain objects
- DTOs
- MyBatis mappers
- service interfaces + service implementations
- controller tests and service tests

Follow that same pattern.

---

## Phase 6 — optional improvement: automatic login immediately after signup

### Option
After `POST /auth/signup`, you can either:

#### Option 1: require separate login
Response:
- return created user metadata only

Pros:
- simplest
- minimal change

Cons:
- extra frontend step

#### Option 2: auto-issue tokens after signup
Response:
- access token
- optional refresh token
- created user/client metadata
- onboarding status

Pros:
- smoother UX
- allows immediate redirect into onboarding flow

Cons:
- slightly more backend wiring

### Recommended for this project
If you want the best UX, use Option 2.

This project already has:
- JWT issuance logic
- refresh token support

So after creating the user, the backend can issue tokens and redirect the frontend into onboarding.

### How to do it cleanly
Either:
- extend `AuthService` with a signup method
- or let `UserService` create the user and then call auth/token issuance logic

Better design:
- move signup orchestration into `AuthService`
- let `UserService` remain focused on persistence/business creation

---

## Detailed Backend Procedure

## Step 1 — add tests first
Follow TDD.

### Add/extend controller tests
Update:
- `backend/src/test/java/com/neueda/leap/controller/AuthControllerTest.java`

Add tests for:
- anonymous signup allowed
- duplicate email rejected with `409`
- weak password rejected with `400`
- onboarding endpoint requires auth
- onboarding endpoint stores profile data and returns success

### Add/extend service tests
Update:
- `backend/src/test/java/com/neueda/leap/service/impl/UserServiceImplTest.java`

Add tests for:
- signup creates BCrypt password hash
- signup assigns `CLIENT` role
- signup can create a user without full profile completion if staged flow is used
- onboarding completion creates/updates linked profile row
- client creation and profile completion roll back together on failure

### Add mapper tests
Add tests similar to existing mapper style:
- `AppUserMapperTest`
- `ClientMapperTest`

Create:
- `ClientProfileMapperTest`

---

## Step 2 — add/adjust schema

### Runtime schema
Update:
- `database/schema/tadpoles_schema.sql`

### Test schema
Update both test schemas if affected:
- `backend/src/test/resources/mapper/revised_schema.sql`
- `backend/src/test/resources/mapper/schema.sql`

### Test seed data
Update seed data files as needed:
- `database/schema/002_tadpoles_test_data.sql`
- `backend/src/test/resources/mapper/revised_data.sql`
- `backend/src/test/resources/mapper/data.sql`

### Why all of these matter
This project uses both runtime Postgres schema and H2 mapper test schemas.
If you change only production schema, tests will drift and fail later.

---

## Step 3 — create DTOs

### Suggested DTOs
Add:
- `SignupRequestDto`
- `SignupResponseDto`
- `CompleteClientOnboardingRequestDto`
- `ClientOnboardingResponseDto`

### Suggested response fields
For signup:
- `userId`
- `email`
- `displayName`
- `clientId` if already created
- `onboardingComplete`
- optional token payload if auto-login is chosen

For onboarding completion:
- `userId`
- `clientId`
- `clientName`
- `onboardingComplete`

---

## Step 4 — choose username strategy

### Current model
The current login request uses:
- `username`
- `password`

### Your desired UX
You want signup using:
- `email + password`

### Recommended approach
Use email as the login identifier for client self-registration.

Options:

#### Option A — keep `username` column and populate it from email
Example:
- username = normalized email

Pros:
- minimal schema change
- almost no impact to current login code

Cons:
- `username` becomes semantically redundant for clients

#### Option B — update login to accept email-or-username
Pros:
- flexible

Cons:
- slightly more service logic

### Best fit here
Option A is the safest minimal-change path.

Implementation note:
- when a client signs up with email, store the same normalized email into both `users.email` and `users.username`
- continue using current `findByUsername(...)` login path

Later, if desired, enhance login to support both username and email explicitly.

---

## Step 5 — implement signup orchestration

### Suggested service shape
Add method to `AuthService` such as:

```java
SignupResponseDto signup(SignupRequestDto request);
```

### Suggested implementation flow
In `AuthServiceImpl` or a dedicated signup service:
1. validate request
2. normalize email
3. validate password strength
4. ensure email/username uniqueness
5. BCrypt-hash password
6. create `users` row
7. assign `CLIENT` role
8. optionally create a placeholder `clients` row or defer until onboarding step
9. optionally issue access token / refresh token
10. return response

### Transactionality
Signup and related row creation must be wrapped in `@Transactional`.

---

## Step 6 — implement onboarding completion

### Suggested flow
1. resolve current user from JWT
2. verify current user is a client
3. upsert profile row
4. create `clients` row if absent
5. set `client_profiles.client_id`
6. mark `onboarding_complete = true`
7. return a response DTO

### Where ownership resolution already exists
See:
- `backend/src/main/java/com/neueda/leap/controller/SecurityRoleSupport.java`

That helper already resolves the current user using:
- `userId` claim if present
- otherwise JWT `subject`

### Optional JWT improvement
Add these claims to issued JWTs:
- `userId`
- `clientId`

This is optional, not required.
The current subject-based resolution already works.

---

## Frontend Procedure

## Step 1 — replace mock login with real backend auth
Current frontend auth is still local-only.

Eventually update frontend auth flow to call:
- `POST /auth/login`

### Request payload
For minimal backend change, if username is derived from email:

```json
{
  "username": "jane@example.com",
  "password": "StrongPassword123!"
}
```

If backend later supports email explicitly, rename accordingly.

---

## Step 2 — add signup page
Recommended route:
- `/signup`

Page fields:
- email
- password
- confirm password
- display name

Submit to:
- `POST /auth/signup`
  or current/extended `POST /auth/register/client`

---

## Step 3 — add onboarding route
Recommended route:
- `/onboarding`

After signup success:
- if backend returns tokens immediately, route to `/onboarding`
- if not, send user to `/login`, then to `/onboarding` after first login

Page fields should map to the real backend onboarding DTO, not the current mock-only profile interface.

---

## Step 4 — update frontend profile model to match backend
Current mock profile in:
- `frontend/src/app/user.service.ts`

Some fields are frontend-only placeholders today.

Before wiring real API data:
- define actual backend DTO shape
- then align Angular interfaces to that shape
- avoid keeping frontend-only fake profile attributes unless they are truly part of product scope

---

## Security / Validation Requirements

## Password handling
Required:
- BCrypt hashing
- strong password validation
- never log plaintext passwords
- never return password hashes in any DTO

## Input validation
Validate:
- email format
- minimum password length
- optional password complexity rules
- nonblank display name
- nonblank client name during onboarding
- date constraints for DOB if used
- numeric ranges for net worth if used

## Authorization
Keep:
- public signup endpoint
- protected onboarding endpoint
- admin-only user creation endpoint for back-office use

That means these flows can coexist:
- admin creates internal users via `POST /auth/register`
- public users self-register via `POST /auth/signup`

---

## Minimal-change implementation option
If you want the smallest possible refactor first:

### Do this now
1. keep `POST /auth/register/client`
2. change it to accept `email`, `password`, `displayName`
3. set `username = normalizedEmail`
4. create `users` row
5. assign `CLIENT`
6. create minimal `clients` row
7. add second authenticated endpoint to complete the rest of the profile

### Why this is the fastest path
- reuses current `UserServiceImpl.registerClient(...)`
- does not force a full auth rewrite
- preserves the working login/token path
- keeps bcrypt exactly as-is

---

## Better long-term implementation option
If you want the cleaner architecture:

1. move self-signup orchestration into `AuthService`
2. keep `UserService` focused on user/client persistence
3. add `ClientOnboardingService`
4. add `ClientProfile` table + mapper
5. optionally auto-issue tokens after signup

This is the best balance of maintainability and consistency.

---

## Files Most Likely To Change

### Backend controllers
- `backend/src/main/java/com/neueda/leap/controller/AuthController.java`
- `backend/src/main/java/com/neueda/leap/controller/AppUserController.java`
- optionally a new onboarding controller

### Backend services
- `backend/src/main/java/com/neueda/leap/service/AuthService.java`
- `backend/src/main/java/com/neueda/leap/service/UserService.java`
- `backend/src/main/java/com/neueda/leap/service/impl/AuthServiceImpl.java`
- `backend/src/main/java/com/neueda/leap/service/impl/UserServiceImpl.java`
- optionally new onboarding service files

### Backend DTOs
- existing auth and client registration DTOs
- new signup/onboarding DTOs

### Backend mappers / domain
- `backend/src/main/java/com/neueda/leap/mapper/UserMapper.java`
- `backend/src/main/java/com/neueda/leap/mapper/AppUserMapper.java`
- `backend/src/main/java/com/neueda/leap/mapper/ClientMapper.java`
- new `ClientProfileMapper.java`
- new `ClientProfile.java`

### Schemas / data
- `database/schema/tadpoles_schema.sql`
- `database/schema/002_tadpoles_test_data.sql`
- `backend/src/test/resources/mapper/revised_schema.sql`
- `backend/src/test/resources/mapper/revised_data.sql`
- `backend/src/test/resources/mapper/schema.sql`
- `backend/src/test/resources/mapper/data.sql`

### Tests
- `backend/src/test/java/com/neueda/leap/controller/AuthControllerTest.java`
- `backend/src/test/java/com/neueda/leap/service/impl/UserServiceImplTest.java`
- mapper tests for any new table

---

## Recommended Order of Work

1. Add tests for desired signup + onboarding behavior
2. Add schema changes for profile/onboarding storage
3. Add DTOs and domain models
4. Add mapper methods and mapper tests
5. Implement service layer with transactions
6. Implement controller endpoints
7. Wire frontend signup page
8. Wire frontend onboarding page
9. Replace frontend mock auth/profile calls with real API calls
10. run backend tests, then frontend tests, then end-to-end manual verification

---

## Final Recommendation

### Best answer for this codebase
Yes, implement it by **extending the existing anonymous client registration flow** and splitting registration from onboarding.

### Keep
- current JWT login flow
- current BCrypt password handling
- current admin registration route for internal users

### Add
- dedicated public signup endpoint or upgrade current client registration route
- authenticated onboarding completion endpoint
- profile storage for required non-automatic attributes
- frontend signup + onboarding screens

### Password requirement
Already compatible with your requirement:
- use BCrypt for all user passwords
- BCrypt is salted and hashed by design

---

## Suggested practical first implementation
If this were being implemented immediately, I would start with:

- backend:
  - keep `POST /auth/register/client`
  - add a new onboarding DTO + endpoint
  - add `client_profiles` table
  - return onboarding status in responses

- frontend:
  - create `/signup`
  - create `/onboarding`
  - after signup, route into onboarding
  - only after onboarding, treat the account as fully set up

That gives the feature you want with the least disruption to the current codebase.


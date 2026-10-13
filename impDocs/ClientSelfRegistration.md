# Client Self-Registration

## What changed

Added a public client onboarding flow that matches the existing controller/service/mapper split:

- `POST /auth/register/client` is now available without authentication
- the flow creates a `users` row, assigns the `CLIENT` role, and creates the linked `clients` row in one transaction
- the user-to-client relationship is now stored directly on `clients.user_id`

## Why it was implemented this way

The existing `/auth/register` path is an admin provisioning flow and already has role-aware behavior for internal users. I kept that path intact and added a dedicated public client-registration endpoint instead of overloading the admin API. That preserves the current pattern while making the business requirement explicit and safer.

The client link was implemented in the database model rather than inferred indirectly so return-visit flows can reliably identify the registered client account without reconstruction logic.

## TDD-first coverage

Tests were added before the production changes for:

- anonymous access to the new auth endpoint
- rejection of non-admin access to the existing admin registration endpoint
- transactional client registration behavior in `UserServiceImpl`
- duplicate-email conflict handling
- mapper loading of `clientId` for linked users

## Refactors

`UserServiceImpl` now shares the common validation and user-creation logic between admin registration and public client registration. That avoids duplicating password hashing, uniqueness checks, and timestamp setup while preserving the existing service style.

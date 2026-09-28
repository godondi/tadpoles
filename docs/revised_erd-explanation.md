# Revised Schema and ERD Differences

This document explains how the revised schema and ERD differ from the original database design.

## Why a revised version was needed

The original schema is a strong fit for the core investment model:
- advisors
- clients
- model portfolios
- instruments
- holdings
- trades

However, your stated goals also include a Spring Boot application with:
- JWT authentication
- Spring Security
- an admin dashboard
- role-based access
- auditability

Those needs require extra tables and relationships that are not present in the original schema.

---

## What stayed the same

The revised model keeps the original business domain intact:
- advisors still manage clients
- clients still align to model portfolios
- model portfolios still define target instrument allocations
- clients still hold instruments
- clients still place trades

The revised design is not a rewrite of the business model. It is an expansion of it.

---

## Main changes in the revised schema

### 1. Added `users`
The revised schema adds a real application user table.

Why:
- Spring Security needs a login identity
- JWT needs an authenticated principal
- admins, advisors, and operations users need accounts

Typical fields include:
- username
- email
- password hash
- enabled flag
- timestamps

This is the biggest missing piece in the original schema.

---

### 2. Added `roles` and `user_roles`
The revised schema supports role-based access control.

Why:
- an admin dashboard needs admin access
- advisors should not necessarily have the same privileges as admins
- some users may be read-only or operations-focused

This makes it possible to enforce access rules like:
- only admins can manage users
- only advisors can manage their own clients
- only approved users can execute certain operations

---

### 3. Linked advisors to users
The revised `advisors` table includes `user_id`.

Why:
- an advisor may also be a system user
- this lets the advisor log in directly
- the application can connect a login account to a business advisor record

This is helpful when the app needs both:
- business identity (`advisor`)
- authentication identity (`user`)

---

### 4. Added creator/approver tracking
The revised schema adds foreign keys like:
- `created_by_user_id`
- `approved_by_user_id`
- `submitted_by_user_id`

Why:
- the admin dashboard often needs to know who created or approved something
- auditing becomes easier
- operational workflows become traceable

This is especially useful for:
- model portfolios
- client subscriptions
- trade requests

---

### 5. Added `refresh_tokens`
The revised schema includes a refresh token table.

Why:
- JWT access tokens are usually short-lived
- refresh tokens let users stay signed in securely
- token revocation becomes possible

This is useful for any real authentication system.

---

### 6. Added `audit_logs`
The revised schema includes an audit log table.

Why:
- admin dashboards often require action history
- compliance and debugging become easier
- you can track who changed what and when

The audit log can store:
- actor user
- entity name
- entity id
- action type
- old values
- new values
- timestamp

---

### 7. Improved history handling for holdings and subscriptions
The revised schema changes some relationship tables so they can better support history.

#### `client_subscriptions`
The revised version uses a surrogate key and can track:
- active subscriptions
- paused subscriptions
- ended subscriptions
- who approved the subscription

#### `client_holdings`
The revised version uses a surrogate key and a uniqueness rule on:
- client
- instrument
- as-of date

The quantity fields involved in actual holdings and trades are now numeric rather than integer so fractional shares can be represented.

Why:
- the original schema only allowed one row per client/instrument pair
- that makes historical snapshots harder
- the revised model is better for reporting and dashboard history

---

### 8. Added `trade_suggestions`
The revised schema adds a separate table for advisor trade suggestions.

Why:
- only clients should place actual trades
- advisors need their own workflow to suggest trades to clients
- the suggestion should be tracked separately from an executed trade

This table makes it possible to store:
- which advisor made the suggestion
- which client received it
- which instrument was involved
- the suggested trade type and quantity
- whether the suggestion was viewed, accepted, rejected, or expired

---

## Admin dashboard impact

The revised schema is much better suited to an admin dashboard because it supports:
- login and access control
- admin and advisor roles
- audit trails
- approval workflows
- historical snapshots
- token-based sessions
- separate advisor suggestion workflows

That means the dashboard can safely expose features like:
- user management
- advisor/client assignment oversight
- portfolio creation and editing
- subscription approvals
- trade approval or review
- activity logs

---

## ERD differences

The revised ERD adds several new relationship groups:

### Security relationships
- `users` to `roles` through `user_roles`
- `users` to `refresh_tokens`
- `users` to `audit_logs`

### Admin workflow relationships
- `users` to `model_portfolios` through `created_by_user_id`
- `users` to `clients` through `created_by_user_id`
- `users` to `client_subscriptions` through `approved_by_user_id`
- `users` to `client_trades` through `submitted_by_user_id` and `approved_by_user_id`

### Existing business relationships retained
- advisors to clients
- model portfolios to clients
- model portfolios to holdings
- instruments to holdings
- clients to trades

### New trade workflow relationships
- advisors to trade suggestions
- clients to trade suggestions
- instruments to trade suggestions
- clients still place the actual trades

---

## Why the revised model is more realistic for Spring Boot

A Spring Boot application with JWT and Spring Security typically needs:
- a user entity
- role-based authorization
- a way to persist tokens or revoke them
- auditability for admin actions

The original schema handled the business side of the app well, but it did not yet support the platform side.

The revised schema fills that gap.

---

## Summary of the main improvements

Compared with the original schema, the revised version adds:
- application users
- roles and permissions structure
- advisor/user linkage
- admin-created and approved records
- refresh token storage
- audit logging
- better historical tracking for holdings and subscriptions
- a separate advisor trade-suggestion workflow
- fractional trade and holding quantities

In short:
- the original schema models the investment domain
- the revised schema models the investment domain **plus** the security and admin layers needed to run it as a real application


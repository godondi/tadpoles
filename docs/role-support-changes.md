# Role Support Changes for the Tadpoles Backend

This note explains what changes may be needed in the UML and schema to support the user levels in `README.md`:

- Admin
- Auditor
- Analyst
- Advisor
- Client

It complements `database/schema/revised_schema.sql` and `docs/uml.mmd`.

---

## What the roles need

| Role | What the backend must support | Schema / UML impact |
|---|---|---|
| Admin | Manage users, roles, system settings, and access to all data | Add user management routes, audit logs, and admin-focused services/controllers |
| Auditor | Read-only access to history and change tracking | Add audit-log entities/endpoints and keep write access off business tables |
| Analyst | Read data for external analytics and reporting | Add analytics/reporting endpoints that expose clean raw data |
| Advisor | Work with assigned clients and their portfolio data | Link advisor accounts to login users and restrict queries to assigned clients |
| Client | View only their own holdings, trades, and recommendations | Link client accounts to login users and scope dashboard responses to the authenticated user |

---

## Schema changes to consider

The revised schema already adds the most important security pieces:

- `users`
- `roles`
- `user_roles`
- `refresh_tokens`
- `audit_logs`
- user links on `advisors`, `clients`, and admin-created records

If the app grows, you may also want:

- `permissions` and `role_permissions` for finer-grained access control
- extra indexes on foreign keys used by dashboard queries
- more status fields for trade, subscription, and account lifecycle tracking
- history/snapshot tables if you want point-in-time reporting

---

## UML changes to consider

The current UML covers the business domain well, but role support would be clearer if it also showed:

### Security layer
- `User` / `AppUser`
- `Role`
- `UserRole`
- `RefreshToken`
- `AuthenticationService`
- `AuthorizationService` or equivalent security logic
- MyBatis `Mapper` classes instead of a repository layer

### Dashboard / reporting layer
- `AdminController`
- `AuditController`
- `AnalyticsController`
- `DashboardController`
- `AdminDashboardService`
- `AuditLogService`
- `AnalyticsService`
- `DashboardService`

### Important relationships
- `User` linked to `Advisor` or `Client` when the login belongs to a business user
- `User` linked to many `Role` entries through `UserRole`
- `AuditLog` linked back to `User`
- `RefreshToken` linked back to `User`
- reporting services reading holdings, trades, and portfolio data

---

## Trade workflow changes

The backend should treat these as two separate workflows:

- **Clients place actual trades** through the trade API
- **Advisors suggest trades** through a separate suggestion API

That means the schema and UML should show:

- `client_trades` for real executed or pending client trades
- `trade_suggestions` for advisor recommendations
- fractional quantities stored as numeric values so partial shares are supported

This separation keeps authorization clear and prevents advisor suggestions from being confused with executable client trades.

---

## API behavior for dashboards

The backend should return role-aware data, not frontend layout details.

A good pattern is:

- `GET /users/me` returns the authenticated user and roles
- `GET /dashboard/me` returns the data needed for that user’s dashboard
- role-specific endpoints return only allowed records
- analytics endpoints expose clean read-only data for the external analytics source

This keeps the frontend flexible while keeping authorization in the backend.

---

## Summary

The revised schema already covers most of the missing application layer for role-based access. The main follow-up work is to make the UML show the security layer and to expose role-aware API routes that let each user level see the correct dashboard data.



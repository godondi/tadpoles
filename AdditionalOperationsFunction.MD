# Additional Operations Function

## Design notes

The request adds two administrative workflows to the existing Trading Operations workspace. I reviewed the Operations workspace notes, app route and authorization policy, shared shell, Market search form, user registration service, trading admission path, user/client schema, and migration conventions before implementation.

The existing UI uses standalone Angular components, role-scoped routes, token-based color variables, square form controls, panels, and `table-wrapper`. Operations actions are exposed as separate role-guarded pages in the left workspace navigation. User Access adapts the Market search layout for username/name/email search and access-state filtering. Bulk controls stay above the client list, use aligned field rows, and remain disabled until a client is selected. Staff account creation lives at its own route and no longer shares the client-list page.

## Implementation

- `database/migrations/020_user_trade_access.sql` adds revocation timestamp, optional end time, and reason to the user record. `NULL` end time means indefinite; a finite end time expires naturally without a scheduled cleanup job.
- `TradingMapper.accountEnabled` treats an active indefinite or not-yet-expired timed revocation as unavailable. Existing trading admission and execution checks use this predicate, so a banner alone cannot permit a trade.
- `/api/operations/users` lists client identity, role, client identifier, balance, and access state in username order. Search binds an explicit empty string for the unfiltered request, and joins use explicit key predicates so the query works consistently in PostgreSQL. The prior empty-list failure was reproducible in the mapper fixture and the query coverage now checks actual client rows, balance, revoked state, and name/email search. Operations-only `PATCH /{clientId}/trade-access` supports indefinite revocation, hours/days/months, and restore. `POST /staff` delegates account creation to the existing registration service and restricts role selection to Analyst, Advisor, Compliance, and Finance.
- Client onboarding assigns through a serialized advisor pool. Only enabled, linked accounts with the ADVISOR role are eligible. Real staff advisors are considered before demo fixtures; within each group, the least-assigned advisor wins, with the lowest advisor ID breaking ties. This keeps newly created staff advisors eligible without assigning ordinary signups to `demo.advisor` when loads are tied.
- Trade-access changes append operator-attributed immutable events in the same transaction as the account update. The migration creates the append-only event table and the restricted runtime grant file grants only select/insert access.
- `/api/workspace/client/trade-access` exposes only the authenticated client's own revocation status. The shared client shell displays the requested support message and either the timed end date or indefinite status, refreshing every 30 seconds while the session is active.
- `frontend/src/app/features/operations/user-access.component.ts` provides checkboxes, multi-selection, filtering, manual/timed revocation, and restore. `create-account.component.ts` owns staff account validation and creation. The Operations sidebar links to both routes independently.
- Signup and onboarding now share the same token-backed surface/control sizing and spacing. Both forms opt out of browser-native submit blocking so their inline validation is consistently rendered; onboarding errors appear at the bottom of the form after the profile fields.

## UI scenarios to verify

1. Open the left-sidebar User Access page and verify there are no horizontal Create Account/User Access tabs; rows show checkbox, username, name, user ID, balance, and access state alphabetically. Revoke and restore actions are only available in the bulk control panel above the list.
2. Search by username, display name, and email; filter allowed/revoked; clear filters; exercise retry after a list request fails.
3. Revoke a client indefinitely and verify the client notice says access is revoked and indefinite.
4. Revoke a client for hours, days, and months; verify the client notice shows the stored end date; verify access returns after that timestamp.
5. Select multiple clients, apply timed or indefinite revoke, and restore selected clients.
6. Open the separate left-sidebar Create Account page; create each allowed staff role with valid inputs; verify missing fields, weak passwords, invalid email, duplicate username/email, and unauthorized role values are rejected by validation/service/API.
7. Verify a revoked client's order preview/admission is unavailable and that a restored or expired account can proceed through normal admission controls.
8. Inspect signup/onboarding and both Operations pages in light and dark modes at desktop and narrow widths. Check table horizontal scrolling, readable status, aligned filters, consistent control padding, and onboarding validation at the bottom of the form.
9. Include a registered client with no completed onboarding. Verify their row displays Onboarding, selection is disabled, and a missing-ID response renders a clear onboarding message without a Retry button or raw parser exception.

## Verification record

- Full frontend suite: 386 tests across 59 files passed, including user-list and retry cases, every revoke duration unit, multi-select and restore, each supported staff role, account validation, signup validation, onboarding validation placement, and the missing-onboarding-ID error states.
- Production frontend build: passed. Angular reported the existing Analytics component is 495 bytes over its configured component-style budget.
- Shared style check: passed (square corners, circular spinner, no gradients).
- Focused backend suite: the Operations mapper and controller tests pass. Coverage includes regular and pre-onboarding client rows, balances, username/name/email search, indefinite denial, timed expiry, restoration, duration limits, and all four permitted account roles. The complete backend suite was attempted; Testcontainers-dependent tests could not initialize Docker from the Maven test JVM in this environment.
- Docker Compose build and fresh startup: all application images built. The full stack initialized from a new, isolated database volume, applied the migrations and demo seeds, and reached the Operations workspace. The active developer stack uses hard-coded Analytics container names and ports, so the test run supplied alternate names and host ports without stopping or modifying that stack.
- Browser UI QA on the retained local stack: the Client Accounts table loaded all three available rows (no “Unable to load” error), rendered names and balances, and sorted usernames. The action column is gone; the revoke/restore controls sit above the list. Selecting a row enables the bulk actions, and Timed reveals aligned Amount, Unit, and Reason fields. No revocation was submitted during this final layout check.
- Browser account/onboarding QA: the separate Create Account sidebar page showed no horizontal tabs. Missing fields surfaced an error; creating a disposable Analyst account succeeded and reset the form. Signup rendered consistently in dark mode and showed its missing-field error. A disposable signup reached onboarding, where an empty submit displayed required-field feedback at the bottom of the form. The Operations pages were visually checked in light and dark themes.
- Disposable test data and containers are isolated from the existing `tadpoles` stack. The seeded client access changes were restored before teardown.

## User Access issue follow-up

The screenshot review identified two issues: row-level actions competed with multi-select actions, and an incomplete-onboarding ID parse error was surfaced verbatim with a Retry button. User Access now relies on the bulk controls above the rows. If the server returns the known missing-ID parser error, both list and access-change error paths show a concise onboarding explanation and suppress Retry. The mapper test adds a registered CLIENT with no `clients` row and verifies the server still returns it with a null client ID and zero balance; the UI test verifies the onboarding row is not selectable. Retry remains available for transient list-load failures.

README now documents current roles, prerequisites, Compose startup, individual service development, and test commands for frontend, backend, PostgreSQL migrations, and analytics. The final Docker Compose images built and were applied to the existing local stack; database volumes were preserved and all services remained running.

## Follow-up considerations

Bulk changes currently issue one PATCH per selected client. For large selections, add an atomic bounded bulk endpoint. Confirm that deployment migration orchestration applies migration 020 and reapplies `database/runtime-grants.sql` before enabling the endpoints in a restricted runtime.

## Follow-up presentation refactor

The screenshot review exposed two spacing problems: Advisor's generic `.empty` rule removed the empty panel's horizontal inset, and onboarding's risk-tolerance helper added an uneven row height between the two-column fields. The Advisor empty state now restores panel padding, while the redundant helper and its dangling accessibility reference are removed. The table keeps a concise screen-reader-only caption for accessibility.

Removed the requested explanatory captions from Compliance, Operations, Analyst and Client support screens while preserving labels and field-specific validation guidance. `DisplayCodePipe` formats uppercase underscore-delimited API codes as sentence-case labels without changing the underlying option value or non-enum text. Shared workspace navigation and action states now use theme tokens for hover/selected contrast; signup and onboarding use matching input, border, hover, and disabled treatments.

Focused visual regression coverage was added for onboarding helper removal, Advisor's empty state and compact accessible table caption, and enum display formatting. The full frontend suite and shared style check are rerun after this refactor; local browser preview is used for signup and screenshot-level spacing review where authentication is not required.

## Client account copy and numeric entry refactor

Removed the specified repeated explanatory copy from the client account, display-currency and banking pages. The empty portfolio state is now a plain, non-bold “No positions” line. Kept the actual action labels, values, and accessible table captions so the data remains understandable and assistive technology retains table context.

`twoDecimals` now groups thousands with commas while preserving the existing exact decimal rounding and large-value handling. Banking movement entry filters to digits and one decimal point, groups the whole-number portion while typing, and adds two decimal places on blur. Routing and account references accept digits only while retaining leading zeroes; movement requests strip display commas before sending to the API.

Regression coverage checks copy removal, plain empty-state styling, numeric-only fields, formatted movement input, and grouped display values. The client account UI requires an authenticated session; component tests exercise account and banking states without creating disposable accounts or transmitting test credentials.

# Section 1 Frontend + Client/Backend Notes

## Summary
This pass focused on frontend structure, routing/auth behavior, header cleanup, dropdown reliability, and typography consistency.

No backend changes were required for Section 1 because the current frontend is still using mock/local state rather than real HTTP auth or profile endpoints.

---

## What was wrong before

### 1. Frontend structure was not modular enough
The frontend was working, but the structure was harder to maintain than it needed to be:

- `App` owned header UI, search UI, profile dropdown state, and navigation behavior.
- `ProfileComponent` and `TradesComponent` each implemented their own separate navbar.
- Route protection did not exist.
- Login only simulated navigation and did not maintain an authenticated session state.
- Shared styling tokens were effectively defined inside component-scoped CSS, which can lead to inconsistent rendering.

### Why that feels messy coming from React
If you are used to React, the original shape likely felt off because layout responsibilities were mixed into feature pages instead of being centralized.

React developers usually expect something closer to:

- a shared authenticated layout/shell
- feature pages focused on page content only
- auth/session logic in a dedicated service/store
- routing rules in one place
- reusable shared UI like headers/nav in their own component

That is the direction this refactor took.

---

## Structural refactor completed

### New frontend structure introduced
I added a more modular Angular structure without over-rewriting the project:

- `frontend/src/app/core/auth/auth.service.ts`
- `frontend/src/app/core/auth/auth.guard.ts`
- `frontend/src/app/layout/authenticated-shell.component.ts`
- `frontend/src/app/shared/top-nav/top-nav.component.ts`
- `frontend/src/app/shared/top-nav/top-nav.component.html`
- `frontend/src/app/shared/top-nav/top-nav.component.css`

### Why these pieces were added

#### `AuthService`
Purpose:
- single source of truth for logged-in state
- persistent browser session via `localStorage`
- keeps login/logout behavior out of page components

Why:
- auth should not live inside `LoginComponent` only
- guards and shell routing need one shared auth state source

#### `authGuard` + `guestOnlyGuard`
Purpose:
- protect authenticated routes
- prevent logged-in users from revisiting `/login`

Why:
- routing rules belong in routing/auth infrastructure, not scattered inside page logic

#### `AuthenticatedShellComponent`
Purpose:
- wraps all authenticated pages with one shared header/navigation shell

Why:
- removes duplicated navbars from profile/trades pages
- makes layout changes happen in one place
- closer to a React-style “app shell + routed pages” mental model

#### `TopNavComponent`
Purpose:
- own the shared header UI only
- keep account menu state local to the header
- host the search stub and account menu actions

Why:
- this was previously mixed into `App`
- navbar behavior is reusable layout UI, not root-application state

---

## Routing and authentication behavior

### Does the project use URL routing?
Yes, it already used Angular routing.

### What changed
Routes were refactored so authenticated pages now sit under a shared shell.

### Current behavior after refactor
- `http://localhost:4200/` -> account dashboard
- if not authenticated -> redirect to `http://localhost:4200/login`
- `http://localhost:4200/profile` -> protected
- `http://localhost:4200/trades` -> protected
- visiting `/login` while already authenticated -> redirect to `/`

### Why this was done
This matches the behavior you requested and makes auth concerns explicit and testable.

---

## Header and navigation changes

### Completed changes
- Removed emoji-based branding/icons from the shared header
- Moved brand text to the far left as plain text: `Tadpoles`
- Removed click behavior from the logo/brand text
- Removed the old header navigation buttons like the top-level trades/dashboard-style navigation
- Moved the search bar to the right side next to the account button
- Kept the search bar as a stub only
- Reworked the account menu dropdown

### Search bar behavior
The search UI is intentionally a stub right now:
- it accepts text
- it renders properly
- it does not execute real search functionality

Reason:
- you explicitly asked for no functionality yet
- this prevents fake or misleading UX behavior

---

## Account dropdown fixes

### Issues addressed
The old dropdown used brittle markup and broken `href="#"` items.

### Fixes implemented
- replaced placeholder anchor links with real button actions
- added explicit menu toggle state inside the shared nav component
- closes when clicking outside
- closes on `Escape`
- added proper `aria-expanded`, `aria-haspopup`, and menu semantics
- includes working actions for:
  - `Profile`
  - `Trade History`
  - `Log Out`

### Why this is better
This avoids dead links, improves accessibility, and makes the dropdown predictable.

---

## Styling and font fixes

### Root cause of the font issue
The project looked like it was falling back to Times New Roman / Courier partly because typography/theme tokens were not truly centralized in global styles.

I moved shared font and design tokens into:
- `frontend/src/styles.css`

### Typography updates
Global font stack now uses:
- `Arial, 'Helvetica Neue', Helvetica, sans-serif`

For value-heavy content, I applied a monospace stack via shared mono styling:
- `Consolas, 'SFMono-Regular', Menlo, Monaco, monospace`

### Where mono styling is used
Applied to value-oriented content such as:
- dashboard stats
- holdings numeric columns
- trade amounts/quantities/dates
- account-like values in profile
- signed-in email in the account menu

### Header/CSS improvements
- solid non-transparent top header styling
- stronger contrast for header controls
- better button visibility
- improved rounded surfaces/cards
- clearer modal close button text
- responsive top-nav layout

### Why this was done
- better readability
- more intentional visual hierarchy
- more professional finance-style number presentation
- avoids default browser-looking typography

---

## Page-level changes

### Dashboard
- shared nav removed from page ownership
- added a page header for dashboard context
- moved `Place Order` action into dashboard content instead of the top nav
- preserved modal behavior

### Profile
- removed duplicated page navbar
- left page focused on profile content only
- removed emoji dependence by using text initials from mock data

### Trades
- removed duplicated page navbar
- retained filtering/table functionality
- improved numeric typography

### Login
- updated branding text to `Tadpoles`
- removed emoji branding
- login now writes authenticated state through `AuthService`
- supports redirecting back to a protected page after login when applicable

---

## Tests added first (TDD direction)
I followed the TDD intent by creating/rewriting tests around the new behavior before finalizing implementation.

### Added/updated tests
- `frontend/src/app/app.spec.ts`
  - redirects unauthenticated visitors from `/` to `/login`
  - renders the authenticated shell for signed-in users
  - redirects authenticated visitors away from `/login`

- `frontend/src/app/core/auth/auth.service.spec.ts`
  - unauthenticated default state
  - login persistence behavior
  - logout cleanup behavior

- `frontend/src/app/shared/top-nav/top-nav.component.spec.ts`
  - shared header renders brand/search/account correctly
  - account menu opens and closes correctly

### Why these tests matter
They lock in the user-visible behavior from this section:
- guarded routing
- shared shell rendering
- auth session persistence
- dropdown interaction

---

## Validation performed

### Unit tests
Executed:

```powershell
Set-Location "C:\Users\Administrator\Documents\tadpoles\frontend"
npm test -- --watch=false
```

Result:
- 3 test files passed
- 8 tests passed

### Production build
Executed:

```powershell
Set-Location "C:\Users\Administrator\Documents\tadpoles\frontend"
npm run build
```

Result:
- build completed successfully
- lazy-loaded route bundles were generated for dashboard, login, profile, and trades

---

## Backend note
No backend endpoint changes were needed for this section.

Reason:
- the frontend is currently backed by mock services (`UserService`, `TradesService`)
- no real auth endpoint was being called before this change
- route protection here is frontend-side session guarding only

If a real backend login/session endpoint is introduced later, the correct next step would be:
1. keep `AuthService` as the orchestration layer
2. replace local-only login with `HttpClient` calls
3. preserve the same guard/shell structure

---

## Why this refactor is better
This refactor makes the Angular app feel more organized in a way that should also feel more familiar from a React architecture perspective:

- shared layout is centralized
- page components are slimmer and more focused
- auth is explicit instead of simulated inside one page
- routing behavior is deterministic
- top-nav is reusable and isolated
- typography and theme tokens are actually global
- tests now cover the most important UX behavior for this section

---

## Follow-up recommendation
A good next step for Section 2 would be to continue this organization into clearer feature folders, for example:

- `features/dashboard/`
- `features/profile/`
- `features/trades/`
- `core/auth/`
- `shared/top-nav/`

I did not do a full file relocation of the existing feature pages yet because the goal here was to improve structure without causing a large-risk rewrite.


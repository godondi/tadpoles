# Instrument candles and quote frontend

Implemented and verified on October 8, 2026.

## Project assessment

Reviewed the implementation notes under `impDocs`, the deployment and architecture documents under `docs`, and the market-data paths, parameters, response schemas, and error schema in `docs/api.yaml`. Traversed the frontend, backend, database, testing, auth-stub, and nginx directories.

The current application uses standalone Angular components, signals, lazy routes, and a shared authenticated shell. The Spring backend uses controllers, services, DTOs, and MyBatis for persisted resources. The existing Fauxnance candle and quote services proxy external market data without persistence. Some older architecture documents describe earlier/future designs; the current source and published market-data contract informed this implementation.

## User-facing behavior

- `/trade/AAPL` loads a daily OHLC candlestick chart on the left and a quote card on the right. Lowercase tickers are normalized to uppercase for requests and display.
- The chart shows the latest 30 returned candles in chronological order. A 90-calendar-day request supplies extra sessions around weekends and holidays. The frontend sends `interval=1d`, inclusive `from`, and inclusive `to` as UTC dates. It sorts a copy of the response and takes the final 30 rows rather than interpreting 30 calendar days as 30 trading sessions.
- If fewer than 30 candles exist in the requested period, the page states the available count. Empty history has an explicit empty state; it does not invent candles. A long suspension or recent listing can legitimately provide fewer rows within this window.
- Each candle draws the high/low wick and open/close body. Up days are filled green and down days are outlined red. Flat prices and doji bodies remain visible. Price labels, endpoint dates, currency, and an expandable OHLC/volume table support interpretation and accessibility.
- The quote card displays price, absolute/percentage change, bid, ask, spread in basis points, previous close, currency, market state, UTC timestamp, source, and spread source. Nullable values display a dash or “Not specified”; missing currency is not silently labelled USD.
- Refresh requests only a new quote. The button is disabled during the request, preventing duplicate refreshes. There is no automatic refresh or polling.
- Quote refresh failure preserves the prior quote and its timestamp, displays the error, and allows retry. Candle failure has a separate retry control, allowing the quote to remain usable.
- A 404 from either endpoint replaces the page content with a warning triangle containing an exclamation mark and the API error message below it. If the API omits a message, a ticker-specific fallback is shown. Other failures display the API message when supplied, with network/session fallback messages otherwise.
- The existing Tadpoles/search/account header is reused. The layout stacks the quote below the chart on smaller screens.

## Files and implementation decisions

### API adapter

Added `frontend/src/app/core/api/market-data.service.ts` and its spec.

The typed service preserves the `data`/`meta` envelopes from `docs/api.yaml` and uses the existing `buildApiUrl` backend URL helper. It calls `GET /candles/{symbol}` and `GET /quotes/{symbol}`, encodes symbols as path segments, and owns date-range construction. Separating requests from presentation makes HTTP behavior independently testable. The Fauxnance API key stays on the backend; no upstream credentials are sent to the browser.

### Trade page and chart

Added these files under `frontend/src/app/features/trade/`:

- `trade.component.ts`, `.html`, `.css`, and `.spec.ts`
- `candle-chart.component.ts` and `.spec.ts`

The page coordinates two independent loading/error states and manual quote refresh. Route changes clear old data and cancel both pending requests. Destruction unsubscribes as well, preventing late responses from affecting a different instrument or destroyed page.

The chart uses native SVG. This provides actual OHLC candles, accessible labels, and testable geometry without adding a chart plugin or dependency. The existing Chart.js dependency does not provide a native financial candlestick series. A collapsible data table provides access to individual values without relying solely on hover or color. The scale includes padding and handles a zero price range without division by zero.

The layout uses existing global color/font tokens and a spacious two-column market overview inspired by the requested brokerage view. Feature-specific styles remain scoped to the page and chart.

### Shared loading component

Added `frontend/src/app/shared/loading/loading.component.ts`.

`<app-loading label="Loading daily candles…" />` is reusable anywhere. It supplies a CSS spinner, configurable text, a polite status announcement, and a reduced-motion alternative. It is used separately for candles and quotes so one completed request does not hide the other request's loading state.

### Routes and authentication wiring

Updated `frontend/src/app/app.routes.ts` to lazy-load `trade/:symbol` under `AuthenticatedShellComponent`. Existing authentication/onboarding guards therefore apply. Guests are redirected to login with the instrument URL preserved in `redirectTo`. Updated `app.spec.ts` to cover protected deep links and header reuse.

Updated `frontend/src/app/app.routes.server.ts` to use `RenderMode.Client` for `trade/:symbol`. Arbitrary tickers cannot be enumerated during prerendering, and the existing session is browser-local. This avoids build-time requests for protected market data and supports direct instrument URLs while preserving existing static-route behavior.

Updated `frontend/src/app/app.config.ts` to enable `withInterceptorsFromDi()`. The application already registered `AuthInterceptor` through `HTTP_INTERCEPTORS`, but `provideHttpClient(withFetch())` did not enable those class interceptors. Enabling them is necessary because both market-data endpoints require authentication. The HTTP service tests use the actual application provider configuration and assert the bearer header on both requests.

## TDD and automated verification

1. Added trade-page tests before creating the implementation.
2. Ran the Angular test runner. The initial red run failed with `TS2307` / unresolved `./trade.component`, because the component did not yet exist.
3. Implemented the service, chart, page, loading component, route, and HTTP wiring. The initial green run passed all 17 tests, including existing regression tests.
4. Expanded coverage for guarded deep links, shared header, application-level authentication headers, exact UTC range construction, encoded symbols, chart geometry, flat prices, limited history, nullable values, and 404 responses from either endpoint.

Final automated suite: **6 test files, 25 tests passed**.

Trade-page tests verify independent loading, 30 chronological candles from an unsorted larger response, quote fields, duplicate-refresh prevention, quote replacement, specified error text/warning icon, retry after failures, cancellation on ticker navigation, empty/limited history, partial metadata, and nullable quote fields. Chart tests verify finite flat-price geometry and wick/body ordering as well as accessible data. Existing auth/header/routing regression tests also pass.

Standard local commands:

```bash
cd frontend
npm test -- --watch=false
npm run build
```

This environment did not expose npm on PATH. Dependencies were installed with the bundled pnpm runtime without writing a new lockfile or changing package declarations. The checks were run with the bundled Node runtime and the equivalent Angular CLI commands:

```bash
node node_modules/@angular/cli/bin/ng.js test --watch=false
node node_modules/@angular/cli/bin/ng.js build
```

The production build passed, generated the lazy trade-page bundle, and prerendered the six existing static routes. The trade route is rendered on the client. Angular prerendering required an approved execution outside the filesystem/network sandbox because its temporary local server could not bind inside the sandbox.

## Browser and live API verification

Ran Chromium checks against the Angular development server with controlled API responses at 1440px desktop and 390px mobile widths. Verified:

- 30 chart candles and a single shared header;
- chart on the left and quote on the right at desktop width;
- stacked panels and no horizontal page overflow on mobile;
- bearer tokens on both requests;
- quote-only refresh without reloading candles;
- account dropdown opening and closing on Escape;
- the invalid-ticker warning and exact supplied error text;
- no browser page errors.

Inspected desktop, mobile, and invalid-ticker screenshots visually. One browser-script assertion initially checked dropdown removal before Angular finished rendering; the script was corrected to wait for the menu to disappear, and the checks then passed. No dropdown production change was needed.

Also made read-only requests to the configured Fauxnance upstream using the existing environment configuration without printing credentials:

- AAPL candles: HTTP 200, 65 daily candles returned for the requested 90-day range;
- AAPL quote: HTTP 200, fields matched the documented quote envelope;
- unknown ticker quote: HTTP 404.

Repeated browser rendering checks using the captured real AAPL response envelopes in place of fixture data. The page rendered the most recent 30 real candles and the real quote successfully at desktop and mobile widths. The refresh and error checks remained controlled for deterministic assertions.

The local Spring backend was not listening on port 8082 during verification. Consequently, the full authenticated browser → Spring → Fauxnance chain was not exercised live. HTTP unit tests cover the frontend's backend URL/parameters/authentication, live calls verify upstream availability/shape, and browser checks cover rendering and interactions. Backend/database source and schemas were unchanged, so backend/database suites were not rerun for this frontend feature.

## Docker build-context fix

The subsequent container build failed at `COPY . .` with `cannot replace ... /app/node_modules/@angular/build with file`. The frontend had no `.dockerignore`, so Docker sent the host dependency tree (the reported context was approximately 308 MB) and copied it over the container's own `npm ci` installation. The local pnpm installation represents packages such as `@angular/build` with symlinks, while npm installed actual directories in the image. That mismatch caused the copy failure before Angular compilation began.

Added `frontend/.dockerignore` to exclude `node_modules` at any depth, `dist`, Angular caches, TypeScript output, coverage, temporary files, environment files, editor settings, Git metadata, and logs. The Dockerfile still copies `package.json` and `package-lock.json` first and runs `npm ci`; subsequent source copying now preserves those container-installed dependencies. This also reduces the build context and prevents host platform-specific dependencies from entering the image. The existing `.gitignore` cannot solve this because Docker uses its own ignore file for build contexts.

Rebuild from the repository root with `docker compose build frontend`, then start the updated image with `docker compose up -d frontend`. A cache purge or deleting local dependencies is not required.

Validation: `git diff --check` passed. Attempted `docker compose build frontend`, including approved execution outside the sandbox, but this host denied access to `/var/run/docker.sock` in both cases. The corrected container build therefore still needs to be run on a host with Docker daemon access. The earlier Angular production build and 25 passing tests remain the application-level verification; this follow-up changes only the Docker context exclusions.

After Docker permissions were updated, the user was listed in the Docker group but the existing process still had its earlier supplementary groups. Retried through `newgrp docker` to activate the assigned membership. **The frontend Docker build passed**: the context was approximately 498 kB instead of 308 MB, `COPY . .` succeeded, the Angular production build prerendered six static routes, and `tadpole-frontend:latest` was exported successfully. This verifies the `.dockerignore` fix with the Dockerfile's own npm-installed dependencies. Compose also emitted a separate unset `PORT` warning; it did not prevent this frontend build.

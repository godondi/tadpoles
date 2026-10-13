# Fauxnance test-run quality-of-life updates

## What changed and why

### Ticker search

The shared top navigation now submits ticker searches to the existing `/trade/:symbol` route. Clicking **Search** or pressing **Enter** uses the same form submission handler.

`TopNavComponent.search()` prevents a browser page reload, trims surrounding whitespace, converts the ticker to uppercase, ignores empty input, closes the account menu, and calls `Router.navigate(['/trade', ticker])`. For example, ` aapl ` opens `/trade/AAPL`; `brk.b` opens `/trade/BRK.B`. Angular handles URL encoding by receiving the ticker as a route segment. Unsupported tickers continue to use the trade page's existing API error view.

This reuses the application router and trade page without adding another route or market-data request in the navigation component. The input's accessible label and placeholder now describe ticker search.

### Clickable Tadpoles brand

The shared header's Tadpoles brand is a native button with the accessible name **Tadpoles dashboard**, making it usable with a mouse, keyboard, and assistive technology. Its styling keeps the existing wordmark presentation.

`openDashboard()` navigates to `/`, the existing dashboard route. It checks the current URL's path before navigating, ignoring query strings and fragments. Clicking the brand on `/`, `/?view=holdings`, or `/#holdings` has no effect, so it does not reset dashboard state or close an open menu.

### Square corners and solid colors throughout the site

All explicit `border-radius` declarations in frontend source styles use `0` except the shared loading spinner, including the dashboard, trade page, top navigation, account menu/avatar, modals, login, signup, onboarding, profile, and trade history. The loading spinner uses `50%` as requested in the follow-up. The global element and pseudo-element rule also defaults corners to square.

Every CSS gradient was replaced at its source with a solid color. Authentication backgrounds and primary action buttons use dark green (`#006044`); pale page backgrounds keep their existing light palette. The dashboard header uses `var(--dark-green)`. Updating component styles directly avoids relying on `!important` overrides against Angular's scoped styles. Existing layout, focus indicators, and responsive breakpoints are retained; dropdown controls share the onboarding input styling.

`frontend/scripts/check-qof-styles.mjs`, exposed as `npm run test:qof-styles`, recursively checks frontend source files for CSS gradients and nonzero corner radii. It requires a circular shared loading spinner and permits `50%` only inside that component's `.spinner` rule; all other corner radii must remain zero. It supplies a repeatable regression check for this site-wide requirement.

### Onboarding dropdowns

The signup flow still leads to the existing onboarding component. Five free-form inputs there are now required native `<select>` controls with an initially empty, disabled prompt and exactly these choices:

| Field | Choices |
| --- | --- |
| Preferred contact method | Email; Phone |
| Employment status | Unemployed; Intern; Student; Employed (Part-Time); Employed (Full-Time); Retired |
| Investment objective | Preservation; Income; Growth; Speculation |
| Risk tolerance | Conservative; Moderate; Aggressive |
| Net worth | $0 - $24,999; $25,000 - $49,999; $50,000 - $99,999; $100,000 - $249,999; $250,000 - $499,999; $500,000 - $999,999; $1,000,000+ |

The component owns the option lists and renders them using Angular `@for`. Change events update the existing form signal. Browser required-field validation prevents leaving the prompts selected, and `submit()` also verifies membership in the four text option lists before calling the service. Net-worth validation rejects missing, negative, and nonfinite amounts. Existing profile loading, API error handling, onboarding status updates, and dashboard navigation remain in place.

## Net-worth contract and design choice

The existing frontend request and profile types use `number | null`; the backend `CompleteClientOnboardingRequestDto` and profile persistence use numeric `BigDecimal` values. The range dropdown retains that numeric contract without a backend or database migration:

| Displayed range | Value submitted when selected |
| --- | ---: |
| $0 - $24,999 | 0 |
| $25,000 - $49,999 | 25000 |
| $50,000 - $99,999 | 50000 |
| $100,000 - $249,999 | 100000 |
| $250,000 - $499,999 | 250000 |
| $500,000 - $999,999 | 500000 |
| $1,000,000+ | 1000000 |

The lower bound is a representative value for the selected range, not an exact statement of net worth. Existing numeric profile displays will show that value. This is a deliberate compatibility tradeoff: the database does not separately record whether the number originated as an exact amount or a range selection.

When loading an existing profile, a computed selection identifies the range containing its saved amount without rewriting the amount. For example, `75000` displays `$50,000 - $99,999` but remains `75000` in the form until the user changes the dropdown. Zero is a valid selection and is distinct from the empty prompt. Previously saved text values outside the new option lists require the user to choose a supported option before completing onboarding.

No backend, database, DTO, endpoint, routing, or authentication architecture changes are needed for these updates.

## Tests and verification

The implementation followed a red-to-green workflow:

1. Added navigation and onboarding tests before changing functionality. The first run had **23 expected failures and 29 passing tests**: ticker submissions and off-dashboard brand navigation did not navigate, and the dropdown controls did not exist. The style check independently failed on the existing gradients and rounded corners.
2. Implemented the requested behaviors and styles. An initial compile exposed use of an ES2023 array helper; it was replaced with an ES2022-compatible reduction to match the existing TypeScript target.
3. All **52 tests passed** after implementation. Added two HTTP integration regressions to verify the actual onboarding service request contract and recovery after a failed save.
4. Initial implementation validation: **54 tests passed across 7 test files**, the QoF style check passed, the production Angular browser/SSR build passed, and `git diff --check` passed. The production build needed local socket access for Angular SSR/prerendering; the initial sandboxed attempt was blocked by `listen EPERM` and the retry completed successfully.

Coverage includes normalized tickers with dot and hyphen symbols, empty searches, brand navigation and dashboard no-ops with query/fragment URLs, all requested option labels, required prompts, all seven numeric range selections, saved-amount range boundaries without value loss, unsupported option rejection, successful completion, real HTTP payload types, and server-error handling without losing selections. Existing auth, app routing, market-data, trade-page, and candlestick tests also pass.

Reproduce from `frontend/` with the project's Node/npm runtime available:

```bash
npm test -- --watch=false
npm run test:qof-styles
npm run build
```

Tests use Angular TestBed and the HTTP testing controller; they do not require live backend or database services. Live signup against a deployed backend was not exercised. Existing unrelated uncommitted work was preserved.


## Additional QoF follow-up

### Circular loading indicator

Restored `border-radius: 50%` on the shared `LoadingComponent` spinner. Its equal width and height produce a circle, and its component-scoped rule takes precedence over the global zero-radius default. This restores both trade-page loading indicators because they reuse the same component. Status announcements, configurable labels, and reduced-motion behavior remain intact.

The style regression check now includes this narrow exception rather than excluding the entire loading component from inspection. It still checks that component for gradients and any other rounded corners, and fails if the spinner is no longer circular.

### Portfolio placeholder disclosure

The dashboard's visible heading now reads **Portfolio overview (placeholder)**. The parenthetical label explains that the displayed account figures, holdings, and history are demonstration data. The component's existing sample data and chart behavior are retained.

### Risk tolerance: researched three-choice dropdown

Replaced the free-form risk tolerance input with a required native dropdown, following the same option-list, signal binding, change handler, and validation structure as the other onboarding dropdowns. The three choices are below the requested maximum of seven and ordered from lower to higher risk:

| Stored value / displayed choice | Meaning in this UI |
| --- | --- |
| Conservative | Willing and able to accept lower investment losses and price fluctuations |
| Moderate | Willing and able to accept a medium level of losses and fluctuations |
| Aggressive | Willing and able to accept higher losses and fluctuations |

An empty disabled prompt requires an explicit choice; no risk level is chosen automatically. Short visible help text is linked with `aria-describedby`, so users can understand the scale when selecting a value. `submit()` rejects empty or unsupported risk tolerance values before sending the existing onboarding request. Supported saved values such as `Moderate` populate the dropdown on profile load; unsupported legacy free text requires a new selection.

Research consulted on October 8, 2026:

- [Investor.gov: Risk Tolerance](https://www.investor.gov/introduction-investing/investing-basics/glossary/risk-tolerance) describes tolerance in terms of both capacity and willingness to accept investment losses for potential returns. This informs the help text.
- [Investor.gov: Beginners' Guide to Asset Allocation, Diversification, and Rebalancing](https://www.investor.gov/additional-resources/general-resources/publications-research/info-sheets/beginners-guide-asset) explains the conservative-to-aggressive spectrum: preserving capital versus accepting more risk for potential returns. This supports the endpoints.
- [FINRA: Know Your Risk Tolerance](https://syndication.finra.org/content/know-your-risk-tolerance) explains that objectives, time horizon, reliance on invested funds, and personal comfort affect willingness and ability to accept risk. This supports asking users to assess both willingness and ability.

The three-level scale and its middle `Moderate` choice are an application design simplification informed by these sources; they are not a regulator-mandated scale or a scored risk questionnaire. No allocation percentages, return guarantees, or investment recommendations are inferred from a selection. Plain string values preserve the existing frontend request, backend DTO, profile display, and database design; no backend or DB change is required.

### Follow-up test-first verification

Added tests before implementation and confirmed **9 expected failures with 55 passing tests**: the spinner was square, the dashboard heading lacked the disclosure, the risk dropdown was absent, and unsupported risk values were accepted. The revised style check also failed because the spinner was not circular.

After implementation, **64 tests passed across 9 files**. New coverage checks computed circular spinner geometry and status accessibility, the parenthetical dashboard heading, exact risk choices and required empty prompt, each of the three selected values in a real HTTP onboarding request, rejection of empty and unsupported values, and restoration of a saved selection with accessible help text. The existing HTTP save-failure test now supplies a supported risk choice so it continues exercising the server-error path.

The updated QoF style check, production Angular browser/SSR build, and `git diff --check` also passed. The same reproduction commands above apply. These checks use mocked HTTP responses and do not exercise a live signup backend.

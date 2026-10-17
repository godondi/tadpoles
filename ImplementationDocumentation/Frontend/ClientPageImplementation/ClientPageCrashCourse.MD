> **Current implementation:** See the [provider-backed USD trading guide](ClientRefactorCrashCourse.MD) for startup, banking, execution, roles and recovery. The material below is historical implementation evidence and may describe retired behavior.

# Client page crash course

This guide describes the **local simulation** implemented in this repository. A confirmed order is a durable application order that a local worker fills using synthetic prices. It does **not** reach an exchange or broker, move real money, or buy a real asset. There is currently no live-order switch.

Run commands from the repository root (`/home/one/Projects/tadpoles`). The examples below assume the default `.env.template` ports: frontend `http://localhost:8086`, backend `http://localhost:8082`. Use the actual ports in your `.env` if you changed them. [RolesCredentials.MD](RolesCredentials.MD) has every local role login.

## Start and enable the simulation

For the normal Compose stack, first prepare `.env` using `.env.template` if needed. Keep the real database and JWT credentials private. Run the migration and the ordinary role seed, then **opt in** to synthetic instruments and demo funds:

```bash
docker compose up -d database
docker compose run --rm database-migrate
docker compose run --rm database-demo-seed
docker compose --profile simulation run --rm database-simulation-seed
TRADING_SIMULATION_ENABLED=true TRADING_SIMULATION_WORKER_ENABLED=true \
  docker compose up -d --build backend frontend
```

Both switches matter. `TRADING_SIMULATION_ENABLED` permits new admissions; `TRADING_SIMULATION_WORKER_ENABLED` processes already accepted jobs. Both default to `false`. If you start the backend again without the overrides, it returns to the values in `.env` or the defaults. Set both to `true` in your local `.env` if you want them to persist across ordinary `docker compose up` calls. Leave the worker running when you merely want to stop **new** orders; accepted orders can then finish. A worker outage leaves them recorded and pending for recovery.

The simulation seed creates five `SIM-` instruments and opens separate USD 10,000, GBP 10,000 and INR 100,000 accounts for `demo.client` and `demo.client2`. The seed is idempotent: running it again does **not** refill money already spent. It does not credit every client. The ordinary role seed alone leaves demo cash at zero.

For a throwaway browser demo, `bash scripts/start-client-review.sh` builds and starts a separate, already enabled and seeded stack at the same frontend URL. `bash scripts/start-client-review.sh --reuse` rebuilds its apps while retaining its disposable database. `bash scripts/stop-client-review.sh` removes that review database and its orders. Do not run the review stack and normal Compose stack on the same ports simultaneously.

Sign in as `demo.client` using [the local credentials](RolesCredentials.MD), then visit **Markets** and search `SIM-`. If an instrument says “Browsing only,” open it to read the eligibility reason. Its catalog entry alone does not make it tradeable.

## Add simulated cash to an account

New clients created while simulation is enabled receive configured **zero-balance** currency accounts when their account is created during onboarding. The older `/auth/register/client` route also creates them at registration. The default currency list is `USD,GBP,INR`; `TRADING_SIMULATION_ACCOUNT_CURRENCIES` changes the list for **new** accounts only. Complete onboarding before placing an order. Existing legacy accounts are not silently converted or funded when you turn on simulation; their opening state needs a separate verified migration.

There is no client deposit, top-up, withdrawal or balance-edit button. For the two demo clients, use the opt-in seed above **once** to get the opening test funds. For another local simulated account, use a journaled setup credit. Never edit `clients.cash_balance` or `client_cash_accounts.posted` directly; that would break the financial audit and reconciliation. The following transaction is a template for a **local test database**, run by its setup/migration owner. Open `psql` in the Compose database container:

```bash
docker compose exec database sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1'
```

In `psql`, choose the existing simulated username, one of that account's configured currencies, a positive exact amount, and a **new setup reference**. The example makes one USD 1,000 test credit:

```sql
\set target_username 'my.client'
\set target_currency 'USD'
\set credit '1000.00'
\set setup_ref 'local-my-client-usd-opening-001'
BEGIN;
SELECT c.client_id
FROM clients c JOIN users u USING (user_id)
WHERE u.username = :'target_username' AND c.financial_model = 'SIMULATION'
FOR UPDATE OF c;

WITH funding AS (
  INSERT INTO cash_journal
    (posting_id, client_id, currency, delta, setup_reference, posting_group, actor, recorded_at)
  SELECT gen_random_uuid(), a.client_id, a.currency, (:'credit')::numeric(28,12),
         :'setup_ref', gen_random_uuid(), 'local simulation setup', now()
  FROM client_cash_accounts a
  JOIN clients c USING (client_id)
  JOIN users u USING (user_id)
  JOIN client_profiles p ON p.client_id = c.client_id
  WHERE u.username = :'target_username'
    AND c.financial_model = 'SIMULATION'
    AND p.onboarding_complete
    AND a.currency = :'target_currency'
    AND (:'credit')::numeric(28,12) > 0
  ON CONFLICT (setup_reference) DO NOTHING
  RETURNING client_id, currency, delta
)
UPDATE client_cash_accounts a
SET posted = a.posted + f.delta, version = a.version + 1, updated_at = now()
FROM funding f
WHERE a.client_id = f.client_id AND a.currency = f.currency
RETURNING a.client_id, a.currency, a.posted, a.reserved, a.posted - a.reserved AS available;
COMMIT;
```

Expect **one** returned row. Zero rows means no credit was made: check the username, completed onboarding, `SIMULATION` model, currency account, positive amount and whether that setup reference was already used. Reusing the same reference cannot credit twice. A new reference is a **new** credit, so do not generate one just to retry an uncertain setup. The transaction locks the client before posting and adds the cash journal and account balance together. Reopen **Portfolio** to verify `posted`, `reserved` and `available`; currencies remain separate. Production deposits and opening-state migration require their own controlled workflow.

## Change the market ticker list

**Browseable catalog:** Instruments live in `instruments`. An authorized `ADMIN` identity without the `CLIENT` role can use `POST /api/instruments` and `PATCH /api/instruments/{instrumentId}`. The demo role credentials do not include such an administrator. In a local database, an owner can add a *browse-only* example:

```sql
INSERT INTO instruments
  (instrument_name, ticker, currency, asset_class, security_type, is_active)
VALUES ('Example test equity', 'SIM-EXAMPLE', 'USD', 'EQUITY', 'STOCK', true)
ON CONFLICT (ticker) DO NOTHING;
```

The new symbol appears in **Markets** after refresh, but says **Browsing only** until it has an enabled, matching row in `instrument_trading_policies`. The client can search by ticker/name, filter asset class and page through the catalog. The top search box and an instrument's provider chart are exploration tools; provider chart data does not set the synthetic order price. A chart may be unavailable while simulation trading still works.

**Tradeable synthetic instrument:** Add a policy through a reviewed local migration or a deliberate extension of `database/demo/simulation.sql`. The five existing policy rows are working examples. For the browse-only `SIM-EXAMPLE` row above, the following local owner SQL opts it into the same USD/share rules as the US fixture, with **synthetic** bid 49 and ask 50:

```sql
INSERT INTO instrument_trading_policies
  (instrument_id, policy_version, venue, quantity_unit, price_unit, currency,
   quantity_step, currency_scale, max_quantity, max_notional,
   quote_max_age_seconds, future_skew_seconds, retry_seconds, max_attempts,
   session, enabled, raw_price_unit, price_multiplier, fixture_bid, fixture_ask)
SELECT i.instrument_id, 'local-sim-example-v1', 'TEST-US', 'shares', 'USD', 'USD',
       1, 2, 10000, 1000000, 30, 2, 120, 3,
       'SIMULATION_24_7', true, 'USD', 1, 49, 50
FROM instruments i
WHERE i.ticker = 'SIM-EXAMPLE' AND i.currency = 'USD'
ON CONFLICT (instrument_id) DO NOTHING;
```

Check that one row was inserted; a zero-row result means the catalog symbol/currency did not match or a policy already exists. Refresh Markets and verify the instrument says “Simulation available” for a funded USD client. For a different instrument, choose its currency, venue, quantity unit/step, currency scale, size and notional limits, session, quote age, raw price unit, multiplier, synthetic bid/ask and policy version deliberately. The account also needs a cash account in that currency. The current quote adapter reads `fixture_bid` and `fixture_ask` from the policy and produces a fresh synthetic quote at request time; adding a symbol does **not** connect it to a real price feed. For example, `SIM-UK` stores raw GBX quotes and a `0.01` multiplier to book in GBP. FX and crypto are simulated pair positions, not currency conversion or spot settlement.

Rerunning the seed will add a **new** seed policy but will **not** overwrite an existing policy, because it uses `ON CONFLICT ... DO NOTHING`. For an existing tradeable instrument, plan a separate policy-versioned change. Drain accepted jobs before changing its quote/precision policy. Avoid renaming or changing the currency of an instrument with orders or positions: records retain snapshots, some older catalog displays use the current name, and the database blocks unsafe currency changes. Prefer a new symbol and disable the old one for new trading. An `is_active=true` catalog flag alone is never proof of trading eligibility.

## Place and follow an order

1. Sign in as a `CLIENT` with completed onboarding and simulated funds. In **Markets**, choose an instrument marked “Simulation available,” then select **Buy**. An owned position in **Portfolio** also offers **Sell** when units are available.
2. Enter the quantity as an exact decimal string. The policy controls the step and maximum. Select **Review estimated impact**. This read-only preview shows the current synthetic ask for a buy or bid for a sell, estimated cost/proceeds, available cash/units, projected cash/units, currency, quote time and zero fee. It neither reserves funds nor creates an order.
3. If eligible, press **Confirm simulated buy/sell** before the preview expires. The browser saves one request reference before sending; duplicate clicks are blocked. The server checks eligibility and unreserved resources again and may use a **different execution quote** later. This is a market-style simulated order, not a guaranteed-price or limit order.
4. **ACCEPTED** means the instruction and reservation were committed, not that it filled. **EXECUTING** means the worker claimed it. **FILLED** means cash, units, journals and fill evidence were booked together. **REJECTED** shows its reason and whether it failed at admission or execution. A final rejection releases its own reservation.
5. Open the receipt and timeline in **Orders**; open **Portfolio** for the latest posted, reserved and available amounts. Order detail refreshes automatically while pending; account/history use authenticated cursor polling, bounded to about 10 minutes per view. Refresh a page to resume after that. Order history can filter status, side, rejection stage, date and instrument.

If submission times out or the tab reloads before the outcome is known, use the ticket's **Check order status** action. It looks up the saved request reference. **Retry the same instruction** reuses that reference and payload. A 404 during recovery can mean the first request is still in flight; do not start a new order with a new reference just to resolve uncertainty. If you sign out, private order state clears and the server session is revoked when reachable; previously accepted orders continue processing. Sign in again and use your own order history. Another client's order or a staff-only account is not accessible from this Client workspace.

An API caller can use the same contract: `POST /api/orders/preview` with `{ "instrumentId": 16, "side": "BUY", "quantity": "1", "orderType": "MARKET" }`, then `POST /api/orders` with that body, a signed Client bearer token and a unique `Idempotency-Key` of 16–128 letters/digits/underscores/hyphens. **Look up the instrument ID in your own catalog; `16` is only an example from one seeded database.** Save the key and reuse it only for the *same* instruction. `GET /api/orders/by-request/{key}` and `GET /api/orders/{id}` recover the receipt. The server derives account, price, actor and status; do not send those fields. A new accepted order returns HTTP 202; a stored admission rejection returns 422; malformed input is 400; a key reused for a different instruction is 409; unavailable admission is 503. The browser ticket handles these details for ordinary users.

## Other Client pages and boundaries

| Page/feature | What it does |
| --- | --- |
| **Sign up / onboarding / profile** | Creates a Client identity, collects the existing profile fields, and keeps the account scoped to that identity. A new simulated client starts with zero configured currency balances. Profile completion does not add funds. |
| **Overview** | Shows separate available balances, current positions and recent orders; each amount carries its currency. |
| **Portfolio** | Shows posted cash/units, amounts reserved by pending orders, and spendable/available amounts. A fully sold position can remain visible with zero units for history. |
| **Markets / instrument detail** | Search/filter the catalog, inspect eligibility, browse an independent provider chart, and enter a ticket only for eligible synthetic instruments. |
| **Orders / order detail** | Filter history, read accepted versus filled/rejected outcomes, booked price and effects, decision evidence and a client-safe timeline. Older legacy rows have weaker evidence and are labelled accordingly. |
| **Account menu / sign out** | Opens profile/history and revokes the signed-in session when the server is reachable; local private state clears even if the logout request fails. |

The Client pages do not provide live broker execution, deposits/withdrawals, arbitrary balance edits, currency conversion, order cancellation, limit orders, a watchlist, performance figures or staff investigation/reporting. For the implementation rationale, test evidence and remaining platform work, see [Implementation.MD](Implementation.MD) and [Verification.txt](Verification.txt).

## Q&As

### After `docker compose down -v --rmi all --remove-orphans`, `docker compose build`, and `docker compose up`, why does my account say “Legacy Account”? Are there different types?

Yes. `LEGACY` and `SIMULATION` are **financial data models**, not different login roles. The Client role can be associated with either one.

On a fresh install, schema 004 gives every existing-style client `financial_model='LEGACY'` by default. Ordinary `docker compose up` runs the standard role seed, which creates the demo clients with the older scalar `cash_balance` and no simulated currency accounts. The account page therefore says **Legacy Account** and treats that old balance/holding history as read-only, with unknown currency or incomplete execution evidence where applicable. That label describes the stored data; it does not mean the account is broken.

The optional `database-simulation-seed` is behind the `simulation` Compose profile, so those three commands do not run it under the default configuration. The seed explicitly changes **only `demo.client` and `demo.client2`** to `SIMULATION`, creates USD/GBP/INR cash accounts, and records their opening test credits in the cash journal. The account page then shows **Simulated funds · no real money**, with posted, reserved and available balances per currency. New clients created while simulation is already enabled get zero-balance simulated accounts, but an old Legacy account is not automatically converted by merely changing a flag. A non-demo Legacy account needs a verified opening-state migration; relabelling it or copying its unknown-currency scalar balance into USD would be unsafe.

The `-v` in `docker compose down -v` removes the Compose database volume and **all prior local orders, balances and users in it**. The standard seed recreates the demo identities, not their former order history. Do not use that reset when you need to keep the data.

### Why do the preloaded Market tickers say “Browsing only”? What other ticker statuses exist, and how do I enable one?

The base catalog is loaded from `database/schema/002_tadpoles_test_data.sql` so you can browse names and provider charts. It does not give those symbols simulation policies. The normal `docker compose up` also leaves admission and the worker off unless you set their switches. **Browsing only** is the resulting label; it does not by itself mean the catalog record is inactive.

The Markets table has **two displayed availability labels**:

| Label | Meaning |
| --- | --- |
| **Browsing only** | This client cannot place a simulation order for that ticker now. The row shows a more specific reason, and the instrument page repeats it. |
| **Simulation available** | The current client/instrument configuration passes the availability check and the simulated ticket can open. This is not a live-trading status or a promise that a particular quantity will be accepted or filled. |

The backend gives “Browsing only” when simulation admission is off, schema 004 is unavailable, the client is not a completed simulated account, the instrument has no enabled policy, its catalog entry is inactive, its policy session is closed, or the client lacks a cash account in the policy currency. These checks are evaluated in that order, so the displayed reason is the **first** blocker found. A ticker marked “Simulation available” can still fail preview or execution because of quantity rules, insufficient unreserved cash/units, a bad or stale quote, or a price change. Those become ticket/order outcomes, not new Market labels.

To enable the **supplied synthetic tickers** after the three commands in the question, run this from the repository root:

```bash
docker compose --profile simulation run --rm database-simulation-seed
TRADING_SIMULATION_ENABLED=true TRADING_SIMULATION_WORKER_ENABLED=true \
  docker compose up -d --no-deps --force-recreate backend
```

Then refresh Markets, search `SIM-`, and sign in as `demo.client` or `demo.client2`. Their completed onboarding and currency accounts come from the seeds. If you want the switches to survive later ordinary `docker compose up` calls, put both `TRADING_SIMULATION_ENABLED=true` and `TRADING_SIMULATION_WORKER_ENABLED=true` in your local `.env`; Compose reads them when it creates the backend container. Running the simulation seed again does not replenish spent funds.

To make **another** ticker tradeable in this implementation, give it a matching enabled row in `instrument_trading_policies`, a synthetic bid/ask and unit/currency rules, plus an account holding that currency; the `SIM-EXAMPLE` SQL in [Change the market ticker list](#change-the-market-ticker-list) shows a local example. Adding a catalog row or setting `is_active=true` alone only enables browsing. The five supplied simulation fixtures are US, UK and Indian equities, EUR/USD and BTC/USD; the older preloaded catalog symbols remain browse-only until each has an explicit policy. There is currently **no “regular/live trading” mode**: enabling a ticker here authorizes only the local synthetic simulation, never a broker order.

### What database schemas does the current project have, and what does each contain?

The SQL files are **versions of one PostgreSQL application schema**, not four separate databases or four trading modes. They create tables in the default `public` schema. A fresh database applies `database/schema/001_tadpoles_schema.sql` through `004_client_simulation.sql` in order; the corresponding files in `database/migrations/` update an existing database. The files have these jobs:

| File | Purpose |
| --- | --- |
| [`001_tadpoles_schema.sql`](../../../database/schema/001_tadpoles_schema.sql) | Original identity, advisory/portfolio, catalog, legacy trade, token and audit tables. |
| [`002_tadpoles_test_data.sql`](../../../database/schema/002_tadpoles_test_data.sql) | Inserts baseline example data. Despite its location, it **does not define a new schema** or grant trading eligibility to its tickers. |
| [`003_workspace_roles.sql`](../../../database/migrations/003_workspace_roles.sql) | Adds `TRADING_OPS` and `FINANCE` roles and read indexes; no new tables. |
| [`004_client_simulation.sql`](../../../database/migrations/004_client_simulation.sql) | Adds the simulated self-directed order/account model, durable execution evidence and sessions; extends `clients` and `client_trades`. |

The current tables, grouped by their purpose, are:

| Area | Tables and meaning |
| --- | --- |
| Identity and access | `users` stores login identities; `roles` names permissions; `user_roles` assigns them; `refresh_tokens` stores refresh-token hashes; `auth_sessions` tracks revocable sessions. |
| Client and older advisory model | `clients` links an identity to an account and retains the older scalar `cash_balance`; `client_profiles` stores profile/onboarding fields; `advisors` identifies advisors; `model_portfolios` defines suggested portfolios; `model_portfolio_holdings` gives target weights; `client_subscriptions` links clients to models; `trade_suggestions` records advisor suggestions. These older tables do not execute a self-directed order. |
| Market catalog and policy | `instruments` is the browseable ticker catalog; `instrument_trading_policies` holds explicit simulation eligibility, venue, currency, size/precision, session and fixture quote rules. A catalog row alone is insufficient for trading. |
| Orders and positions | `client_trades` stores older trades and new self-directed order intents, distinguished by `record_kind`; `client_holdings` contains older dated snapshots; `client_cash_accounts` and `client_positions` are the authoritative per-currency cash and current units for simulated accounts; `order_reservations` locks funds or units while an accepted order awaits its outcome. |
| Execution and evidence | `order_quote_observations` retains observed prices; `order_rule_decisions` records validation outcomes; `order_fills` records booked fills; `cash_journal` and `position_journal` retain financial movements; `order_events` records lifecycle events; `order_execution_jobs` and `order_execution_attempts` make worker processing durable and inspectable; `outbox_events` retains events for downstream delivery. |
| Operations | `audit_logs` tracks administrative changes; `schema_migrations` records applied database versions. |

These are **transactional application tables**, not the separate analytics database proposed in [HailMary.md](../../HailMary.md). See the SQL definitions for columns, keys and constraints; the grouping above is a functional map rather than a substitute for those definitions.

### What exactly is simulated? Does an order still pass through the backend and database?

**Yes.** The Angular ticket sends an authenticated request to the Spring backend. The backend validates account, instrument, policy, quote, quantity and available cash/units. Admission saves the order intent, decision/quote evidence, reservation, execution job and outbox event in PostgreSQL. A durable backend worker later obtains another synthetic execution quote, rechecks the rules, and either commits the fill together with cash/position changes and journals, or records a rejection and releases the reservation. The Client pages then read the persisted order, timeline and balances. A browser-only animation or local-only balance change would not be the implemented flow.

“Simulated” refers to the **external market and money**, not the internal transaction processing: the quote adapter produces deterministic fixture bid/ask prices, the worker simulates an all-or-nothing fill or rejection, and setup credits are test balances. There is no real broker or exchange order, bank deposit, clearing or legal settlement. An `ACCEPTED` order is persisted and reserved but may still be rejected at execution; `FILLED` means the application's simulated booking committed, not that an outside venue executed it. The two switches in [Start and enable the simulation](#start-and-enable-the-simulation) govern admission and worker processing; disabling the worker after acceptance leaves the job pending for recovery rather than discarding the order.

### Do HailMary.md requirements call for simulated mode and synthetic stocks? Could there be only one regular trading model?

[HailMary.md](../../HailMary.md) separates the source brief from proposed implementation. Its source scope calls for self-directed buy/sell, execution-time pricing, cash/holdings/history, auditable outcomes, and UK/US/India equities, FX and crypto. It explicitly says the brief **does not settle live broker/exchange execution versus simulated execution**; that is a decision to agree. It treats a simulator as a way to demonstrate architecture and warns against presenting it as live market execution. Synthetic prices and `SIM-` fixture instruments are therefore **implementation choices**, detailed for this delivery in [ClientAndBuySell.MD](../../../ClientAndBuySell.MD), not source-mandated extra asset classes or a second required customer account type.

A future system **could use one authoritative trading account/order model** with a real execution and market-data adapter after the business defines and approves the live rules. That would require provider and account arrangements, execution/reconciliation and settlement semantics, currency and instrument controls, operational readiness and appropriate testing. It would also require a verified migration of older balances, holdings and trade history into that model. The current `LEGACY` account is an older data model, **not “regular/live trading”**; simply renaming it or removing the `SIMULATION` flag would not supply any of those capabilities or make its scalar balance reliable across currencies. The present split protects old records while the new simulated flow is developed. A single model is a plausible target architecture, but neither legacy-only nor simulation-only currently establishes that the entire LEAP brief is met, including independent historical analytics and operational investigation views.

### Why are the preloaded Market tickers not allowed for trading?

The original seed inserts those symbols into `instruments` for catalog browsing. It supplies no `instrument_trading_policies` rows or synthetic execution quotes for them, and the default Compose startup keeps simulation admission/worker switches off. Default demo accounts also remain `LEGACY` until the optional simulation seed runs. As a result, even a visible and `is_active=true` ticker fails trading eligibility. This is deliberate: an old symbol's name, currency and chart do not establish its venue, units, price scale, session, trade limits or a valid execution source.

The simulation seed opts in **separate `SIM-` fixtures**, not the older preloaded symbols. To make one of the older symbols *simulation-tradeable*, an owner would need to define and review an enabled policy and fixture quotes for that exact instrument, turn on simulation admission/worker processing, and use a completed simulated account with the required currency cash account, as described above. That would still be **synthetic trading only**. Real trading needs an approved live market-data/execution path and operating rules; no existing flag or seed turns a preloaded ticker into a broker-routed instrument.

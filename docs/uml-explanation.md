# Mock UML Design Explanation

This document explains the reasoning behind the mock UML in `docs/uml.mmd`, what each class is meant to represent, and what the connections between classes mean.

## Why this design was chosen

The current repository contains:
- a relational schema in `database/schema/tadpoles_schema.sql`
- integration tests that prove expected database behavior
- only minimal backend application code today

Because of that, the UML in `docs/uml.mmd` was designed as a **future-state Spring Boot architecture** rather than a diagram of the code that currently exists.

The design assumes a fairly standard Spring Boot application using:
- **Spring Web** for REST APIs
- **Spring Security** for authentication and authorization
- **JWT** for stateless login sessions
- **JPA repositories** for persistence
- **service classes** for business logic
- **DTOs** for request/response payloads

This structure was chosen because it cleanly separates responsibilities:
- controllers handle HTTP requests
- services hold business rules
- repositories access the database
- entities map to the database tables
- security classes manage login and request authorization

---

## High-level design philosophy

The mock UML is layered on purpose.

### 1. Security layer
This layer handles who can access the system and whether a request is authenticated.

### 2. Controller layer
This layer exposes business capabilities through REST endpoints.

### 3. Service layer
This layer contains the actual business logic, such as assigning advisors, subscribing clients to model portfolios, or generating rebalancing trades.

### 4. Repository layer
This layer is responsible for loading and saving data.

### 5. Domain/entity layer
This layer maps directly to the database schema and represents the business objects in code.

### 6. DTO layer
This layer defines request/response shapes so the API does not expose database objects directly.

This is a common Spring Boot design because it scales well as features grow and makes testing easier.

---

## Assumptions used in the UML

A few things in the UML are **assumed**, because they are not yet present in the repository:

1. **JWT authentication exists**
   - The schema currently does not include users, passwords, or roles.
   - So `AppUser`, `Role`, and authentication classes were added as conceptual placeholders.

2. **A REST API exists**
   - There are no controllers in the current backend yet.
   - The diagram assumes typical endpoints for clients, portfolios, holdings, and trades.

3. **JPA-style persistence exists**
   - The schema implies natural entity/repository mappings.
   - The UML models the likely repository layer you would build around those tables.

4. **Rebalancing is a business use case**
   - Because you have model portfolios, current client holdings, and trades, rebalancing is a logical service to include.

---

# Class groups and what they do

## Security classes

### `SecurityConfig`
This is the central Spring Security configuration class.

It would typically:
- define the filter chain
- disable session-based auth if using JWT
- configure which routes are public vs protected
- register authentication providers
- attach the JWT filter before Spring's username/password filter

Why it exists:
- Spring Security usually has a single place where security rules are wired together.

### `JwtAuthenticationFilter`
This filter examines every incoming request.

It would typically:
- read the `Authorization: Bearer <token>` header
- extract the JWT
- validate the token
- load the user details
- place the authenticated user into the Spring Security context

Why it exists:
- With JWT, each request must carry its own authentication token.

### `JwtService`
This class is responsible for JWT operations.

It would typically:
- generate tokens when a user logs in
- extract claims such as username or expiration
- validate whether a token is still valid and belongs to the correct user

Why it exists:
- Token generation and parsing should be centralized and reusable.

### `CustomUserDetailsService`
This class would bridge your application users to Spring Security.

It would typically:
- look up a user by username or email
- return a Spring Security `UserDetails` object

Why it exists:
- Spring Security expects a user-loading component during authentication.

### `AuthenticationService`
This service handles authentication workflows.

It would typically:
- register new users
- verify credentials during login
- ask `JwtService` to create a token

Why it exists:
- login/registration logic is business logic and should not live inside controllers.

### `AppUser`
This is an assumed application user entity.

It would usually contain:
- identity fields
- username/login
- password hash
- enabled/disabled status
- role(s)

Why it exists:
- JWT and Spring Security need a user model, but that user table is not currently in the schema.

### `Role`
This enum represents authorization roles.

Examples used in the UML:
- `ADVISOR`
- `ADMIN`
- `OPERATIONS`

Why it exists:
- authentication answers “who are you?”
- authorization answers “what are you allowed to do?”

### `AuthController`
This controller exposes authentication endpoints.

Likely endpoints:
- login
- register
- token refresh

Why it exists:
- clients need an API entry point to obtain JWT tokens.

### `LoginRequest`, `RegisterRequest`, `JwtResponse`
These are DTOs for auth flows.

Why they exist:
- API payloads should be modeled explicitly rather than passing entities directly.

---

## Controller classes

Controllers represent the external API layer.

They usually:
- receive HTTP requests
- validate input
- call services
- return JSON responses

They should **not** contain heavy business logic.

### `AdvisorController`
Represents advisor-focused endpoints.

Possible responsibilities:
- fetch an advisor's clients
- view portfolios associated with the advisor's book of business

### `ClientController`
Represents client management endpoints.

Possible responsibilities:
- create a client
- fetch client details
- assign a client to an advisor
- subscribe a client to a model portfolio

This controller exists because the `clients` table is central to the schema and ties together advisors, model portfolios, holdings, subscriptions, and trades.

### `PortfolioController`
Represents model portfolio endpoints.

Possible responsibilities:
- create model portfolios
- view model details
- define target allocations

This fits the schema because `model_portfolios` and `model_portfolio_holdings` together represent the investment template.

### `HoldingsController`
Represents read-heavy portfolio state endpoints.

Possible responsibilities:
- view current client holdings
- compare current holdings to target model holdings
- show drift from allocation targets

This controller is useful because holdings are conceptually different from trades: holdings show the current state, trades show the activity that changed that state.

### `TradeController`
Represents trade endpoints.

Possible responsibilities:
- place a trade
- retrieve a client's trade history
- trigger or preview rebalancing

This matches the schema because `client_trades` naturally supports a transaction/activity API.

---

## Service classes

Services hold business rules and orchestration logic.

This layer is important because the same rule might be needed by:
- controllers
- scheduled jobs
- integration flows
- admin operations

### `AdvisorService`
Encapsulates business operations related to advisors.

Examples:
- finding an advisor
- listing all clients assigned to an advisor

### `ClientService`
Encapsulates client-centric workflows.

Examples:
- create a client
- fetch a summary that combines client, advisor, and portfolio data
- assign or change an advisor

This is important because `Client` is one of the most connected domain objects in your schema.

### `PortfolioService`
Handles model portfolio logic.

Examples:
- create portfolios
- define target holdings and target weights
- retrieve target allocations

This service corresponds closely to:
- `model_portfolios`
- `model_portfolio_holdings`

### `SubscriptionService`
Handles the relationship between clients and model portfolios.

Examples:
- subscribe a client to a model portfolio
- fetch subscription history or current subscription data

This service exists because `client_subscriptions` is a separate relationship table with its own business meaning and `subscribed_date` field.

### `HoldingsService`
Handles current holdings and derived portfolio views.

Examples:
- retrieve client holdings
- aggregate positions
- calculate drift against a target model

This is supported by the schema because `client_holdings` stores actual positions, while model holdings store intended allocations.

### `TradeService`
Handles trading operations.

Examples:
- create buy/sell trades
- return trade history
- enforce trade validation rules

Trade rules are a good fit for a service because they often involve validation such as:
- allowed trade types
- positive quantities
- valid instruments
- possibly business-day constraints

### `RebalancingService`
This is a higher-level orchestration service.

Examples:
- compare actual holdings with target model weights
- decide what trades are required to realign a client portfolio
- generate suggested or executable rebalance trades

Why this was included:
- your schema strongly suggests a portfolio management use case
- rebalancing is a natural business capability when you have:
  - a target model
  - actual holdings
  - a trade table

---

## Repository classes

Repositories represent data access.

In a Spring Boot design, these would likely be interfaces such as:
- `AdvisorRepository`
- `ClientRepository`
- `ModelPortfolioRepository`
- etc.

They would usually:
- load rows by primary key
- save entities
- support lookup queries
- expose custom query methods

### Why one repository per main entity/table?
Because it keeps persistence responsibilities focused and mirrors the schema cleanly.

#### `AdvisorRepository`
Used to read/write advisor data.

#### `ClientRepository`
Used to manage clients and client lookups.

#### `ModelPortfolioRepository`
Used to manage model portfolios.

#### `InstrumentRepository`
Used to manage securities/instruments.

#### `ModelPortfolioHoldingRepository`
Used to manage target portfolio composition rows.

#### `ClientSubscriptionRepository`
Used to manage client-to-model subscriptions.

#### `ClientHoldingRepository`
Used to manage actual holdings snapshots.

#### `ClientTradeRepository`
Used to manage trade records.

#### `UserRepository`
Used to manage security users.

This last one is assumed and supports JWT/Spring Security rather than the current provided schema.

---

## Domain/entity classes

These classes represent the business data model and map most directly to the SQL schema.

## `Advisor`
Represents the `advisors` table.

Fields in the UML:
- `advisorId`
- `advisorName`

Schema note:
- the SQL column is currently named `adviosr_name`, which appears to be a typo in the schema
- the UML uses the cleaner domain name `advisorName`

### Role in the system
An advisor is responsible for one or more clients.

---

## `Client`
Represents the `clients` table.

Fields in the UML:
- `clientId`
- `clientName`

### Role in the system
A client is the central business object.
A client:
- belongs to an advisor
- has a default model portfolio reference
- may subscribe to model portfolios
- has current holdings
- places trades

---

## `ModelPortfolio`
Represents the `model_portfolios` table.

Fields in the UML:
- `modelPortfolioId`
- `modelName`

### Role in the system
A model portfolio defines the intended allocation structure clients should follow.

---

## `Instrument`
Represents the `instruments` table.

Fields in the UML:
- `instrumentId`
- `instrumentName`
- `ticker`
- `currency`

### Role in the system
An instrument is a tradable asset used in:
- target model holdings
- client holdings
- client trades

It is shared across much of the domain.

---

## `ModelPortfolioHolding`
Represents the `model_portfolio_holdings` join table.

Field in the UML:
- `targetWeightPct`

### Role in the system
This entity links:
- one model portfolio
- one instrument

and stores the target allocation percentage.

This is not just a plain join table, because it contains business data (`target_weight_pct`).

---

## `ClientSubscription`
Represents the `client_subscriptions` table.

Field in the UML:
- `subscribedDate`

### Role in the system
This entity records that a client subscribed to a model portfolio on a particular date.

Again, it is more than just a join table because it has business meaning and a timestamp.

---

## `ClientHolding`
Represents the `client_holdings` table.

Fields in the UML:
- `quantity`
- `asOfDate`

### Role in the system
This entity describes what a client currently holds, or held as of a specific date.

This is useful for:
- reporting
- valuation
- drift calculation
- rebalancing

---

## `ClientTrade`
Represents the `client_trades` table.

Fields in the UML:
- `tradeId`
- `tradeType`
- `quantity`
- `price`
- `tradeDate`

### Role in the system
This entity records trade activity.

It is the event history for changes in client portfolios.

---

## `TradeType`
Enum representing valid trade directions.

Values:
- `BUY`
- `SELL`

This directly reflects the schema check constraint on `client_trades.trade_type`.

---

## DTO classes

DTOs are used to move data into and out of the API cleanly.

### `ClientSummaryDto`
A read model that combines client-related information into a simple response.

Likely content:
- client name
- advisor name
- model portfolio name

This matches the kind of join shown in the integration tests.

### `TradeRequest`
Represents the payload needed to create a trade.

Likely fields:
- client id
- instrument id
- trade type
- quantity
- price

### `PortfolioDriftDto`
Represents a comparison between:
- actual holdings
- target model allocation

This is useful for dashboards and rebalance workflows.

---

# What the connections in the UML mean

The diagram uses different kinds of arrows conceptually, even though Mermaid class diagrams render them with a shared syntax style.

## 1. Dependency / usage connections
Example:
- `ClientController --> ClientService : uses`

Meaning:
- the class on the left depends on the class on the right
- usually this means it calls methods on that class
- in Spring Boot, this is typically dependency injection

Interpretation example:
- `ClientController` does not implement client business logic itself
- it delegates to `ClientService`

---

## 2. Configuration connections
Example:
- `SecurityConfig --> JwtAuthenticationFilter : registers`

Meaning:
- the configuration class wires that component into the application lifecycle
- it is not a domain relationship; it is a framework setup relationship

---

## 3. Persistence mapping connections
Example:
- `ClientRepository --> Client`

Meaning:
- the repository is responsible for reading and writing that entity
- this is a data access relationship

---

## 4. Domain associations derived from the schema
Example:
- `Advisor "1" --> "*" Client : advises`

Meaning:
- one advisor can be linked to many clients
- this comes from the foreign key `clients.advisor_id -> advisors.advisor_id`

These relationships are the most important business relationships in the diagram.

---

# What each domain relationship represents

## `Advisor "1" --> "*" Client : advises`
This means:
- one advisor can advise many clients
- each client references one advisor through `advisor_id`

Business interpretation:
- advisors manage books of clients

---

## `ModelPortfolio "1" --> "*" Client : assigned default model`
This means:
- one model portfolio can be assigned to many clients
- each client references a model portfolio through `model_portfolio_id`

Business interpretation:
- a client may have a default or primary model alignment

---

## `ModelPortfolio "1" --> "*" ModelPortfolioHolding : contains`
This means:
- one model portfolio contains many target holding rows

Business interpretation:
- a portfolio is composed of many instruments with target weights

---

## `Instrument "1" --> "*" ModelPortfolioHolding : target asset`
This means:
- one instrument can appear in many model portfolios

Business interpretation:
- the same instrument can be reused across different strategies

---

## `Client "1" --> "*" ClientSubscription : subscribes`
This means:
- one client can have one or more subscription records

Business interpretation:
- subscriptions track the relationship between clients and model portfolios over time

---

## `ModelPortfolio "1" --> "*" ClientSubscription : subscription target`
This means:
- one model portfolio can have many subscribed clients

Together with the previous relationship, this creates a many-to-many business relationship between clients and model portfolios through `ClientSubscription`.

---

## `Client "1" --> "*" ClientHolding : owns`
This means:
- one client can hold many instruments

Business interpretation:
- actual portfolio state is stored as holdings rows

---

## `Instrument "1" --> "*" ClientHolding : held asset`
This means:
- the same instrument can be held by many clients

Together, `ClientHolding` works as a client-to-instrument association with extra state (`quantity`, `asOfDate`).

---

## `Client "1" --> "*" ClientTrade : places`
This means:
- one client can place many trades

Business interpretation:
- trades form the transaction history for a client

---

## `Instrument "1" --> "*" ClientTrade : traded asset`
This means:
- one instrument can appear in many client trade records

Business interpretation:
- the same security can be traded by many clients many times

---

## `ClientTrade --> TradeType`
This means:
- each trade uses a trade direction enum
- the enum restricts the valid values to `BUY` or `SELL`

---

# Why certain services connect to each other

## Why `RebalancingService` depends on `HoldingsService`
To rebalance a client, the system must know the client's current positions.

## Why `RebalancingService` depends on `PortfolioService`
To rebalance a client, the system must know the target allocation from the model portfolio.

## Why `RebalancingService` depends on `TradeService`
Rebalancing often results in proposed or executed buy/sell trades.

This is a good example of service orchestration:
- one service combines information from several lower-level services
- it performs a larger business workflow

---

# Why DTOs were included

Without DTOs, controllers often become tightly coupled to entity classes.
That causes problems such as:
- leaking internal persistence details
- making future refactors harder
- exposing more data than intended

Using DTOs makes the design safer and clearer.

For example:
- `TradeRequest` describes exactly what the API needs to create a trade
- `ClientSummaryDto` describes a read-only combined view for the frontend
- `PortfolioDriftDto` provides a reporting-friendly response shape

---

# Why the UML is intentionally "mock"

This diagram is not claiming that all of these classes already exist in the repository.

Instead, it does three things:
1. reflects the **real business structure** already implied by the database schema
2. adds the **standard Spring Boot layers** that would likely sit on top of that schema
3. introduces **security concepts** required by the assumption of JWT and Spring Security

That makes it useful as:
- architecture documentation
- a planning tool
- a guide for future implementation

---

# Suggested way to read the UML

If your team is reviewing the diagram together, read it in this order:

1. **Domain layer**
   - understand the business objects first
2. **Repository layer**
   - see how each domain object is persisted
3. **Service layer**
   - identify where the business rules live
4. **Controller layer**
   - identify which APIs expose those business capabilities
5. **Security layer**
   - understand how authentication and authorization wrap around the APIs

This order makes the system easier to understand because it starts with the business model and then moves outward to technical concerns.

---

# Important schema note

The SQL schema currently uses the column name `adviosr_name` in the `advisors` table.
That appears to be a typo.

In the UML and this explanation, the cleaner domain term `advisorName` was used because:
- it better represents the intended meaning
- domain models often normalize naming even if the database has a legacy typo

If you later implement JPA entities, this would typically be handled with explicit column mapping.

---

# Final summary

The UML was designed to represent a realistic Spring Boot application for investment/advisory workflows.

At a business level, the core story is:
- advisors manage clients
- clients are aligned to model portfolios
- model portfolios define target instrument allocations
- clients hold actual positions in instruments
- clients place trades
- services compare current holdings to target allocations and can support rebalancing
- JWT/Spring Security protects the API and controls access to these operations

If you want, the next useful step would be to create either:
- a **shorter student-friendly version** of this explanation
- a **database-only ERD explanation**
- or a **package-by-package implementation plan** showing which Java files you would create first


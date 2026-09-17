# ERD Explanation

This document explains the Mermaid ER diagram in `docs/erd.mmd`, which is based on the SQL schema in `database/schema/tadpoles_schema.sql`.

## Purpose of the ERD

The ERD shows how the database tables relate to one another. It is a database-focused view, not an application architecture diagram.

The goal is to make it easy to see:
- which tables exist
- which columns are primary keys
- which columns are foreign keys
- which tables are connected through join tables
- which relationships are one-to-many vs many-to-many

---

## How to read the diagram

In the ERD:
- each table is shown as an entity box
- `PK` means primary key
- `FK` means foreign key
- `UK` means unique constraint
- connectors show how rows in one table relate to rows in another

Cardinality symbols mean:
- `||` = exactly one
- `o{` = zero or many

So a line like:

`advisors ||--o{ clients`

means:
- one advisor can have many clients
- each client points to one advisor

---

# Table-by-table explanation

## `advisors`
This table stores advisor records.

### Columns
- `advisor_id` — primary key
- `adviosr_name` — advisor name

### Role in the model
An advisor is the person or entity responsible for managing one or more clients.

---

## `model_portfolios`
This table stores investment model portfolios.

### Columns
- `model_portfolio_id` — primary key
- `model_name` — model portfolio name

### Role in the model
A model portfolio represents a target allocation strategy, such as Conservative or Growth.

---

## `clients`
This table stores client records.

### Columns
- `client_id` — primary key
- `client_name` — client name
- `advisor_id` — foreign key to `advisors.advisor_id`
- `model_portfolio_id` — foreign key to `model_portfolios.model_portfolio_id`

### Role in the model
A client belongs to an advisor and can be associated with a model portfolio.

This table is the central hub of the schema because it connects to:
- advisors
- model portfolios
- subscriptions
- holdings
- trades

---

## `instruments`
This table stores tradable assets.

### Columns
- `instrument_id` — primary key
- `instrument_name` — unique instrument name
- `ticker` — unique ticker symbol
- `currency` — currency used for pricing

### Role in the model
An instrument is a security such as a stock, ETF, or bond fund.

The uniqueness constraints on `instrument_name` and `ticker` help prevent duplicate instrument records.

---

## `model_portfolio_holdings`
This is a join table between model portfolios and instruments.

### Columns
- `model_portfolio_id` — primary key and foreign key to `model_portfolios`
- `instrument_id` — primary key and foreign key to `instruments`
- `target_weight_pct` — target allocation percentage

### Role in the model
This table defines the intended composition of a model portfolio.

For example:
- one portfolio may contain several instruments
- each instrument may appear in several portfolios
- the `target_weight_pct` column stores the business data for that relationship

Because this table contains its own value (`target_weight_pct`), it is more than just a simple linking table.

---

## `client_subscriptions`
This is a join table between clients and model portfolios.

### Columns
- `client_id` — primary key and foreign key to `clients`
- `model_portfolio_id` — primary key and foreign key to `model_portfolios`
- `subscribed_date` — date the client subscribed

### Role in the model
This table records which model portfolio a client subscribed to and when.

It represents a many-to-many business relationship:
- one client can subscribe to many model portfolios over time
- one model portfolio can have many subscribed clients

The subscription date makes this relationship historically meaningful.

---

## `client_holdings`
This table stores the current holdings for each client and instrument.

### Columns
- `client_id` — primary key and foreign key to `clients`
- `instrument_id` — primary key and foreign key to `instruments`
- `quantity` — number of units held
- `as_of_date` — date the holding snapshot applies to

### Role in the model
This table shows what a client actually owns.

It is used for:
- current portfolio state
- reporting
- drift calculations
- rebalancing logic

Like the other relationship tables, it uses a composite key because a client can hold many instruments and each instrument can be held by many clients.

---

## `client_trades`
This table stores trade activity.

### Columns
- `trade_id` — primary key
- `client_id` — foreign key to `clients`
- `instrument_id` — foreign key to `instruments`
- `trade_type` — must be `BUY` or `SELL`
- `quantity` — number of units traded
- `price` — trade price
- `trade_date` — date of the trade

### Role in the model
This table records transactions that affect a client’s holdings.

It is the activity log of the system:
- buys increase exposure
- sells decrease exposure
- each trade belongs to one client and one instrument

The check constraint on `trade_type` ensures only valid trade directions are allowed.

---

# Relationship explanations

## `advisors ||--o{ clients`
One advisor can have many clients.

### Why this exists
Clients are assigned to an advisor, so the `clients` table stores `advisor_id` as a foreign key.

### Business meaning
This represents an advisor’s book of business.

---

## `model_portfolios ||--o{ clients`
One model portfolio can be associated with many clients.

### Why this exists
The `clients` table stores `model_portfolio_id` as a foreign key.

### Business meaning
Clients can be grouped by a shared portfolio strategy.

---

## `model_portfolios ||--o{ model_portfolio_holdings`
One model portfolio contains many holding rows.

### Why this exists
Each row in `model_portfolio_holdings` describes one instrument inside one portfolio.

### Business meaning
This defines the target allocation of a model portfolio.

---

## `instruments ||--o{ model_portfolio_holdings`
One instrument can appear in many model portfolios.

### Why this exists
The same ETF or stock may be used in multiple strategies.

### Business meaning
The instrument is reusable across different allocation models.

---

## `clients ||--o{ client_subscriptions`
One client can have many subscription rows.

### Why this exists
The schema allows the system to track subscription history.

### Business meaning
A client may subscribe to different model portfolios over time.

---

## `model_portfolios ||--o{ client_subscriptions`
One model portfolio can have many subscribed clients.

### Why this exists
Many clients can follow the same model.

### Business meaning
This is the portfolio’s subscriber list.

---

## `clients ||--o{ client_holdings`
One client can have many holding rows.

### Why this exists
A client can own multiple instruments.

### Business meaning
This is the client’s actual portfolio position list.

---

## `instruments ||--o{ client_holdings`
One instrument can be held by many clients.

### Why this exists
Many clients can own the same stock or ETF.

### Business meaning
This is the asset side of the client’s portfolio positions.

---

## `clients ||--o{ client_trades`
One client can place many trades.

### Why this exists
Each trade belongs to a single client.

### Business meaning
This is the client’s transaction history.

---

## `instruments ||--o{ client_trades`
One instrument can appear in many trades.

### Why this exists
Different clients can trade the same instrument many times.

### Business meaning
This is the instrument side of trade activity.

---

# Why some tables are join tables

A join table is used when two entities have a relationship that needs extra information or when the relationship is many-to-many.

In this schema, the join tables are:
- `model_portfolio_holdings`
- `client_subscriptions`
- `client_holdings`

These tables are important because they do more than connect two IDs. They also store business data like:
- `target_weight_pct`
- `subscribed_date`
- `quantity`
- `as_of_date`

That extra data is why these tables deserve their own ERD boxes instead of being treated as invisible links.

---

# Why the ERD is useful

The ERD helps answer questions like:
- Which client belongs to which advisor?
- Which model portfolio does a client follow?
- Which instruments belong to a model portfolio?
- What is the client currently holding?
- What trades has the client made?

This is especially useful for understanding business flows such as:
1. assign a client to an advisor
2. subscribe the client to a model portfolio
3. compare target holdings vs actual holdings
4. create trades to rebalance the portfolio

---

# Summary

This ERD represents a portfolio-management style database with:
- advisors managing clients
- clients linked to model portfolios
- model portfolios made of target instrument holdings
- clients holding real instruments
- clients placing trades

The diagram is database-first and mirrors the SQL schema closely, so it should be a good reference for anyone reading or implementing the system.


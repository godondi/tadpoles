-- Revised schema for a Spring Boot application with JWT, Spring Security, admin dashboard support, and audit/history tracking.
-- This extends the original portfolio-management model with explicit user, role, token, and audit tables.

CREATE TABLE users (
    user_id         SERIAL PRIMARY KEY,
    username        TEXT NOT NULL UNIQUE,
    email           TEXT UNIQUE,
    password_hash   TEXT NOT NULL,
    display_name    TEXT NOT NULL,
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE roles (
    role_id     SERIAL PRIMARY KEY,
    role_name   TEXT NOT NULL UNIQUE
);

CREATE TABLE user_roles (
    user_id     INTEGER NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    role_id     INTEGER NOT NULL REFERENCES roles(role_id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE advisors (
    advisor_id      SERIAL PRIMARY KEY,
    advisor_name    TEXT NOT NULL,
    user_id         INTEGER UNIQUE REFERENCES users(user_id)
);

CREATE TABLE model_portfolios (
    model_portfolio_id  SERIAL PRIMARY KEY,
    model_name          TEXT NOT NULL,
    description         TEXT,
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_by_user_id  INTEGER REFERENCES users(user_id),
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE clients (
    client_id           SERIAL PRIMARY KEY,
    client_name         TEXT NOT NULL,
    advisor_id          INTEGER REFERENCES advisors(advisor_id),
    model_portfolio_id  INTEGER REFERENCES model_portfolios(model_portfolio_id),
    created_by_user_id  INTEGER REFERENCES users(user_id),
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    cash_balance        NUMERIC(18,2) NOT NULL DEFAULT 0.00
);

CREATE TABLE instruments (
    instrument_id       SERIAL PRIMARY KEY,
    instrument_name     TEXT NOT NULL UNIQUE,
    ticker              TEXT NOT NULL UNIQUE,
    currency            TEXT NOT NULL,
    asset_class         TEXT,
    security_type       TEXT,
    is_active           BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE model_portfolio_holdings (
    model_portfolio_id  INTEGER NOT NULL REFERENCES model_portfolios(model_portfolio_id),
    instrument_id       INTEGER NOT NULL REFERENCES instruments(instrument_id),
    target_weight_pct   NUMERIC(5,2) NOT NULL CHECK (target_weight_pct BETWEEN 0 AND 100),
    PRIMARY KEY (model_portfolio_id, instrument_id)
);

CREATE TABLE client_subscriptions (
    subscription_id         SERIAL PRIMARY KEY,
    client_id               INTEGER NOT NULL REFERENCES clients(client_id),
    model_portfolio_id      INTEGER NOT NULL REFERENCES model_portfolios(model_portfolio_id),
    subscribed_date         DATE NOT NULL,
    ended_date              DATE,
    status                  TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'PAUSED', 'ENDED')),
    approved_by_user_id     INTEGER REFERENCES users(user_id)
);

CREATE TABLE trade_suggestions (
    suggestion_id       SERIAL PRIMARY KEY,
    advisor_id          INTEGER NOT NULL REFERENCES advisors(advisor_id),
    client_id           INTEGER NOT NULL REFERENCES clients(client_id),
    instrument_id       INTEGER NOT NULL REFERENCES instruments(instrument_id),
    trade_type          TEXT NOT NULL CHECK (trade_type IN ('BUY', 'SELL')),
    quantity            NUMERIC(18,6) NOT NULL CHECK (quantity > 0),
    proposed_price      NUMERIC(10,2),
    suggested_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status              TEXT NOT NULL DEFAULT 'SUGGESTED' CHECK (status IN ('SUGGESTED', 'VIEWED', 'ACCEPTED', 'REJECTED', 'EXPIRED')),
    notes               TEXT
);

CREATE TABLE client_trades (
    trade_id                SERIAL PRIMARY KEY,
    client_id               INTEGER NOT NULL REFERENCES clients(client_id),
    instrument_id           INTEGER NOT NULL REFERENCES instruments(instrument_id),
    submitted_by_user_id    INTEGER REFERENCES users(user_id),
    approved_by_user_id     INTEGER REFERENCES users(user_id),
    trade_type              TEXT NOT NULL CHECK (trade_type IN ('BUY', 'SELL')),
    quantity                NUMERIC(18,6) NOT NULL CHECK (quantity > 0),
    price                   NUMERIC(10,2) NOT NULL CHECK (price > 0),
    trade_date              DATE NOT NULL,
    status                  TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'EXECUTED')),
    executed_at             TIMESTAMP,
    reason                  TEXT
);

CREATE TABLE client_holdings (
    holding_id      SERIAL PRIMARY KEY,
    client_id       INTEGER NOT NULL REFERENCES clients(client_id),
    instrument_id   INTEGER NOT NULL REFERENCES instruments(instrument_id),
    quantity        NUMERIC(18,6) NOT NULL CHECK (quantity > 0),
    as_of_date      DATE NOT NULL,
    UNIQUE (client_id, instrument_id, as_of_date)
);

CREATE TABLE refresh_tokens (
    refresh_token_id    SERIAL PRIMARY KEY,
    user_id             INTEGER NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    token_hash          TEXT NOT NULL UNIQUE,
    expires_at          TIMESTAMP NOT NULL,
    revoked_at          TIMESTAMP,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE audit_logs (
    audit_log_id    SERIAL PRIMARY KEY,
    user_id         INTEGER REFERENCES users(user_id),
    entity_name     TEXT NOT NULL,
    entity_id       TEXT,
    action_type     TEXT NOT NULL,
    old_values      JSONB,
    new_values      JSONB,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);


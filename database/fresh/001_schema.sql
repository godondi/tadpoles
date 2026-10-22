-- Current empty-database snapshot, including migrations 003 through 010.
-- Load before 002_reference.sql. Never run this DDL over existing tables.
-- Derived from the ordered legacy upgrade chain; FreshTradingSchemaTest checks parity.
-- Sections below follow pg_dump dependency order: functions, tables/sequences,
-- defaults, constraints/indexes, triggers, then foreign keys. No demo balances.

SET statement_timeout = 0;

SET lock_timeout = 0;

SET idle_in_transaction_session_timeout = 0;

SET client_encoding = 'UTF8';

SET standard_conforming_strings = on;

SELECT
    pg_catalog.set_config('search_path', '', false);

SET check_function_bodies = false;

SET xmloption = content;

SET client_min_messages = warning;

SET row_security = off;

--
-- Name: guard_client_cash(); Type: FUNCTION; Schema: public; Owner: -
--
CREATE FUNCTION public.guard_client_cash ()
    RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        IF NEW.cash_balance <> 0 THEN
            RAISE EXCEPTION 'New accounts start at zero; use controlled cash movements';
        END IF;
    ELSIF NEW.cash_balance IS DISTINCT FROM OLD.cash_balance THEN
        RAISE EXCEPTION 'Historical scalar cash is read-only; use the journal-backed USD account';
    END IF;
    RETURN NEW;
END
$$;

--
-- Name: guard_instrument_currency(); Type: FUNCTION; Schema: public; Owner: -
--
CREATE FUNCTION public.guard_instrument_currency ()
    RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF (NEW.currency,
        NEW.ticker) IS DISTINCT FROM (OLD.currency,
    OLD.ticker) THEN
        RAISE EXCEPTION 'Economic instrument identity is immutable; create a replacement';
    END IF;
    RETURN NEW;
END
$$;

--
-- Name: guard_legacy_financial_write(); Type: FUNCTION; Schema: public; Owner: -
--
CREATE FUNCTION public.guard_legacy_financial_write ()
    RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF TG_TABLE_NAME = 'client_trades' AND TG_OP <> 'DELETE' AND NEW.record_kind = 'SELF_DIRECTED' THEN
        RETURN NEW;
    END IF;
    RAISE EXCEPTION 'Historical financial records are read-only; use the self-directed trading service';
END
$$;

--
-- Name: guard_order_intent(); Type: FUNCTION; Schema: public; Owner: -
--
CREATE FUNCTION public.guard_order_intent ()
    RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF TG_OP = 'DELETE' AND OLD.record_kind = 'SELF_DIRECTED' THEN
        RAISE EXCEPTION 'Order intent is immutable';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    IF OLD.record_kind = 'SELF_DIRECTED' AND ((to_jsonb (NEW) - ARRAY['status', 'updated_at',
    'version', 'rejection_stage', 'rejection_code', 'reason']) IS DISTINCT FROM (to_jsonb (OLD)
    - ARRAY['status', 'updated_at', 'version', 'rejection_stage', 'rejection_code',
    'reason']) OR OLD.status IN ('FILLED', 'REJECTED')) THEN
        RAISE EXCEPTION 'Order intent or terminal outcome is immutable';
    END IF;
    RETURN NEW;
END
$$;

--
-- Name: guard_outbox_fact(); Type: FUNCTION; Schema: public; Owner: -
--
CREATE FUNCTION public.guard_outbox_fact ()
    RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Committed outbox facts cannot be deleted';
    END IF;
    IF (to_jsonb (NEW) - ARRAY['delivered_at', 'attempts']) IS DISTINCT FROM (to_jsonb (OLD) -
    ARRAY['delivered_at', 'attempts']) THEN
        RAISE EXCEPTION 'Committed outbox facts are immutable';
    END IF;
    RETURN NEW;
END
$$;

--
-- Name: guard_provider_identity(); Type: FUNCTION; Schema: public; Owner: -
--
CREATE FUNCTION public.guard_provider_identity ()
    RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF OLD.provider_symbol IS NOT NULL AND NEW.provider_symbol IS DISTINCT FROM OLD.provider_symbol THEN
        RAISE EXCEPTION 'Provider identity is immutable; create a replacement instrument';
    END IF;
    RETURN NEW;
END
$$;

--
-- Name: guard_reservation(); Type: FUNCTION; Schema: public; Owner: -
--
CREATE FUNCTION public.guard_reservation ()
    RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF TG_OP = 'DELETE' OR OLD.state <> 'ACTIVE' THEN
        RAISE EXCEPTION 'Reservation outcome is immutable';
    END IF;
    IF (to_jsonb (NEW) - ARRAY['state', 'ended_at']) IS DISTINCT FROM (to_jsonb (OLD) -
    ARRAY['state', 'ended_at']) THEN
        RAISE EXCEPTION 'Reservation amount is immutable';
    END IF;
    RETURN NEW;
END
$$;

--
-- Name: reject_evidence_change(); Type: FUNCTION; Schema: public; Owner: -
--
CREATE FUNCTION public.reject_evidence_change ()
    RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    RAISE EXCEPTION 'Trading evidence is append-only';
END
$$;

--
-- Name: require_lifecycle_evidence(); Type: FUNCTION; Schema: public; Owner: -
--
CREATE FUNCTION public.require_lifecycle_evidence ()
    RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF NEW.record_kind = 'SELF_DIRECTED' AND NOT EXISTS (
        SELECT
            1
        FROM
            order_events
        WHERE
            order_id = NEW.trade_id AND sequence =
                NEW.version AND to_state = NEW.status) THEN
        RAISE EXCEPTION 'Order state must have matching committed lifecycle evidence';
    END IF;
    IF NEW.record_kind = 'SELF_DIRECTED' AND NEW.status = 'FILLED' AND NOT EXISTS (
        SELECT
            1
        FROM
            order_fills
        WHERE
            order_id = NEW.trade_id) THEN
        RAISE EXCEPTION 'Filled order must have an immutable fill';
    END IF;
    RETURN NULL;
END
$$;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: advisors; Type: TABLE; Schema: public; Owner: -
--
CREATE TABLE public.advisors (
    advisor_id integer NOT NULL,
    advisor_name text NOT NULL,
    user_id integer
);

--
-- Name: advisors_advisor_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--
CREATE SEQUENCE public.advisors_advisor_id_seq
    AS integer START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: advisors_advisor_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--
ALTER SEQUENCE public.advisors_advisor_id_seq OWNED BY public.advisors.advisor_id;

--
-- Name: audit_logs; Type: TABLE; Schema: public; Owner: -
--
CREATE TABLE public.audit_logs (
    audit_log_id integer NOT NULL,
    user_id integer,
    entity_name text NOT NULL,
    entity_id text,
    action_type text NOT NULL,
    old_values jsonb,
    new_values jsonb,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);

--
-- Name: audit_logs_audit_log_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--
CREATE SEQUENCE public.audit_logs_audit_log_id_seq
    AS integer START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: audit_logs_audit_log_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--
ALTER SEQUENCE public.audit_logs_audit_log_id_seq OWNED BY public.audit_logs.audit_log_id;

--
-- Name: auth_sessions; Type: TABLE; Schema: public; Owner: -
--
CREATE TABLE public.auth_sessions (
    session_id uuid NOT NULL,
    user_id integer NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    revoked_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL
);

--
-- Name: bank_references; Type: TABLE; Schema: public; Owner: -
--
CREATE TABLE public.bank_references (
    bank_id uuid NOT NULL,
    client_id integer NOT NULL,
    nickname text NOT NULL,
    encrypted_details text NOT NULL,
    last_four text NOT NULL,
    is_default boolean DEFAULT false NOT NULL,
    active boolean DEFAULT true NOT NULL,
    version bigint DEFAULT 1 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT bank_references_check CHECK ((active OR (NOT is_default))),
    CONSTRAINT bank_references_last_four_check CHECK ((last_four ~ '^[0-9]{4}$'::text)),
    CONSTRAINT bank_references_nickname_check CHECK (((length(nickname) >= 2) AND (length(nickname) <= 50)))
);

--
-- Name: cash_journal; Type: TABLE; Schema: public; Owner: -
--
CREATE TABLE public.cash_journal (
    posting_id uuid NOT NULL,
    client_id integer NOT NULL,
    currency text NOT NULL,
    delta numeric(28, 12) NOT NULL,
    fill_id uuid,
    setup_reference text,
    posting_group uuid NOT NULL,
    actor text NOT NULL,
    recorded_at timestamp with time zone NOT NULL,
    movement_id uuid,
    CONSTRAINT cash_journal_source CHECK ((num_nonnulls (fill_id, setup_reference, movement_id) = 1))
);

--
-- Name: cash_movement_outbox; Type: TABLE; Schema: public; Owner: -
--
CREATE TABLE public.cash_movement_outbox (
    event_id uuid NOT NULL,
    movement_id uuid NOT NULL,
    event_type text NOT NULL,
    schema_version integer DEFAULT 1 NOT NULL,
    payload jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    delivered_at timestamp with time zone,
    attempts integer DEFAULT 0 NOT NULL
);

--
-- Name: cash_movements; Type: TABLE; Schema: public; Owner: -
--
CREATE TABLE public.cash_movements (
    movement_id uuid NOT NULL,
    client_id integer NOT NULL,
    bank_id uuid NOT NULL,
    request_key text NOT NULL,
    request_hash text NOT NULL,
    direction text NOT NULL,
    currency text DEFAULT 'USD' ::text NOT NULL,
    amount numeric NOT NULL,
    status text NOT NULL,
    reason text,
    bank_snapshot text NOT NULL,
    posting_group uuid NOT NULL,
    actor text NOT NULL,
    recorded_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT cash_movements_amount_check CHECK (((amount > (0)::numeric) AND (amount <= (10000)::numeric)
    AND (scale (amount) <= 2))),
    CONSTRAINT cash_movements_currency_check CHECK ((currency = 'USD'::text)),
    CONSTRAINT cash_movements_direction_check CHECK ((direction = ANY (ARRAY['DEPOSIT'::text,
    'WITHDRAW'::text]))),
    CONSTRAINT cash_movements_status_check CHECK ((status = ANY (ARRAY['SETTLED'::text, 'REJECTED'::text])))
);

--
-- Name: client_cash_accounts; Type: TABLE; Schema: public; Owner: -
--
CREATE TABLE public.client_cash_accounts (
    client_id integer NOT NULL,
    currency text NOT NULL,
    posted numeric(28, 12) DEFAULT 0 NOT NULL,
    reserved numeric(28, 12) DEFAULT 0 NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    available_for_trading boolean DEFAULT true NOT NULL,
    CONSTRAINT client_cash_accounts_check CHECK (((posted >= reserved) AND (reserved >= (0)::numeric)))
);

--
-- Name: client_holdings; Type: TABLE; Schema: public; Owner: -
--
CREATE TABLE public.client_holdings (
    holding_id integer NOT NULL,
    client_id integer NOT NULL,
    instrument_id integer NOT NULL,
    quantity numeric(18, 6) NOT NULL,
    as_of_date date NOT NULL,
    CONSTRAINT client_holdings_quantity_check CHECK ((quantity > (0)::numeric))
);

--
-- Name: client_holdings_holding_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--
CREATE SEQUENCE public.client_holdings_holding_id_seq
    AS integer START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: client_holdings_holding_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--
ALTER SEQUENCE public.client_holdings_holding_id_seq OWNED BY public.client_holdings.holding_id;

--
-- Name: client_positions; Type: TABLE; Schema: public; Owner: -
--
CREATE TABLE public.client_positions (
    client_id integer NOT NULL,
    instrument_id integer NOT NULL,
    posted numeric(28, 12) DEFAULT 0 NOT NULL,
    reserved numeric(28, 12) DEFAULT 0 NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    available_for_trading boolean DEFAULT true NOT NULL,
    CONSTRAINT client_positions_check CHECK (((posted >= reserved) AND (reserved >= (0)::numeric)))
);

--
-- Name: client_profiles; Type: TABLE; Schema: public; Owner: -
--
CREATE TABLE public.client_profiles (
    client_profile_id integer NOT NULL,
    user_id integer NOT NULL,
    client_id integer,
    phone text,
    date_of_birth date,
    address_line_1 text,
    address_line_2 text,
    city text,
    state text,
    postal_code text,
    country text,
    employment_status text,
    net_worth numeric(18, 2),
    risk_tolerance text,
    investment_objective text,
    preferred_contact_method text,
    paperless_statements boolean DEFAULT true NOT NULL,
    marketing_opt_in boolean DEFAULT false NOT NULL,
    onboarding_complete boolean DEFAULT false NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);

--
-- Name: client_profiles_client_profile_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--
CREATE SEQUENCE public.client_profiles_client_profile_id_seq
    AS integer START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: client_profiles_client_profile_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--
ALTER SEQUENCE public.client_profiles_client_profile_id_seq OWNED BY public.client_profiles.client_profile_id;

--
-- Name: client_subscriptions; Type: TABLE; Schema: public; Owner: -
--
CREATE TABLE public.client_subscriptions (
    subscription_id integer NOT NULL,
    client_id integer NOT NULL,
    model_portfolio_id integer NOT NULL,
    subscribed_date date NOT NULL,
    ended_date date,
    status text DEFAULT 'ACTIVE' ::text NOT NULL,
    approved_by_user_id integer,
    CONSTRAINT client_subscriptions_status_check CHECK ((status = ANY (ARRAY['ACTIVE'::text,
    'PAUSED'::text, 'ENDED'::text])))
);

--
-- Name: client_subscriptions_subscription_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--
CREATE SEQUENCE public.client_subscriptions_subscription_id_seq
    AS integer START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: client_subscriptions_subscription_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--
ALTER SEQUENCE public.client_subscriptions_subscription_id_seq OWNED BY public.client_subscriptions.subscription_id;

--
-- Name: client_trades; Type: TABLE; Schema: public; Owner: -
--
CREATE TABLE public.client_trades (
    trade_id integer NOT NULL,
    client_id integer NOT NULL,
    instrument_id integer NOT NULL,
    submitted_by_user_id integer,
    approved_by_user_id integer,
    trade_type text NOT NULL,
    quantity numeric(28, 12) NOT NULL,
    price numeric(10, 2),
    trade_date date NOT NULL,
    status text DEFAULT 'PENDING' ::text NOT NULL,
    executed_at timestamp without time zone,
    reason text,
    record_kind text DEFAULT 'LEGACY' ::text NOT NULL,
    idempotency_key text,
    request_hash text,
    submitted_at timestamp with time zone,
    accepted_at timestamp with time zone,
    updated_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL,
    order_type text,
    currency_snapshot text,
    policy_snapshot jsonb,
    rejection_stage text,
    rejection_code text,
    CONSTRAINT client_trades_lifecycle_check CHECK ((((record_kind = 'LEGACY'::text) AND (status = ANY
    (ARRAY['PENDING'::text, 'APPROVED'::text, 'REJECTED'::text, 'EXECUTED'::text]))
    AND (price IS NOT NULL)) OR ((record_kind = 'SELF_DIRECTED'::text) AND (status = ANY
    (ARRAY['ACCEPTED'::text, 'EXECUTING'::text, 'FILLED'::text, 'REJECTED'::text]))
    AND (price IS NULL)))),
    CONSTRAINT client_trades_price_check CHECK ((price > (0)::numeric)),
    CONSTRAINT client_trades_quantity_check CHECK ((quantity > (0)::numeric)),
    CONSTRAINT client_trades_record_kind_check CHECK ((record_kind = ANY (ARRAY['LEGACY'::text,
    'SELF_DIRECTED'::text]))),
    CONSTRAINT client_trades_trade_type_check CHECK ((trade_type = ANY (ARRAY['BUY'::text,
    'SELL'::text]))),
    CONSTRAINT self_directed_intent CHECK (((record_kind = 'LEGACY'::text) OR ((idempotency_key IS NOT
    NULL) AND (request_hash IS NOT NULL) AND (submitted_at IS NOT NULL) AND (updated_at IS NOT NULL) AND
    (order_type = 'MARKET'::text) AND (submitted_by_user_id IS NOT NULL) AND ((status =
    'REJECTED'::text) OR (accepted_at IS NOT NULL)))))
);

--
-- Name: client_trades_trade_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--
CREATE SEQUENCE public.client_trades_trade_id_seq
    AS integer START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: client_trades_trade_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--
ALTER SEQUENCE public.client_trades_trade_id_seq OWNED BY public.client_trades.trade_id;

--
-- Name: clients; Type: TABLE; Schema: public; Owner: -
--
CREATE TABLE public.clients (
    client_id integer NOT NULL,
    user_id integer,
    client_name text NOT NULL,
    advisor_id integer,
    model_portfolio_id integer,
    created_by_user_id integer,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    cash_balance numeric(18, 2) DEFAULT 0.00 NOT NULL,
    event_cursor bigint DEFAULT 0 NOT NULL
);

--
-- Name: clients_client_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--
CREATE SEQUENCE public.clients_client_id_seq
    AS integer START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: clients_client_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--
ALTER SEQUENCE public.clients_client_id_seq OWNED BY public.clients.client_id;

--
-- Name: financial_migration_exceptions; Type: TABLE; Schema: public; Owner: -
--
CREATE TABLE public.financial_migration_exceptions (
    exception_id bigint NOT NULL,
    source_reference text NOT NULL,
    kind text NOT NULL,
    client_id integer,
    instrument_id integer,
    facts jsonb NOT NULL,
    recorded_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: financial_migration_exceptions_exception_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--
CREATE SEQUENCE public.financial_migration_exceptions_exception_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: financial_migration_exceptions_exception_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--
ALTER SEQUENCE public.financial_migration_exceptions_exception_id_seq OWNED BY
    public.financial_migration_exceptions.exception_id;

--
-- Name: instrument_policy_audit; Type: TABLE; Schema: public; Owner: -
--
CREATE TABLE public.instrument_policy_audit (
    audit_id uuid NOT NULL,
    instrument_id integer NOT NULL,
    actor integer,
    reason text NOT NULL,
    before_values jsonb,
    after_values jsonb NOT NULL,
    recorded_at timestamp with time zone DEFAULT now() NOT NULL,
    actor_type text DEFAULT 'USER' ::text NOT NULL,
    CONSTRAINT instrument_policy_audit_actor_type_check CHECK ((actor_type = ANY
    (ARRAY['USER'::text, 'SYSTEM'::text])))
);

--
-- Name: instrument_trading_policies; Type: TABLE; Schema: public; Owner: -
--
CREATE TABLE public.instrument_trading_policies (
    instrument_id integer NOT NULL,
    policy_version text NOT NULL,
    venue text NOT NULL,
    quantity_unit text NOT NULL,
    price_unit text NOT NULL,
    currency text NOT NULL,
    quantity_step numeric(28, 12) NOT NULL,
    currency_scale integer NOT NULL,
    max_quantity numeric(28, 12) NOT NULL,
    max_notional numeric(28, 12) NOT NULL,
    quote_max_age_seconds integer NOT NULL,
    future_skew_seconds integer NOT NULL,
    retry_seconds integer NOT NULL,
    max_attempts integer NOT NULL,
    session text NOT NULL,
    enabled boolean DEFAULT false NOT NULL,
    raw_price_unit text NOT NULL,
    price_multiplier numeric(28, 12) NOT NULL,
    provider_symbol text,
    buy_allowed boolean DEFAULT true NOT NULL,
    sell_allowed boolean DEFAULT true NOT NULL,
    validated_at timestamp with time zone,
    revision bigint DEFAULT 1 NOT NULL,
    CONSTRAINT instrument_trading_policies_currency_scale_check CHECK (((currency_scale >= 0) AND (currency_scale <= 12))),
    CONSTRAINT instrument_trading_policies_future_skew_seconds_check CHECK (((future_skew_seconds >= 0) AND
    (future_skew_seconds <= 30))),
    CONSTRAINT instrument_trading_policies_max_attempts_check CHECK (((max_attempts >= 1) AND (max_attempts <= 10))),
    CONSTRAINT instrument_trading_policies_max_notional_check CHECK ((max_notional > (0)::numeric)),
    CONSTRAINT instrument_trading_policies_max_quantity_check CHECK ((max_quantity > (0)::numeric)),
    CONSTRAINT instrument_trading_policies_price_multiplier_check CHECK ((price_multiplier > (0)::numeric)),
    CONSTRAINT instrument_trading_policies_quantity_step_check CHECK ((quantity_step > (0)::numeric)),
    CONSTRAINT instrument_trading_policies_quote_max_age_seconds_check CHECK (((quote_max_age_seconds >= 1)
    AND (quote_max_age_seconds <= 1200))),
    CONSTRAINT instrument_trading_policies_retry_seconds_check CHECK (((retry_seconds >= 1) AND (retry_seconds <= 3600))),
    CONSTRAINT provider_session CHECK ((session = ANY (ARRAY['PROVIDER_OPEN'::text, 'CLOSED'::text])))
);

--
-- Name: instruments; Type: TABLE; Schema: public; Owner: -
--
CREATE TABLE public.instruments (
    instrument_id integer NOT NULL,
    instrument_name text NOT NULL,
    ticker text NOT NULL,
    currency text NOT NULL,
    asset_class text,
    security_type text,
    is_active boolean DEFAULT true NOT NULL
);

--
-- Name: instruments_instrument_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--
CREATE SEQUENCE public.instruments_instrument_id_seq
    AS integer START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: instruments_instrument_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--
ALTER SEQUENCE public.instruments_instrument_id_seq OWNED BY public.instruments.instrument_id;

--
-- Name: model_portfolio_holdings; Type: TABLE; Schema: public; Owner: -
--
CREATE TABLE public.model_portfolio_holdings (
    model_portfolio_id integer NOT NULL,
    instrument_id integer NOT NULL,
    target_weight_pct numeric(5, 2) NOT NULL,
    CONSTRAINT model_portfolio_holdings_target_weight_pct_check CHECK (((target_weight_pct >= (0)::numeric)
    AND (target_weight_pct <= (100)::numeric)))
);

--
-- Name: model_portfolios; Type: TABLE; Schema: public; Owner: -
--
CREATE TABLE public.model_portfolios (
    model_portfolio_id integer NOT NULL,
    model_name text NOT NULL,
    description text,
    is_active boolean DEFAULT true NOT NULL,
    created_by_user_id integer,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);

--
-- Name: model_portfolios_model_portfolio_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--
CREATE SEQUENCE public.model_portfolios_model_portfolio_id_seq
    AS integer START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: model_portfolios_model_portfolio_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--
ALTER SEQUENCE public.model_portfolios_model_portfolio_id_seq OWNED BY public.model_portfolios.model_portfolio_id;

--
-- Name: order_events; Type: TABLE; Schema: public; Owner: -
--
CREATE TABLE public.order_events (
    event_id uuid NOT NULL,
    order_id integer NOT NULL,
    client_id integer NOT NULL,
    cursor bigint NOT NULL,
    sequence bigint
        NOT NULL,
        from_state text,
        to_state text NOT NULL,
        reason text NOT NULL,
        actor text NOT NULL,
        quote_id uuid,
        fill_id uuid,
        occurred_at timestamp with time zone NOT NULL
);

--
-- Name: order_execution_attempts; Type: TABLE; Schema: public; Owner: -
--
CREATE TABLE public.order_execution_attempts (
    attempt_id uuid NOT NULL,
    order_id integer NOT NULL,
    attempt integer NOT NULL,
    outcome text NOT NULL,
    reason text NOT NULL,
    quote_id uuid,
    recorded_at timestamp with time zone NOT NULL
);

--
-- Name: order_execution_jobs; Type: TABLE; Schema: public; Owner: -
--
CREATE TABLE public.order_execution_jobs (
    order_id integer NOT NULL,
    claim_token uuid,
    lease_until timestamp with time zone,
    attempts integer DEFAULT 0 NOT NULL,
    next_attempt timestamp with time zone NOT NULL,
    deadline timestamp with time zone NOT NULL,
    completed boolean DEFAULT false NOT NULL
);

--
-- Name: order_fills; Type: TABLE; Schema: public; Owner: -
--
CREATE TABLE public.order_fills (
    fill_id uuid NOT NULL,
    order_id integer NOT NULL,
    execution_reference text NOT NULL,
    quote_id uuid NOT NULL,
    quantity numeric(28, 12) NOT NULL,
    price numeric(28, 12) NOT NULL,
    gross numeric(28, 12) NOT NULL,
    fee numeric(28, 12) NOT NULL,
    currency text NOT NULL,
    posting_group uuid NOT NULL,
    executed_at timestamp with time zone NOT NULL,
    booked_at timestamp with time zone NOT NULL,
    CONSTRAINT order_fills_fee_check CHECK ((fee = (0)::numeric)),
    CONSTRAINT order_fills_gross_check CHECK ((gross > (0)::numeric)),
    CONSTRAINT order_fills_price_check CHECK ((price > (0)::numeric)),
    CONSTRAINT order_fills_quantity_check CHECK ((quantity > (0)::numeric))
);

--
-- Name: order_quote_observations; Type: TABLE; Schema: public; Owner: -
--
CREATE TABLE public.order_quote_observations (
    quote_id uuid NOT NULL,
    order_id integer NOT NULL,
    stage text NOT NULL,
    facts jsonb NOT NULL,
    recorded_at timestamp with time zone NOT NULL
);

--
-- Name: order_reservations; Type: TABLE; Schema: public; Owner: -
--
CREATE TABLE public.order_reservations (
    order_id integer NOT NULL,
    resource text NOT NULL,
    amount numeric(28, 12) NOT NULL,
    state text NOT NULL,
    created_at timestamp with time zone NOT NULL,
    ended_at timestamp with time zone,
    CONSTRAINT order_reservations_amount_check CHECK ((amount > (0)::numeric)),
    CONSTRAINT order_reservations_check CHECK (((state = 'ACTIVE'::text) = (ended_at IS NULL))),
    CONSTRAINT order_reservations_resource_check CHECK ((resource = ANY (ARRAY['CASH'::text,
    'UNITS'::text]))),
    CONSTRAINT order_reservations_state_check CHECK ((state = ANY (ARRAY['ACTIVE'::text,
    'CONSUMED'::text, 'RELEASED'::text])))
);

--
-- Name: order_rule_decisions; Type: TABLE; Schema: public; Owner: -
--
CREATE TABLE public.order_rule_decisions (
    decision_id uuid NOT NULL,
    order_id integer NOT NULL,
    stage text NOT NULL,
    policy_version text NOT NULL,
    passed boolean NOT NULL,
    code text NOT NULL,
    explanation text NOT NULL,
    quote_id uuid,
    recorded_at timestamp with time zone NOT NULL
);

--
-- Name: outbox_events; Type: TABLE; Schema: public; Owner: -
--
CREATE TABLE public.outbox_events (
    event_id uuid NOT NULL,
    order_id integer NOT NULL,
    version bigint NOT NULL,
    event_type text NOT NULL,
    schema_version integer DEFAULT 1 NOT NULL,
    payload jsonb NOT NULL,
    created_at timestamp with time zone NOT NULL,
    delivered_at timestamp with time zone,
    attempts integer DEFAULT 0 NOT NULL
);

--
-- Name: position_journal; Type: TABLE; Schema: public; Owner: -
--
CREATE TABLE public.position_journal (
    posting_id uuid NOT NULL,
    client_id integer NOT NULL,
    instrument_id integer NOT NULL,
    delta numeric(28, 12) NOT NULL,
    fill_id uuid,
    setup_reference text,
    posting_group uuid NOT NULL,
    actor text NOT NULL,
    recorded_at timestamp with time zone NOT NULL,
    CONSTRAINT position_journal_check CHECK (((fill_id IS NULL) <> (setup_reference IS NULL)))
);

--
-- Name: provider_setup_checks; Type: TABLE; Schema: public; Owner: -
--
CREATE TABLE public.provider_setup_checks (
    check_id uuid NOT NULL,
    symbol text NOT NULL,
    checked_at timestamp with time zone NOT NULL,
    result text NOT NULL,
    detail text NOT NULL
);

--
-- Name: reconciliation_runs; Type: TABLE; Schema: public; Owner: -
--
CREATE TABLE public.reconciliation_runs (
    run_id uuid NOT NULL,
    actor integer NOT NULL,
    checked_at timestamp with time zone NOT NULL,
    matched boolean NOT NULL,
    report jsonb NOT NULL
);

--
-- Name: refresh_tokens; Type: TABLE; Schema: public; Owner: -
--
CREATE TABLE public.refresh_tokens (
    refresh_token_id integer NOT NULL,
    user_id integer NOT NULL,
    token_hash text NOT NULL,
    expires_at timestamp without time zone NOT NULL,
    revoked_at timestamp without time zone,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);

--
-- Name: refresh_tokens_refresh_token_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--
CREATE SEQUENCE public.refresh_tokens_refresh_token_id_seq
    AS integer START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: refresh_tokens_refresh_token_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--
ALTER SEQUENCE public.refresh_tokens_refresh_token_id_seq OWNED BY public.refresh_tokens.refresh_token_id;

--
-- Name: roles; Type: TABLE; Schema: public; Owner: -
--
CREATE TABLE public.roles (
    role_id integer NOT NULL,
    role_name text NOT NULL
);

--
-- Name: roles_role_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--
CREATE SEQUENCE public.roles_role_id_seq
    AS integer START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: roles_role_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--
ALTER SEQUENCE public.roles_role_id_seq OWNED BY public.roles.role_id;

--
-- Name: schema_migrations; Type: TABLE; Schema: public; Owner: -
--
CREATE TABLE public.schema_migrations (
    version integer NOT NULL,
    applied_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: trade_suggestions; Type: TABLE; Schema: public; Owner: -
--
CREATE TABLE public.trade_suggestions (
    suggestion_id integer NOT NULL,
    advisor_id integer NOT NULL,
    client_id integer NOT NULL,
    instrument_id integer NOT NULL,
    trade_type text NOT NULL,
    quantity numeric(18, 6) NOT NULL,
    proposed_price numeric(10, 2),
    suggested_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    status text DEFAULT 'SUGGESTED' ::text NOT NULL,
    notes text,
    CONSTRAINT trade_suggestions_quantity_check CHECK ((quantity > (0)::numeric)),
    CONSTRAINT trade_suggestions_status_check CHECK ((status = ANY (ARRAY['SUGGESTED'::text,
    'VIEWED'::text, 'ACCEPTED'::text, 'REJECTED'::text, 'EXPIRED'::text]))),
    CONSTRAINT trade_suggestions_trade_type_check CHECK ((trade_type = ANY (ARRAY['BUY'::text,
    'SELL'::text])))
);

--
-- Name: trade_suggestions_suggestion_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--
CREATE SEQUENCE public.trade_suggestions_suggestion_id_seq
    AS integer START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: trade_suggestions_suggestion_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--
ALTER SEQUENCE public.trade_suggestions_suggestion_id_seq OWNED BY public.trade_suggestions.suggestion_id;

--
-- Name: user_roles; Type: TABLE; Schema: public; Owner: -
--
CREATE TABLE public.user_roles (
    user_id integer NOT NULL,
    role_id integer NOT NULL
);

--
-- Name: users; Type: TABLE; Schema: public; Owner: -
--
CREATE TABLE public.users (
    user_id integer NOT NULL,
    username text NOT NULL,
    email text,
    password_hash text NOT NULL,
    display_name text NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);

--
-- Name: users_user_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--
CREATE SEQUENCE public.users_user_id_seq
    AS integer START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: users_user_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--
ALTER SEQUENCE public.users_user_id_seq OWNED BY public.users.user_id;

--
-- Name: advisors advisor_id; Type: DEFAULT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.advisors
    ALTER COLUMN advisor_id SET DEFAULT nextval('public.advisors_advisor_id_seq'::regclass);

--
-- Name: audit_logs audit_log_id; Type: DEFAULT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.audit_logs
    ALTER COLUMN audit_log_id SET DEFAULT nextval('public.audit_logs_audit_log_id_seq'::regclass);

--
-- Name: client_holdings holding_id; Type: DEFAULT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.client_holdings
    ALTER COLUMN holding_id SET DEFAULT nextval('public.client_holdings_holding_id_seq'::regclass);

--
-- Name: client_profiles client_profile_id; Type: DEFAULT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.client_profiles
    ALTER COLUMN client_profile_id SET DEFAULT nextval('public.client_profiles_client_profile_id_seq'::regclass);

--
-- Name: client_subscriptions subscription_id; Type: DEFAULT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.client_subscriptions
    ALTER COLUMN subscription_id SET DEFAULT nextval('public.client_subscriptions_subscription_id_seq'::regclass);

--
-- Name: client_trades trade_id; Type: DEFAULT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.client_trades
    ALTER COLUMN trade_id SET DEFAULT nextval('public.client_trades_trade_id_seq'::regclass);

--
-- Name: clients client_id; Type: DEFAULT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.clients
    ALTER COLUMN client_id SET DEFAULT nextval('public.clients_client_id_seq'::regclass);

--
-- Name: financial_migration_exceptions exception_id; Type: DEFAULT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.financial_migration_exceptions
    ALTER COLUMN exception_id SET DEFAULT nextval('public.financial_migration_exceptions_exception_id_seq'::regclass);

--
-- Name: instruments instrument_id; Type: DEFAULT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.instruments
    ALTER COLUMN instrument_id SET DEFAULT nextval('public.instruments_instrument_id_seq'::regclass);

--
-- Name: model_portfolios model_portfolio_id; Type: DEFAULT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.model_portfolios
    ALTER COLUMN model_portfolio_id SET DEFAULT nextval('public.model_portfolios_model_portfolio_id_seq'::regclass);

--
-- Name: refresh_tokens refresh_token_id; Type: DEFAULT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.refresh_tokens
    ALTER COLUMN refresh_token_id SET DEFAULT nextval('public.refresh_tokens_refresh_token_id_seq'::regclass);

--
-- Name: roles role_id; Type: DEFAULT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.roles
    ALTER COLUMN role_id SET DEFAULT nextval('public.roles_role_id_seq'::regclass);

--
-- Name: trade_suggestions suggestion_id; Type: DEFAULT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.trade_suggestions
    ALTER COLUMN suggestion_id SET DEFAULT nextval('public.trade_suggestions_suggestion_id_seq'::regclass);

--
-- Name: users user_id; Type: DEFAULT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.users
    ALTER COLUMN user_id SET DEFAULT nextval('public.users_user_id_seq'::regclass);

--
-- Name: advisors advisors_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.advisors
    ADD CONSTRAINT advisors_pkey PRIMARY KEY (advisor_id);

--
-- Name: advisors advisors_user_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.advisors
    ADD CONSTRAINT advisors_user_id_key UNIQUE (user_id);

--
-- Name: audit_logs audit_logs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.audit_logs
    ADD CONSTRAINT audit_logs_pkey PRIMARY KEY (audit_log_id);

--
-- Name: auth_sessions auth_sessions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.auth_sessions
    ADD CONSTRAINT auth_sessions_pkey PRIMARY KEY (session_id);

--
-- Name: bank_references bank_references_bank_id_client_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.bank_references
    ADD CONSTRAINT bank_references_bank_id_client_id_key UNIQUE (bank_id, client_id);

--
-- Name: bank_references bank_references_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.bank_references
    ADD CONSTRAINT bank_references_pkey PRIMARY KEY (bank_id);

--
-- Name: cash_journal cash_journal_fill_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.cash_journal
    ADD CONSTRAINT cash_journal_fill_id_key UNIQUE (fill_id);

--
-- Name: cash_journal cash_journal_movement_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.cash_journal
    ADD CONSTRAINT cash_journal_movement_id_key UNIQUE (movement_id);

--
-- Name: cash_journal cash_journal_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.cash_journal
    ADD CONSTRAINT cash_journal_pkey PRIMARY KEY (posting_id);

--
-- Name: cash_journal cash_journal_setup_reference_key; Type: CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.cash_journal
    ADD CONSTRAINT cash_journal_setup_reference_key UNIQUE (setup_reference);

--
-- Name: cash_movement_outbox cash_movement_outbox_movement_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.cash_movement_outbox
    ADD CONSTRAINT cash_movement_outbox_movement_id_key UNIQUE (movement_id);

--
-- Name: cash_movement_outbox cash_movement_outbox_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.cash_movement_outbox
    ADD CONSTRAINT cash_movement_outbox_pkey PRIMARY KEY (event_id);

--
-- Name: cash_movements cash_movements_client_id_request_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.cash_movements
    ADD CONSTRAINT cash_movements_client_id_request_key_key UNIQUE (client_id, request_key);

--
-- Name: cash_movements cash_movements_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.cash_movements
    ADD CONSTRAINT cash_movements_pkey PRIMARY KEY (movement_id);

--
-- Name: cash_movements cash_movements_posting_group_key; Type: CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.cash_movements
    ADD CONSTRAINT cash_movements_posting_group_key UNIQUE (posting_group);

--
-- Name: client_cash_accounts client_cash_accounts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.client_cash_accounts
    ADD CONSTRAINT client_cash_accounts_pkey PRIMARY KEY (client_id, currency);

--
-- Name: client_holdings client_holdings_client_id_instrument_id_as_of_date_key; Type: CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.client_holdings
    ADD CONSTRAINT client_holdings_client_id_instrument_id_as_of_date_key UNIQUE (client_id, instrument_id, as_of_date);

--
-- Name: client_holdings client_holdings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.client_holdings
    ADD CONSTRAINT client_holdings_pkey PRIMARY KEY (holding_id);

--
-- Name: client_positions client_positions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.client_positions
    ADD CONSTRAINT client_positions_pkey PRIMARY KEY (client_id, instrument_id);

--
-- Name: client_profiles client_profiles_client_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.client_profiles
    ADD CONSTRAINT client_profiles_client_id_key UNIQUE (client_id);

--
-- Name: client_profiles client_profiles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.client_profiles
    ADD CONSTRAINT client_profiles_pkey PRIMARY KEY (client_profile_id);

--
-- Name: client_profiles client_profiles_user_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.client_profiles
    ADD CONSTRAINT client_profiles_user_id_key UNIQUE (user_id);

--
-- Name: client_subscriptions client_subscriptions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.client_subscriptions
    ADD CONSTRAINT client_subscriptions_pkey PRIMARY KEY (subscription_id);

--
-- Name: client_trades client_trades_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.client_trades
    ADD CONSTRAINT client_trades_pkey PRIMARY KEY (trade_id);

--
-- Name: clients clients_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.clients
    ADD CONSTRAINT clients_pkey PRIMARY KEY (client_id);

--
-- Name: clients clients_user_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.clients
    ADD CONSTRAINT clients_user_id_key UNIQUE (user_id);

--
-- Name: financial_migration_exceptions financial_migration_exceptions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.financial_migration_exceptions
    ADD CONSTRAINT financial_migration_exceptions_pkey PRIMARY KEY (exception_id);

--
-- Name: financial_migration_exceptions financial_migration_exceptions_source_reference_key; Type: CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.financial_migration_exceptions
    ADD CONSTRAINT financial_migration_exceptions_source_reference_key UNIQUE (source_reference);

--
-- Name: instrument_policy_audit instrument_policy_audit_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.instrument_policy_audit
    ADD CONSTRAINT instrument_policy_audit_pkey PRIMARY KEY (audit_id);

--
-- Name: instrument_trading_policies instrument_trading_policies_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.instrument_trading_policies
    ADD CONSTRAINT instrument_trading_policies_pkey PRIMARY KEY (instrument_id);

--
-- Name: instruments instruments_instrument_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.instruments
    ADD CONSTRAINT instruments_instrument_name_key UNIQUE (instrument_name);

--
-- Name: instruments instruments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.instruments
    ADD CONSTRAINT instruments_pkey PRIMARY KEY (instrument_id);

--
-- Name: instruments instruments_ticker_key; Type: CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.instruments
    ADD CONSTRAINT instruments_ticker_key UNIQUE (ticker);

--
-- Name: model_portfolio_holdings model_portfolio_holdings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.model_portfolio_holdings
    ADD CONSTRAINT model_portfolio_holdings_pkey PRIMARY KEY (model_portfolio_id, instrument_id);

--
-- Name: model_portfolios model_portfolios_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.model_portfolios
    ADD CONSTRAINT model_portfolios_pkey PRIMARY KEY (model_portfolio_id);

--
-- Name: order_events order_events_client_id_cursor_key; Type: CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.order_events
    ADD CONSTRAINT order_events_client_id_cursor_key UNIQUE (client_id, cursor);

--
-- Name: order_events order_events_order_id_sequence_key; Type: CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.order_events
    ADD CONSTRAINT order_events_order_id_sequence_key UNIQUE (order_id, sequence);

--
-- Name: order_events order_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.order_events
    ADD CONSTRAINT order_events_pkey PRIMARY KEY (event_id);

--
-- Name: order_execution_attempts order_execution_attempts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.order_execution_attempts
    ADD CONSTRAINT order_execution_attempts_pkey PRIMARY KEY (attempt_id);

--
-- Name: order_execution_jobs order_execution_jobs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.order_execution_jobs
    ADD CONSTRAINT order_execution_jobs_pkey PRIMARY KEY (order_id);

--
-- Name: order_fills order_fills_execution_reference_key; Type: CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.order_fills
    ADD CONSTRAINT order_fills_execution_reference_key UNIQUE (execution_reference);

--
-- Name: order_fills order_fills_order_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.order_fills
    ADD CONSTRAINT order_fills_order_id_key UNIQUE (order_id);

--
-- Name: order_fills order_fills_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.order_fills
    ADD CONSTRAINT order_fills_pkey PRIMARY KEY (fill_id);

--
-- Name: order_fills order_fills_posting_group_key; Type: CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.order_fills
    ADD CONSTRAINT order_fills_posting_group_key UNIQUE (posting_group);

--
-- Name: order_quote_observations order_quote_observations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.order_quote_observations
    ADD CONSTRAINT order_quote_observations_pkey PRIMARY KEY (quote_id);

--
-- Name: order_reservations order_reservations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.order_reservations
    ADD CONSTRAINT order_reservations_pkey PRIMARY KEY (order_id);

--
-- Name: order_rule_decisions order_rule_decisions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.order_rule_decisions
    ADD CONSTRAINT order_rule_decisions_pkey PRIMARY KEY (decision_id);

--
-- Name: outbox_events outbox_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.outbox_events
    ADD CONSTRAINT outbox_events_pkey PRIMARY KEY (event_id);

--
-- Name: position_journal position_journal_fill_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.position_journal
    ADD CONSTRAINT position_journal_fill_id_key UNIQUE (fill_id);

--
-- Name: position_journal position_journal_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.position_journal
    ADD CONSTRAINT position_journal_pkey PRIMARY KEY (posting_id);

--
-- Name: position_journal position_journal_setup_reference_key; Type: CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.position_journal
    ADD CONSTRAINT position_journal_setup_reference_key UNIQUE (setup_reference);

--
-- Name: provider_setup_checks provider_setup_checks_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.provider_setup_checks
    ADD CONSTRAINT provider_setup_checks_pkey PRIMARY KEY (check_id);

--
-- Name: reconciliation_runs reconciliation_runs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.reconciliation_runs
    ADD CONSTRAINT reconciliation_runs_pkey PRIMARY KEY (run_id);

--
-- Name: refresh_tokens refresh_tokens_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT refresh_tokens_pkey PRIMARY KEY (refresh_token_id);

--
-- Name: refresh_tokens refresh_tokens_token_hash_key; Type: CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT refresh_tokens_token_hash_key UNIQUE (token_hash);

--
-- Name: roles roles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.roles
    ADD CONSTRAINT roles_pkey PRIMARY KEY (role_id);

--
-- Name: roles roles_role_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.roles
    ADD CONSTRAINT roles_role_name_key UNIQUE (role_name);

--
-- Name: schema_migrations schema_migrations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.schema_migrations
    ADD CONSTRAINT schema_migrations_pkey PRIMARY KEY (version);

--
-- Name: trade_suggestions trade_suggestions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.trade_suggestions
    ADD CONSTRAINT trade_suggestions_pkey PRIMARY KEY (suggestion_id);

--
-- Name: user_roles user_roles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT user_roles_pkey PRIMARY KEY (user_id, role_id);

--
-- Name: users users_email_key; Type: CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_email_key UNIQUE (email);

--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (user_id);

--
-- Name: users users_username_key; Type: CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_username_key UNIQUE (username);

--
-- Name: bank_reference_default; Type: INDEX; Schema: public; Owner: -
--
CREATE UNIQUE INDEX bank_reference_default ON public.bank_references USING btree (client_id)
WHERE (active AND is_default);

--
-- Name: bank_reference_nickname; Type: INDEX; Schema: public; Owner: -
--
CREATE UNIQUE INDEX bank_reference_nickname ON public.bank_references USING btree (client_id, lower(nickname))
WHERE
    active;

--
-- Name: cash_movement_history; Type: INDEX; Schema: public; Owner: -
--
CREATE INDEX cash_movement_history ON public.cash_movements USING btree (client_id, recorded_at DESC, movement_id);

--
-- Name: client_holdings_current_idx; Type: INDEX; Schema: public; Owner: -
--
CREATE INDEX client_holdings_current_idx ON public.client_holdings USING btree (client_id, instrument_id,
    as_of_date DESC, holding_id DESC);

--
-- Name: client_trades_client_date_id_idx; Type: INDEX; Schema: public; Owner: -
--
CREATE INDEX client_trades_client_date_id_idx ON public.client_trades USING btree (client_id, trade_date
    DESC, trade_id DESC);

--
-- Name: order_request_key; Type: INDEX; Schema: public; Owner: -
--
CREATE UNIQUE INDEX order_request_key ON public.client_trades USING btree (client_id, idempotency_key)
WHERE (record_kind = 'SELF_DIRECTED'::text);

--
-- Name: ready_execution_jobs; Type: INDEX; Schema: public; Owner: -
--
CREATE INDEX ready_execution_jobs ON public.order_execution_jobs USING btree (next_attempt)
WHERE (NOT completed);

--
-- Name: clients guard_client_cash; Type: TRIGGER; Schema: public; Owner: -
--
CREATE TRIGGER guard_client_cash
    BEFORE INSERT OR UPDATE ON public.clients
    FOR EACH ROW
    EXECUTE FUNCTION public.guard_client_cash ();

--
-- Name: instruments guard_instrument_currency; Type: TRIGGER; Schema: public; Owner: -
--
CREATE TRIGGER guard_instrument_currency
    BEFORE UPDATE ON public.instruments
    FOR EACH ROW
    EXECUTE FUNCTION public.guard_instrument_currency ();

--
-- Name: client_holdings guard_legacy_holding; Type: TRIGGER; Schema: public; Owner: -
--
CREATE TRIGGER guard_legacy_holding
    BEFORE INSERT OR DELETE OR UPDATE ON public.client_holdings
    FOR EACH ROW
    EXECUTE FUNCTION public.guard_legacy_financial_write ();

--
-- Name: client_trades guard_legacy_trade; Type: TRIGGER; Schema: public; Owner: -
--
CREATE TRIGGER guard_legacy_trade
    BEFORE INSERT OR DELETE OR UPDATE ON public.client_trades
    FOR EACH ROW
    EXECUTE FUNCTION public.guard_legacy_financial_write ();

--
-- Name: client_trades guard_order_intent; Type: TRIGGER; Schema: public; Owner: -
--
CREATE TRIGGER guard_order_intent
    BEFORE DELETE OR UPDATE ON public.client_trades
    FOR EACH ROW
    EXECUTE FUNCTION public.guard_order_intent ();

--
-- Name: outbox_events guard_outbox_fact; Type: TRIGGER; Schema: public; Owner: -
--
CREATE TRIGGER guard_outbox_fact
    BEFORE DELETE OR UPDATE ON public.outbox_events
    FOR EACH ROW
    EXECUTE FUNCTION public.guard_outbox_fact ();

--
-- Name: instrument_trading_policies guard_provider_identity; Type: TRIGGER; Schema: public; Owner: -
--
CREATE TRIGGER guard_provider_identity
    BEFORE UPDATE ON public.instrument_trading_policies
    FOR EACH ROW
    EXECUTE FUNCTION public.guard_provider_identity ();

--
-- Name: order_reservations guard_reservation; Type: TRIGGER; Schema: public; Owner: -
--
CREATE TRIGGER guard_reservation
    BEFORE DELETE OR UPDATE ON public.order_reservations
    FOR EACH ROW
    EXECUTE FUNCTION public.guard_reservation ();

--
-- Name: cash_journal immutable_evidence; Type: TRIGGER; Schema: public; Owner: -
--
CREATE TRIGGER immutable_evidence
    BEFORE DELETE OR UPDATE ON public.cash_journal
    FOR EACH ROW
    EXECUTE FUNCTION public.reject_evidence_change ();

--
-- Name: cash_movements immutable_evidence; Type: TRIGGER; Schema: public; Owner: -
--
CREATE TRIGGER immutable_evidence
    BEFORE DELETE OR UPDATE ON public.cash_movements
    FOR EACH ROW
    EXECUTE FUNCTION public.reject_evidence_change ();

--
-- Name: financial_migration_exceptions immutable_evidence; Type: TRIGGER; Schema: public; Owner: -
--
CREATE TRIGGER immutable_evidence
    BEFORE DELETE OR UPDATE ON public.financial_migration_exceptions
    FOR EACH ROW
    EXECUTE FUNCTION public.reject_evidence_change ();

--
-- Name: instrument_policy_audit immutable_evidence; Type: TRIGGER; Schema: public; Owner: -
--
CREATE TRIGGER immutable_evidence
    BEFORE DELETE OR UPDATE ON public.instrument_policy_audit
    FOR EACH ROW
    EXECUTE FUNCTION public.reject_evidence_change ();

--
-- Name: order_events immutable_evidence; Type: TRIGGER; Schema: public; Owner: -
--
CREATE TRIGGER immutable_evidence
    BEFORE DELETE OR UPDATE ON public.order_events
    FOR EACH ROW
    EXECUTE FUNCTION public.reject_evidence_change ();

--
-- Name: order_execution_attempts immutable_evidence; Type: TRIGGER; Schema: public; Owner: -
--
CREATE TRIGGER immutable_evidence
    BEFORE DELETE OR UPDATE ON public.order_execution_attempts
    FOR EACH ROW
    EXECUTE FUNCTION public.reject_evidence_change ();

--
-- Name: order_fills immutable_evidence; Type: TRIGGER; Schema: public; Owner: -
--
CREATE TRIGGER immutable_evidence
    BEFORE DELETE OR UPDATE ON public.order_fills
    FOR EACH ROW
    EXECUTE FUNCTION public.reject_evidence_change ();

--
-- Name: order_quote_observations immutable_evidence; Type: TRIGGER; Schema: public; Owner: -
--
CREATE TRIGGER immutable_evidence
    BEFORE DELETE OR UPDATE ON public.order_quote_observations
    FOR EACH ROW
    EXECUTE FUNCTION public.reject_evidence_change ();

--
-- Name: order_rule_decisions immutable_evidence; Type: TRIGGER; Schema: public; Owner: -
--
CREATE TRIGGER immutable_evidence
    BEFORE DELETE OR UPDATE ON public.order_rule_decisions
    FOR EACH ROW
    EXECUTE FUNCTION public.reject_evidence_change ();

--
-- Name: position_journal immutable_evidence; Type: TRIGGER; Schema: public; Owner: -
--
CREATE TRIGGER immutable_evidence
    BEFORE DELETE OR UPDATE ON public.position_journal
    FOR EACH ROW
    EXECUTE FUNCTION public.reject_evidence_change ();

--
-- Name: provider_setup_checks immutable_evidence; Type: TRIGGER; Schema: public; Owner: -
--
CREATE TRIGGER immutable_evidence
    BEFORE DELETE OR UPDATE ON public.provider_setup_checks
    FOR EACH ROW
    EXECUTE FUNCTION public.reject_evidence_change ();

--
-- Name: reconciliation_runs immutable_evidence; Type: TRIGGER; Schema: public; Owner: -
--
CREATE TRIGGER immutable_evidence
    BEFORE DELETE OR UPDATE ON public.reconciliation_runs
    FOR EACH ROW
    EXECUTE FUNCTION public.reject_evidence_change ();

--
-- Name: cash_movement_outbox immutable_movement_outbox; Type: TRIGGER; Schema: public; Owner: -
--
CREATE TRIGGER immutable_movement_outbox
    BEFORE DELETE OR UPDATE ON public.cash_movement_outbox
    FOR EACH ROW
    EXECUTE FUNCTION public.guard_outbox_fact ();

--
-- Name: client_trades require_lifecycle_evidence; Type: TRIGGER; Schema: public; Owner: -
--
CREATE CONSTRAINT TRIGGER require_lifecycle_evidence
    AFTER INSERT OR UPDATE ON public.client_trades DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
    EXECUTE FUNCTION public.require_lifecycle_evidence ();

--
-- Name: advisors advisors_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.advisors
    ADD CONSTRAINT advisors_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users (user_id);

--
-- Name: audit_logs audit_logs_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.audit_logs
    ADD CONSTRAINT audit_logs_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users (user_id);

--
-- Name: auth_sessions auth_sessions_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.auth_sessions
    ADD CONSTRAINT auth_sessions_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users (user_id);

--
-- Name: bank_references bank_references_client_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.bank_references
    ADD CONSTRAINT bank_references_client_id_fkey FOREIGN KEY (client_id) REFERENCES public.clients (client_id);

--
-- Name: cash_journal cash_journal_client_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.cash_journal
    ADD CONSTRAINT cash_journal_client_id_fkey FOREIGN KEY (client_id) REFERENCES public.clients (client_id);

--
-- Name: cash_journal cash_journal_fill_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.cash_journal
    ADD CONSTRAINT cash_journal_fill_id_fkey FOREIGN KEY (fill_id) REFERENCES public.order_fills (fill_id);

--
-- Name: cash_journal cash_journal_movement_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.cash_journal
    ADD CONSTRAINT cash_journal_movement_id_fkey FOREIGN KEY (movement_id) REFERENCES public.cash_movements (movement_id);

--
-- Name: cash_movement_outbox cash_movement_outbox_movement_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.cash_movement_outbox
    ADD CONSTRAINT cash_movement_outbox_movement_id_fkey FOREIGN KEY (movement_id) REFERENCES
    public.cash_movements (movement_id);

--
-- Name: cash_movements cash_movements_bank_id_client_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.cash_movements
    ADD CONSTRAINT cash_movements_bank_id_client_id_fkey FOREIGN KEY (bank_id, client_id) REFERENCES
    public.bank_references (bank_id, client_id);

--
-- Name: cash_movements cash_movements_client_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.cash_movements
    ADD CONSTRAINT cash_movements_client_id_fkey FOREIGN KEY (client_id) REFERENCES public.clients (client_id);

--
-- Name: client_cash_accounts client_cash_accounts_client_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.client_cash_accounts
    ADD CONSTRAINT client_cash_accounts_client_id_fkey FOREIGN KEY (client_id) REFERENCES public.clients (client_id);

--
-- Name: client_holdings client_holdings_client_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.client_holdings
    ADD CONSTRAINT client_holdings_client_id_fkey FOREIGN KEY (client_id) REFERENCES public.clients (client_id);

--
-- Name: client_holdings client_holdings_instrument_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.client_holdings
    ADD CONSTRAINT client_holdings_instrument_id_fkey FOREIGN KEY (instrument_id) REFERENCES
    public.instruments (instrument_id);

--
-- Name: client_positions client_positions_client_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.client_positions
    ADD CONSTRAINT client_positions_client_id_fkey FOREIGN KEY (client_id) REFERENCES public.clients (client_id);

--
-- Name: client_positions client_positions_instrument_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.client_positions
    ADD CONSTRAINT client_positions_instrument_id_fkey FOREIGN KEY (instrument_id) REFERENCES
    public.instruments (instrument_id);

--
-- Name: client_profiles client_profiles_client_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.client_profiles
    ADD CONSTRAINT client_profiles_client_id_fkey FOREIGN KEY (client_id) REFERENCES public.clients
    (client_id) ON DELETE CASCADE;

--
-- Name: client_profiles client_profiles_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.client_profiles
    ADD CONSTRAINT client_profiles_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users (user_id) ON DELETE CASCADE;

--
-- Name: client_subscriptions client_subscriptions_approved_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.client_subscriptions
    ADD CONSTRAINT client_subscriptions_approved_by_user_id_fkey FOREIGN KEY (approved_by_user_id) REFERENCES
    public.users (user_id);

--
-- Name: client_subscriptions client_subscriptions_client_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.client_subscriptions
    ADD CONSTRAINT client_subscriptions_client_id_fkey FOREIGN KEY (client_id) REFERENCES public.clients (client_id);

--
-- Name: client_subscriptions client_subscriptions_model_portfolio_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.client_subscriptions
    ADD CONSTRAINT client_subscriptions_model_portfolio_id_fkey FOREIGN KEY (model_portfolio_id) REFERENCES
    public.model_portfolios (model_portfolio_id);

--
-- Name: client_trades client_trades_approved_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.client_trades
    ADD CONSTRAINT client_trades_approved_by_user_id_fkey FOREIGN KEY (approved_by_user_id) REFERENCES
    public.users (user_id);

--
-- Name: client_trades client_trades_client_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.client_trades
    ADD CONSTRAINT client_trades_client_id_fkey FOREIGN KEY (client_id) REFERENCES public.clients (client_id);

--
-- Name: client_trades client_trades_instrument_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.client_trades
    ADD CONSTRAINT client_trades_instrument_id_fkey FOREIGN KEY (instrument_id) REFERENCES public.instruments
    (instrument_id);

--
-- Name: client_trades client_trades_submitted_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.client_trades
    ADD CONSTRAINT client_trades_submitted_by_user_id_fkey FOREIGN KEY (submitted_by_user_id) REFERENCES
    public.users (user_id);

--
-- Name: clients clients_advisor_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.clients
    ADD CONSTRAINT clients_advisor_id_fkey FOREIGN KEY (advisor_id) REFERENCES public.advisors (advisor_id);

--
-- Name: clients clients_created_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.clients
    ADD CONSTRAINT clients_created_by_user_id_fkey FOREIGN KEY (created_by_user_id) REFERENCES public.users (user_id);

--
-- Name: clients clients_model_portfolio_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.clients
    ADD CONSTRAINT clients_model_portfolio_id_fkey FOREIGN KEY (model_portfolio_id) REFERENCES
    public.model_portfolios (model_portfolio_id);

--
-- Name: clients clients_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.clients
    ADD CONSTRAINT clients_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users (user_id);

--
-- Name: financial_migration_exceptions financial_migration_exceptions_client_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.financial_migration_exceptions
    ADD CONSTRAINT financial_migration_exceptions_client_id_fkey FOREIGN KEY (client_id) REFERENCES
    public.clients (client_id);

--
-- Name: financial_migration_exceptions financial_migration_exceptions_instrument_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.financial_migration_exceptions
    ADD CONSTRAINT financial_migration_exceptions_instrument_id_fkey FOREIGN KEY (instrument_id) REFERENCES
    public.instruments (instrument_id);

--
-- Name: instrument_policy_audit instrument_policy_audit_actor_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.instrument_policy_audit
    ADD CONSTRAINT instrument_policy_audit_actor_fkey FOREIGN KEY (actor) REFERENCES public.users (user_id);

--
-- Name: instrument_policy_audit instrument_policy_audit_instrument_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.instrument_policy_audit
    ADD CONSTRAINT instrument_policy_audit_instrument_id_fkey FOREIGN KEY (instrument_id) REFERENCES
    public.instruments (instrument_id);

--
-- Name: instrument_trading_policies instrument_trading_policies_instrument_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.instrument_trading_policies
    ADD CONSTRAINT instrument_trading_policies_instrument_id_fkey FOREIGN KEY (instrument_id) REFERENCES
    public.instruments (instrument_id);

--
-- Name: model_portfolio_holdings model_portfolio_holdings_instrument_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.model_portfolio_holdings
    ADD CONSTRAINT model_portfolio_holdings_instrument_id_fkey FOREIGN KEY (instrument_id) REFERENCES
    public.instruments (instrument_id);

--
-- Name: model_portfolio_holdings model_portfolio_holdings_model_portfolio_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.model_portfolio_holdings
    ADD CONSTRAINT model_portfolio_holdings_model_portfolio_id_fkey FOREIGN KEY (model_portfolio_id)
    REFERENCES public.model_portfolios (model_portfolio_id);

--
-- Name: model_portfolios model_portfolios_created_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.model_portfolios
    ADD CONSTRAINT model_portfolios_created_by_user_id_fkey FOREIGN KEY (created_by_user_id) REFERENCES
    public.users (user_id);

--
-- Name: order_events order_events_client_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.order_events
    ADD CONSTRAINT order_events_client_id_fkey FOREIGN KEY (client_id) REFERENCES public.clients (client_id);

--
-- Name: order_events order_events_fill_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.order_events
    ADD CONSTRAINT order_events_fill_id_fkey FOREIGN KEY (fill_id) REFERENCES public.order_fills (fill_id);

--
-- Name: order_events order_events_order_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.order_events
    ADD CONSTRAINT order_events_order_id_fkey FOREIGN KEY (order_id) REFERENCES public.client_trades (trade_id);

--
-- Name: order_events order_events_quote_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.order_events
    ADD CONSTRAINT order_events_quote_id_fkey FOREIGN KEY (quote_id) REFERENCES public.order_quote_observations (quote_id);

--
-- Name: order_execution_attempts order_execution_attempts_order_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.order_execution_attempts
    ADD CONSTRAINT order_execution_attempts_order_id_fkey FOREIGN KEY (order_id) REFERENCES public.client_trades (trade_id);

--
-- Name: order_execution_attempts order_execution_attempts_quote_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.order_execution_attempts
    ADD CONSTRAINT order_execution_attempts_quote_id_fkey FOREIGN KEY (quote_id) REFERENCES
    public.order_quote_observations (quote_id);

--
-- Name: order_execution_jobs order_execution_jobs_order_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.order_execution_jobs
    ADD CONSTRAINT order_execution_jobs_order_id_fkey FOREIGN KEY (order_id) REFERENCES public.client_trades (trade_id);

--
-- Name: order_fills order_fills_order_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.order_fills
    ADD CONSTRAINT order_fills_order_id_fkey FOREIGN KEY (order_id) REFERENCES public.client_trades (trade_id);

--
-- Name: order_fills order_fills_quote_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.order_fills
    ADD CONSTRAINT order_fills_quote_id_fkey FOREIGN KEY (quote_id) REFERENCES public.order_quote_observations (quote_id);

--
-- Name: order_quote_observations order_quote_observations_order_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.order_quote_observations
    ADD CONSTRAINT order_quote_observations_order_id_fkey FOREIGN KEY (order_id) REFERENCES public.client_trades (trade_id);

--
-- Name: order_reservations order_reservations_order_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.order_reservations
    ADD CONSTRAINT order_reservations_order_id_fkey FOREIGN KEY (order_id) REFERENCES public.client_trades (trade_id);

--
-- Name: order_rule_decisions order_rule_decisions_order_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.order_rule_decisions
    ADD CONSTRAINT order_rule_decisions_order_id_fkey FOREIGN KEY (order_id) REFERENCES public.client_trades (trade_id);

--
-- Name: order_rule_decisions order_rule_decisions_quote_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.order_rule_decisions
    ADD CONSTRAINT order_rule_decisions_quote_id_fkey FOREIGN KEY (quote_id) REFERENCES
    public.order_quote_observations (quote_id);

--
-- Name: outbox_events outbox_events_event_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.outbox_events
    ADD CONSTRAINT outbox_events_event_id_fkey FOREIGN KEY (event_id) REFERENCES public.order_events (event_id);

--
-- Name: outbox_events outbox_events_order_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.outbox_events
    ADD CONSTRAINT outbox_events_order_id_fkey FOREIGN KEY (order_id) REFERENCES public.client_trades (trade_id);

--
-- Name: position_journal position_journal_client_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.position_journal
    ADD CONSTRAINT position_journal_client_id_fkey FOREIGN KEY (client_id) REFERENCES public.clients (client_id);

--
-- Name: position_journal position_journal_fill_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.position_journal
    ADD CONSTRAINT position_journal_fill_id_fkey FOREIGN KEY (fill_id) REFERENCES public.order_fills (fill_id);

--
-- Name: position_journal position_journal_instrument_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.position_journal
    ADD CONSTRAINT position_journal_instrument_id_fkey FOREIGN KEY (instrument_id) REFERENCES
    public.instruments (instrument_id);

--
-- Name: reconciliation_runs reconciliation_runs_actor_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.reconciliation_runs
    ADD CONSTRAINT reconciliation_runs_actor_fkey FOREIGN KEY (actor) REFERENCES public.users (user_id);

--
-- Name: refresh_tokens refresh_tokens_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT refresh_tokens_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users (user_id) ON DELETE CASCADE;

--
-- Name: trade_suggestions trade_suggestions_advisor_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.trade_suggestions
    ADD CONSTRAINT trade_suggestions_advisor_id_fkey FOREIGN KEY (advisor_id) REFERENCES public.advisors (advisor_id);

--
-- Name: trade_suggestions trade_suggestions_client_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.trade_suggestions
    ADD CONSTRAINT trade_suggestions_client_id_fkey FOREIGN KEY (client_id) REFERENCES public.clients (client_id);

--
-- Name: trade_suggestions trade_suggestions_instrument_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.trade_suggestions
    ADD CONSTRAINT trade_suggestions_instrument_id_fkey FOREIGN KEY (instrument_id) REFERENCES
    public.instruments (instrument_id);

--
-- Name: user_roles user_roles_role_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT user_roles_role_id_fkey FOREIGN KEY (role_id) REFERENCES public.roles (role_id) ON DELETE CASCADE;

--
-- Name: user_roles user_roles_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT user_roles_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users (user_id) ON DELETE CASCADE;

--
-- PostgreSQL database dump complete
--

-- Operations investigation schema.
SET search_path = public;
-- Explicit client scopes only. Provision through an authorized database deployment, never staff UI.
CREATE TABLE operations_access_grants (
    grant_id uuid PRIMARY KEY,
    staff_user_id integer NOT NULL REFERENCES users(user_id),
    permission text NOT NULL CHECK (permission IN ('ORDER_READ', 'EVIDENCE_READ', 'RECONCILIATION_READ', 'EVIDENCE_EXPORT')),
    scope_type text NOT NULL DEFAULT 'CLIENT' CHECK (scope_type = 'CLIENT'),
    client_id integer NOT NULL REFERENCES clients(client_id),
    account_reference varchar(80) NOT NULL CHECK (account_reference ~ '^[A-Za-z0-9][A-Za-z0-9._-]{0,79}$'),
    valid_from timestamptz NOT NULL,
    valid_until timestamptz NOT NULL CHECK (valid_until > valid_from),
    approved_by integer NOT NULL REFERENCES users(user_id),
    approval_reference varchar(200) NOT NULL CHECK (length(trim(approval_reference)) >= 8),
    revoked_at timestamptz,
    CHECK (approved_by <> staff_user_id)
);
CREATE INDEX operations_grants_lookup ON operations_access_grants (staff_user_id, permission, client_id, valid_until) WHERE revoked_at IS NULL;
CREATE TABLE operations_grant_events (
    event_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    grant_id uuid NOT NULL REFERENCES operations_access_grants(grant_id),
    database_actor text NOT NULL,
    action text NOT NULL,
    before_values jsonb,
    after_values jsonb NOT NULL,
    recorded_at timestamptz NOT NULL DEFAULT now()
);
CREATE FUNCTION record_operations_grant_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'UPDATE' AND (to_jsonb(NEW) - 'revoked_at') IS DISTINCT FROM (to_jsonb(OLD) - 'revoked_at') THEN
        RAISE EXCEPTION 'Revoke and issue a new grant to change scope';
    END IF;
    IF TG_OP = 'UPDATE' AND (OLD.revoked_at IS NOT NULL OR NEW.revoked_at IS NULL) THEN
        RAISE EXCEPTION 'Grant revocation cannot be reversed';
    END IF;
    INSERT INTO operations_grant_events(grant_id, database_actor, action, before_values, after_values)
    VALUES (NEW.grant_id, current_user, TG_OP, CASE WHEN TG_OP = 'UPDATE' THEN to_jsonb(OLD) END, to_jsonb(NEW));
    RETURN NEW;
END;
$$;
CREATE TRIGGER operations_grant_changes AFTER INSERT OR UPDATE ON operations_access_grants FOR EACH ROW EXECUTE FUNCTION record_operations_grant_change();
CREATE TRIGGER operations_grant_no_delete BEFORE DELETE ON operations_access_grants FOR EACH ROW EXECUTE FUNCTION reject_evidence_change();
CREATE TABLE operations_access_contexts (
    context_id uuid PRIMARY KEY,
    actor integer NOT NULL REFERENCES users(user_id),
    purpose_code varchar(40) NOT NULL,
    case_reference varchar(200) NOT NULL CHECK (length(trim(case_reference)) BETWEEN 8 AND 200),
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL CHECK (expires_at > created_at),
    policy_version varchar(80) NOT NULL
);
CREATE TABLE operations_access_events (
    event_id uuid PRIMARY KEY,
    actor integer NOT NULL REFERENCES users(user_id),
    context_id uuid,
    action varchar(80) NOT NULL,
    target_type varchar(40) NOT NULL,
    target_id varchar(80),
    filter_digest varchar(64) NOT NULL,
    scope_version varchar(64) NOT NULL,
    outcome varchar(40) NOT NULL,
    returned_count integer NOT NULL CHECK (returned_count >= 0),
    recorded_at timestamptz NOT NULL,
    correlation_id uuid NOT NULL,
    export_id uuid
);
CREATE INDEX operations_access_events_actor_time ON operations_access_events(actor, recorded_at);
CREATE TABLE operations_evidence_exports (
    export_id uuid PRIMARY KEY,
    requester integer NOT NULL REFERENCES users(user_id),
    context_id uuid NOT NULL REFERENCES operations_access_contexts(context_id),
    order_id integer NOT NULL REFERENCES client_trades(trade_id),
    idempotency_key varchar(80) NOT NULL,
    request_hash varchar(64) NOT NULL,
    scope_version varchar(64) NOT NULL,
    snapshot_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    order_version bigint NOT NULL,
    digest varchar(64) NOT NULL,
    archive bytea NOT NULL,
    UNIQUE(requester, idempotency_key)
);
CREATE TABLE order_decision_contexts (
    decision_id uuid PRIMARY KEY REFERENCES order_rule_decisions(decision_id),
    schema_version integer NOT NULL CHECK (schema_version = 1),
    facts jsonb NOT NULL,
    recorded_at timestamptz NOT NULL
);
CREATE INDEX operations_order_scope_time ON client_trades(client_id, submitted_at DESC, trade_id DESC) WHERE record_kind = 'SELF_DIRECTED';
CREATE INDEX operations_order_time ON client_trades(submitted_at DESC, trade_id DESC) WHERE record_kind = 'SELF_DIRECTED';
CREATE INDEX operations_order_status_time ON client_trades(status, submitted_at DESC, trade_id DESC) WHERE record_kind = 'SELF_DIRECTED';
CREATE TABLE reconciliation_findings (
    finding_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    run_id uuid NOT NULL REFERENCES reconciliation_runs(run_id),
    kind text NOT NULL,
    client_id integer REFERENCES clients(client_id),
    order_id integer REFERENCES client_trades(trade_id),
    reference text NOT NULL,
    detected_at timestamptz NOT NULL
);
CREATE INDEX operations_reconciliation_scope ON reconciliation_findings(run_id, client_id, finding_id);
CREATE TRIGGER immutable_evidence BEFORE UPDATE OR DELETE ON operations_grant_events FOR EACH ROW EXECUTE FUNCTION reject_evidence_change();
CREATE TRIGGER immutable_evidence BEFORE UPDATE OR DELETE ON operations_access_contexts FOR EACH ROW EXECUTE FUNCTION reject_evidence_change();
CREATE TRIGGER immutable_evidence BEFORE UPDATE OR DELETE ON operations_access_events FOR EACH ROW EXECUTE FUNCTION reject_evidence_change();
CREATE TRIGGER immutable_evidence BEFORE UPDATE OR DELETE ON operations_evidence_exports FOR EACH ROW EXECUTE FUNCTION reject_evidence_change();
CREATE TRIGGER immutable_evidence BEFORE UPDATE OR DELETE ON order_decision_contexts FOR EACH ROW EXECUTE FUNCTION reject_evidence_change();
CREATE TRIGGER immutable_evidence BEFORE UPDATE OR DELETE ON reconciliation_findings FOR EACH ROW EXECUTE FUNCTION reject_evidence_change();

-- Managed support persistence (migration 012).

ALTER TABLE client_trades ADD CONSTRAINT support_order_owner UNIQUE (trade_id, client_id);
CREATE TABLE support_cases (
    case_id uuid PRIMARY KEY,
    reference varchar(41) NOT NULL UNIQUE CHECK (reference ~ '^CASE-[0-9a-f-]{36}$'),
    client_id integer NOT NULL REFERENCES clients(client_id),
    order_id integer,
    category varchar(40) NOT NULL CHECK (category IN ('ORDER_QUESTION','ACCOUNT_REVIEW','HISTORICAL_RECORD','TRADING_AVAILABILITY','OTHER')),
    subject varchar(120) NOT NULL CHECK (length(trim(subject)) BETWEEN 5 AND 120),
    request_key varchar(128) NOT NULL,
    request_hash varchar(64) NOT NULL,
    status varchar(24) NOT NULL CHECK (status IN ('OPEN','IN_REVIEW','AWAITING_CLIENT','RESOLVED')),
    version bigint NOT NULL CHECK (version >= 1),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    UNIQUE (client_id,request_key),
    UNIQUE (case_id,client_id),
    FOREIGN KEY (order_id,client_id) REFERENCES client_trades(trade_id,client_id)
);
CREATE INDEX support_owner_time ON support_cases(client_id,created_at DESC,case_id DESC);
CREATE INDEX support_owner_status ON support_cases(client_id,status,created_at DESC,case_id DESC);
CREATE TABLE support_case_events (
    event_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    case_id uuid NOT NULL,
    client_id integer NOT NULL,
    version bigint NOT NULL,
    kind varchar(24) NOT NULL,
    actor integer NOT NULL REFERENCES users(user_id),
    author_role varchar(16) NOT NULL CHECK (author_role IN ('CLIENT','OPERATIONS')),
    body varchar(4000) NOT NULL CHECK (length(trim(body)) BETWEEN 10 AND 4000),
    previous_status varchar(24),
    status varchar(24) NOT NULL,
    recorded_at timestamptz NOT NULL,
    UNIQUE(case_id,version),
    FOREIGN KEY(case_id,client_id) REFERENCES support_cases(case_id,client_id)
);
CREATE INDEX support_public_updates ON support_case_events(client_id,event_id);
CREATE TRIGGER immutable_evidence BEFORE UPDATE OR DELETE ON support_case_events FOR EACH ROW EXECUTE FUNCTION reject_evidence_change();
CREATE FUNCTION guard_support_case() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN RAISE EXCEPTION 'Support history cannot be deleted'; END IF;
    IF (to_jsonb(NEW) - ARRAY['status','version','updated_at']) IS DISTINCT FROM
       (to_jsonb(OLD) - ARRAY['status','version','updated_at']) THEN
        RAISE EXCEPTION 'Case identity and request are immutable';
    END IF;
    IF NEW.version <> OLD.version + 1 THEN RAISE EXCEPTION 'Case version must advance once'; END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER support_case_guard BEFORE UPDATE OR DELETE ON support_cases FOR EACH ROW EXECUTE FUNCTION guard_support_case();
ALTER TABLE operations_access_grants DROP CONSTRAINT operations_access_grants_permission_check;
ALTER TABLE operations_access_grants ADD CONSTRAINT operations_access_grants_permission_check
    CHECK (permission IN ('ORDER_READ','EVIDENCE_READ','RECONCILIATION_READ','EVIDENCE_EXPORT','CASE_READ','CASE_UPDATE'));

CREATE TABLE support_case_notes (
    note_id uuid PRIMARY KEY,
    case_id uuid NOT NULL REFERENCES support_cases(case_id),
    actor integer NOT NULL REFERENCES users(user_id),
    body varchar(4000) NOT NULL CHECK (length(trim(body)) BETWEEN 10 AND 4000),
    recorded_at timestamptz NOT NULL
);
CREATE INDEX support_notes_page ON support_case_notes(case_id,recorded_at,note_id);
CREATE TABLE support_case_requests (
    case_id uuid NOT NULL REFERENCES support_cases(case_id),
    actor integer NOT NULL REFERENCES users(user_id),
    request_key varchar(128) NOT NULL,
    request_hash varchar(64) NOT NULL,
    receipt jsonb NOT NULL,
    recorded_at timestamptz NOT NULL,
    PRIMARY KEY(case_id,actor,request_key)
);
CREATE TRIGGER immutable_evidence BEFORE UPDATE OR DELETE ON support_case_notes FOR EACH ROW EXECUTE FUNCTION reject_evidence_change();
CREATE TRIGGER immutable_evidence BEFORE UPDATE OR DELETE ON support_case_requests FOR EACH ROW EXECUTE FUNCTION reject_evidence_change();

ALTER TABLE support_cases ADD CONSTRAINT support_case_reference_identity UNIQUE(case_id,reference);
ALTER TABLE operations_access_contexts ADD COLUMN reference_type varchar(16) NOT NULL DEFAULT 'EXTERNAL'
    CHECK (reference_type IN ('EXTERNAL','MANAGED'));
ALTER TABLE operations_access_contexts ADD COLUMN managed_case_id uuid;
ALTER TABLE operations_access_contexts ADD CONSTRAINT managed_context_reference
    FOREIGN KEY(managed_case_id,case_reference) REFERENCES support_cases(case_id,reference);
ALTER TABLE operations_access_contexts ADD CONSTRAINT managed_context_kind
    CHECK ((reference_type='MANAGED') = (managed_case_id IS NOT NULL));
CREATE TABLE support_read_state (
    client_id integer NOT NULL REFERENCES clients(client_id),
    recipient integer NOT NULL REFERENCES users(user_id),
    last_event bigint NOT NULL CHECK(last_event >= 0),
    PRIMARY KEY(client_id,recipient)
);

CREATE TABLE support_case_exports (
    case_id uuid NOT NULL REFERENCES support_cases(case_id),
    export_id uuid NOT NULL REFERENCES operations_evidence_exports(export_id),
    digest varchar(64) NOT NULL,
    actor integer NOT NULL REFERENCES users(user_id),
    recorded_at timestamptz NOT NULL,
    PRIMARY KEY(case_id,export_id)
);
CREATE TRIGGER immutable_evidence BEFORE UPDATE OR DELETE ON support_case_exports FOR EACH ROW EXECUTE FUNCTION reject_evidence_change();

CREATE FUNCTION check_support_projection() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE aggregate support_cases%ROWTYPE;
BEGIN
    SELECT * INTO aggregate FROM support_cases WHERE case_id=NEW.case_id;
    IF NOT FOUND OR (SELECT count(*) FROM support_case_events WHERE case_id=NEW.case_id) <> aggregate.version
       OR NOT EXISTS(SELECT 1 FROM support_case_events WHERE case_id=NEW.case_id
            AND version=aggregate.version AND status=aggregate.status AND recorded_at=aggregate.updated_at) THEN
        RAISE EXCEPTION 'Case projection requires matching retained history';
    END IF;
    RETURN NULL;
END;
$$;
CREATE CONSTRAINT TRIGGER support_projection_consistent AFTER INSERT OR UPDATE ON support_cases
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_support_projection();
CREATE CONSTRAINT TRIGGER support_history_consistent AFTER INSERT ON support_case_events
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_support_projection();
CREATE FUNCTION guard_support_read_state() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS(SELECT 1 FROM clients WHERE client_id=NEW.client_id AND user_id=NEW.recipient)
       OR (NEW.last_event<>0 AND NOT EXISTS(SELECT 1 FROM support_case_events WHERE event_id=NEW.last_event AND client_id=NEW.client_id)) THEN
        RAISE EXCEPTION 'Read watermark must belong to the recipient';
    END IF;
    IF TG_OP='UPDATE' AND (NEW.client_id<>OLD.client_id OR NEW.recipient<>OLD.recipient OR NEW.last_event<OLD.last_event) THEN
        RAISE EXCEPTION 'Read watermark cannot change recipient or move backwards';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER support_read_state_guard BEFORE INSERT OR UPDATE ON support_read_state
    FOR EACH ROW EXECUTE FUNCTION guard_support_read_state();

-- Compliance workspace snapshot; kept in parity with migration 013.
CREATE TABLE compliance_access_grants (
    grant_id uuid PRIMARY KEY,
    staff_user_id integer NOT NULL REFERENCES users(user_id),
    CHECK (staff_user_id > 0),
    permission text NOT NULL CHECK (permission IN ('EVIDENCE_READ','EXPORT_CREATE','EXPORT_DOWNLOAD')),
    scope_type text NOT NULL DEFAULT 'CLIENT' CHECK (scope_type = 'CLIENT'),
    client_id integer NOT NULL REFERENCES clients(client_id) CHECK (client_id > 0),
    account_reference varchar(80) NOT NULL
        CHECK (account_reference ~ '^[A-Za-z0-9][A-Za-z0-9._-]{0,79}$'),
    valid_from timestamptz NOT NULL,
    valid_until timestamptz NOT NULL CHECK (valid_until > valid_from),
    approved_by integer NOT NULL REFERENCES users(user_id),
    approval_reference varchar(200) NOT NULL CHECK (length(trim(approval_reference)) >= 8),
    revoked_at timestamptz,
    CHECK (approved_by <> staff_user_id)
);
CREATE INDEX compliance_grants_lookup
    ON compliance_access_grants(staff_user_id,permission,client_id,valid_until)
    WHERE revoked_at IS NULL;
CREATE TABLE compliance_grant_events (
    event_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    grant_id uuid NOT NULL REFERENCES compliance_access_grants(grant_id),
    database_actor text NOT NULL,
    action text NOT NULL,
    before_values jsonb,
    after_values jsonb NOT NULL,
    recorded_at timestamptz NOT NULL DEFAULT now()
);
CREATE FUNCTION record_compliance_grant_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'UPDATE'
       AND (to_jsonb(NEW) - 'revoked_at') IS DISTINCT FROM (to_jsonb(OLD) - 'revoked_at') THEN
        RAISE EXCEPTION 'Revoke and issue a new Compliance grant to change scope';
    END IF;
    IF TG_OP = 'UPDATE' AND (OLD.revoked_at IS NOT NULL OR NEW.revoked_at IS NULL) THEN
        RAISE EXCEPTION 'Compliance grant revocation cannot be reversed';
    END IF;
    INSERT INTO compliance_grant_events(
        grant_id,database_actor,action,before_values,after_values)
    VALUES(
        NEW.grant_id,current_user,TG_OP,
        CASE WHEN TG_OP='UPDATE' THEN to_jsonb(OLD) END,to_jsonb(NEW));
    RETURN NEW;
END;
$$;
CREATE TRIGGER compliance_grant_changes
    AFTER INSERT OR UPDATE ON compliance_access_grants
    FOR EACH ROW EXECUTE FUNCTION record_compliance_grant_change();
CREATE TRIGGER immutable_evidence
    BEFORE UPDATE OR DELETE ON compliance_grant_events
    FOR EACH ROW EXECUTE FUNCTION reject_evidence_change();
CREATE TRIGGER immutable_evidence
    BEFORE DELETE ON compliance_access_grants
    FOR EACH ROW EXECUTE FUNCTION reject_evidence_change();
CREATE TABLE compliance_access_contexts (
    context_id uuid PRIMARY KEY,
    actor integer NOT NULL REFERENCES users(user_id) CHECK (actor > 0),
    purpose_code varchar(40) NOT NULL CHECK (purpose_code ~ '^[A-Z][A-Z0-9_]{1,39}$'),
    case_reference varchar(200) NOT NULL
        CHECK (case_reference ~ '^[A-Za-z0-9][A-Za-z0-9._:/-]{7,199}$'),
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL CHECK (expires_at > created_at),
    policy_version varchar(80) NOT NULL,
    scope_version varchar(64) NOT NULL CHECK (scope_version ~ '^[0-9a-f]{64}$'),
    revoked_at timestamptz
);
CREATE INDEX compliance_context_actor_time
    ON compliance_access_contexts(actor,expires_at,context_id);
CREATE FUNCTION guard_compliance_context() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Compliance contexts cannot be deleted';
    END IF;
    IF (to_jsonb(NEW) - 'revoked_at') IS DISTINCT FROM
       (to_jsonb(OLD) - 'revoked_at') THEN
        RAISE EXCEPTION 'Compliance context identity is immutable';
    END IF;
    IF OLD.revoked_at IS NOT NULL OR NEW.revoked_at IS NULL THEN
        RAISE EXCEPTION 'Compliance context revocation cannot be reversed';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER compliance_context_guard
    BEFORE UPDATE OR DELETE ON compliance_access_contexts
    FOR EACH ROW EXECUTE FUNCTION guard_compliance_context();
CREATE TABLE compliance_access_events (
    event_id uuid PRIMARY KEY,
    actor integer NOT NULL REFERENCES users(user_id),
    context_id uuid REFERENCES compliance_access_contexts(context_id),
    action varchar(80) NOT NULL,
    target_type varchar(40) NOT NULL,
    target_id varchar(80),
    filter_digest varchar(64) NOT NULL,
    scope_version varchar(64) NOT NULL,
    outcome varchar(40) NOT NULL,
    returned_count integer NOT NULL CHECK (returned_count >= 0),
    recorded_at timestamptz NOT NULL,
    correlation_id uuid NOT NULL,
    export_id uuid
);
CREATE INDEX compliance_access_events_actor_time
    ON compliance_access_events(actor,recorded_at);
CREATE INDEX compliance_access_events_context_time
    ON compliance_access_events(context_id,recorded_at);
CREATE TRIGGER immutable_evidence
    BEFORE UPDATE OR DELETE ON compliance_access_events
    FOR EACH ROW EXECUTE FUNCTION reject_evidence_change();
CREATE TABLE compliance_evidence_exports (
    export_id uuid PRIMARY KEY,
    requester integer NOT NULL REFERENCES users(user_id) CHECK (requester > 0),
    context_id uuid NOT NULL REFERENCES compliance_access_contexts(context_id),
    order_id integer NOT NULL REFERENCES client_trades(trade_id) CHECK (order_id > 0),
    idempotency_key varchar(80) NOT NULL
        CHECK (idempotency_key ~ '^[A-Za-z0-9._-]{8,80}$'),
    request_hash varchar(64) NOT NULL CHECK (request_hash ~ '^[0-9a-f]{64}$'),
    scope_version varchar(64) NOT NULL,
    snapshot_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL CHECK (expires_at > created_at),
    state varchar(16) NOT NULL CHECK (state IN ('REQUESTED','READY','FAILED','EXPIRED','DENIED')),
    digest varchar(64) NOT NULL CHECK (digest ~ '^[0-9a-f]{64}$'),
    manifest jsonb NOT NULL,
    archive bytea NOT NULL,
    UNIQUE(requester,idempotency_key)
);
CREATE INDEX compliance_exports_requester_time
    ON compliance_evidence_exports(requester,created_at DESC,export_id DESC);
CREATE INDEX compliance_exports_order_time
    ON compliance_evidence_exports(order_id,created_at DESC,export_id DESC);
CREATE TRIGGER immutable_evidence
    BEFORE UPDATE OR DELETE ON compliance_evidence_exports
    FOR EACH ROW EXECUTE FUNCTION reject_evidence_change();
INSERT INTO schema_migrations(version) VALUES (13) ON CONFLICT DO NOTHING;
INSERT INTO schema_migrations(version) VALUES (14) ON CONFLICT DO NOTHING;

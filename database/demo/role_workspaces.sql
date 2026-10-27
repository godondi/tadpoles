-- Optional local Compose identities. Requires base identity/client tables.
-- Current startup runs this after migrations; upgrade tests may use it after 004.
-- Repeatable without overwriting existing passwords or refilling spent cash.

BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

INSERT INTO roles (role_name)
    VALUES ('CLIENT'),
    ('TRADING_OPS'),
    ('COMPLIANCE'),
    ('ANALYST'),
    ('FINANCE'),
    ('ADVISOR')
ON CONFLICT (role_name)
    DO NOTHING;

WITH demo_accounts (
    username,
    email,
    display_name
) AS (
    VALUES ('demo.client', 'demo.client@tadpoles.test', 'Demo Client'),
        ('demo.client2', 'demo.client2@tadpoles.test', 'Second Demo Client'),
        ('demo.operations', 'demo.operations@tadpoles.test', 'Demo Operations'),
        ('demo.compliance', 'demo.compliance@tadpoles.test', 'Demo Compliance'),
        ('demo.analyst', 'demo.analyst@tadpoles.test', 'Demo Analyst'),
        ('demo.finance', 'demo.finance@tadpoles.test', 'Demo Finance'),
        ('demo.advisor', 'demo.advisor@tadpoles.test', 'Demo Advisor'))
INSERT INTO users (username, email, password_hash, display_name, enabled)
SELECT
    username,
    email,
    crypt('RoleDemo2026!', gen_salt('bf', 12)),
    display_name,
    TRUE
FROM
    demo_accounts
ON CONFLICT
    DO NOTHING;

WITH demo_roles (
    username,
    email,
    role_name
) AS (
    VALUES ('demo.client', 'demo.client@tadpoles.test', 'CLIENT'),
        ('demo.client2', 'demo.client2@tadpoles.test', 'CLIENT'),
        ('demo.operations', 'demo.operations@tadpoles.test', 'TRADING_OPS'),
        ('demo.compliance', 'demo.compliance@tadpoles.test', 'COMPLIANCE'),
        ('demo.analyst', 'demo.analyst@tadpoles.test', 'ANALYST'),
        ('demo.finance', 'demo.finance@tadpoles.test', 'FINANCE'),
        ('demo.advisor', 'demo.advisor@tadpoles.test', 'ADVISOR'))
INSERT INTO user_roles (user_id, role_id)
SELECT
    users.user_id,
    roles.role_id
FROM
    demo_roles
    JOIN users ON users.username = demo_roles.username
        AND users.email = demo_roles.email
    JOIN roles ON roles.role_name = demo_roles.role_name
ON CONFLICT (user_id,
    role_id)
    DO NOTHING;

INSERT INTO clients (user_id, client_name, cash_balance)
SELECT
    users.user_id,
    users.display_name,
    0.00
FROM
    users
WHERE
    users.username IN ('demo.client', 'demo.client2')
    AND users.email = users.username || '@tadpoles.test'
ON CONFLICT (user_id)
    DO NOTHING;

INSERT INTO client_profiles (user_id, client_id, onboarding_complete)
SELECT
    clients.user_id,
    clients.client_id,
    TRUE
FROM
    clients
    JOIN users ON users.user_id = clients.user_id
WHERE
    users.username IN ('demo.client', 'demo.client2')
    AND users.email = users.username || '@tadpoles.test'
ON CONFLICT (user_id)
    DO NOTHING;

-- A real linked advisor identity, not a browser-side login or ownership bypass.
INSERT INTO advisors (user_id, advisor_name)
SELECT user_id, 'Demo Advisor'
FROM users
WHERE username = 'demo.advisor'
    AND email = 'demo.advisor@tadpoles.test'
ON CONFLICT (user_id)
    DO NOTHING;

-- Only the designated positive demo fixture is assigned. Never silently reassign
-- an existing client, and leave demo.client2 and all other assignments untouched.
DO $$
DECLARE
    demo_advisor_id integer;
    existing_advisor_id integer;
BEGIN
    SELECT a.advisor_id INTO STRICT demo_advisor_id
    FROM advisors a JOIN users u USING (user_id)
    WHERE u.username = 'demo.advisor'
        AND u.email = 'demo.advisor@tadpoles.test';

    SELECT c.advisor_id INTO STRICT existing_advisor_id
    FROM clients c JOIN users u USING (user_id)
    WHERE u.username = 'demo.client'
        AND u.email = 'demo.client@tadpoles.test'
    FOR UPDATE OF c;

    IF existing_advisor_id IS NOT NULL AND existing_advisor_id <> demo_advisor_id THEN
        RAISE EXCEPTION 'demo.client already has a different advisor; preserve the existing assignment and resolve explicitly';
    END IF;

    UPDATE clients c SET advisor_id = demo_advisor_id
    FROM users u
    WHERE c.user_id = u.user_id AND u.username = 'demo.client'
        AND u.email = 'demo.client@tadpoles.test' AND c.advisor_id IS NULL;
END
$$;

-- Demo identities bypass registration, so create their empty ledger accounts explicitly.
-- Never replace balances, grants or quarantine flags on a repeated seed.
DO $$
BEGIN
    IF to_regclass ('public.client_cash_accounts') IS NOT NULL THEN
        INSERT INTO client_cash_accounts (client_id, currency, posted, reserved)
        SELECT
            c.client_id,
            'USD',
            0,
            0
        FROM
            clients c
            JOIN users u USING (user_id)
        WHERE
            u.username IN ('demo.client', 'demo.client2')
            AND u.email = u.username || '@tadpoles.test'
        ON CONFLICT (client_id,
            currency)
            DO NOTHING;
    END IF;
END
$$;

COMMIT;

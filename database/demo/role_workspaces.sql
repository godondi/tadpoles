-- Local Compose debugging accounts. Safe to re-run; existing passwords and roles are not replaced.
BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

INSERT INTO roles (role_name)
VALUES ('CLIENT'), ('TRADING_OPS'), ('COMPLIANCE'), ('ANALYST'), ('FINANCE')
ON CONFLICT (role_name) DO NOTHING;

WITH demo_accounts(username, email, display_name) AS (
    VALUES
        ('demo.client', 'demo.client@tadpoles.test', 'Demo Client'),
        ('demo.client2', 'demo.client2@tadpoles.test', 'Second Demo Client'),
        ('demo.operations', 'demo.operations@tadpoles.test', 'Demo Operations'),
        ('demo.compliance', 'demo.compliance@tadpoles.test', 'Demo Compliance'),
        ('demo.analyst', 'demo.analyst@tadpoles.test', 'Demo Analyst'),
        ('demo.finance', 'demo.finance@tadpoles.test', 'Demo Finance')
)
INSERT INTO users (username, email, password_hash, display_name, enabled)
SELECT username, email, crypt('RoleDemo2026!', gen_salt('bf', 12)), display_name, TRUE
FROM demo_accounts
ON CONFLICT DO NOTHING;

WITH demo_roles(username, email, role_name) AS (
    VALUES
        ('demo.client', 'demo.client@tadpoles.test', 'CLIENT'),
        ('demo.client2', 'demo.client2@tadpoles.test', 'CLIENT'),
        ('demo.operations', 'demo.operations@tadpoles.test', 'TRADING_OPS'),
        ('demo.compliance', 'demo.compliance@tadpoles.test', 'COMPLIANCE'),
        ('demo.analyst', 'demo.analyst@tadpoles.test', 'ANALYST'),
        ('demo.finance', 'demo.finance@tadpoles.test', 'FINANCE')
)
INSERT INTO user_roles (user_id, role_id)
SELECT users.user_id, roles.role_id
FROM demo_roles
JOIN users ON users.username = demo_roles.username AND users.email = demo_roles.email
JOIN roles ON roles.role_name = demo_roles.role_name
ON CONFLICT (user_id, role_id) DO NOTHING;

INSERT INTO clients (user_id, client_name, cash_balance)
SELECT users.user_id, users.display_name, 0.00
FROM users
WHERE users.username IN ('demo.client', 'demo.client2')
  AND users.email = users.username || '@tadpoles.test'
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO client_profiles (user_id, client_id, onboarding_complete)
SELECT clients.user_id, clients.client_id, TRUE
FROM clients
JOIN users ON users.user_id = clients.user_id
WHERE users.username IN ('demo.client', 'demo.client2')
  AND users.email = users.username || '@tadpoles.test'
ON CONFLICT (user_id) DO NOTHING;

COMMIT;

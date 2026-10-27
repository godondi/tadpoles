ALTER TABLE users ADD COLUMN trade_access_revoked_at timestamptz;
ALTER TABLE users ADD COLUMN trade_access_until timestamptz;
ALTER TABLE users ADD COLUMN trade_access_reason text;
ALTER TABLE users ADD CONSTRAINT trade_access_expiry_after_revoke
    CHECK (trade_access_until IS NULL OR trade_access_revoked_at IS NOT NULL);

CREATE INDEX users_trade_access_revoked ON users(user_id)
    WHERE trade_access_revoked_at IS NOT NULL;

CREATE TABLE client_trade_access_events (
    event_id bigserial PRIMARY KEY,
    client_id integer NOT NULL REFERENCES clients(client_id),
    actor_subject text NOT NULL,
    action text NOT NULL CHECK (action IN ('REVOKE_INDEFINITE', 'REVOKE_TIMED', 'RESTORE')),
    revoked_until timestamptz,
    reason text,
    recorded_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX client_trade_access_events_client_time ON client_trade_access_events(client_id, recorded_at DESC);
CREATE TRIGGER immutable_evidence BEFORE UPDATE OR DELETE ON client_trade_access_events
    FOR EACH ROW EXECUTE FUNCTION reject_evidence_change();

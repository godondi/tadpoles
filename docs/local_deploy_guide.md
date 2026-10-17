# Local deployment

The [provider-backed USD trading guide](../impDocs/Frontend/ClientPageImplementation/ClientRefactorCrashCourse.MD) is the maintained deployment and recovery reference.

## Fresh local Compose

Use Docker Compose with Node/Maven builds performed by the project Dockerfiles. Copy `.env.template` only if `.env` does not already exist. Configure the required provider URL/key, database settings, signing key and ports locally; never publish their values. Then run:

```bash
docker compose config --quiet
docker compose up --build -d
```

Fresh databases load `database/fresh/001_schema.sql` followed by `002_reference.sql`. The migration service runs before the local demo seed and backend. Local demo setup creates the unique USD 10,000 opening once and validates the 14-symbol catalog. The normal stack never starts the HTTP test fixture. A recognized symbol still requires a fresh quote and explicitly open provider session before trading.

## Existing volumes

Back up and rehearse restoration first. Halt old admission and drain old jobs/holds before migration. Keep the volume; do not use `down -v`. The versioned migrations preserve history and quarantine unverified historical balances and positions. Follow the guide's exact cutover and rollback sequence. Repeated startup preserves balances and Operations policy edits.

## Retained deployment

Use `docker-compose.hardened.yml` with the base Compose file, a separately provisioned runtime database identity and unique banking/signing secrets. Apply `database/runtime-grants.sql` as the schema owner. The override excludes demo seeding and the auth stub by default. Check Operations readiness and persist a Finance reconciliation after startup. The local default is not a production security configuration.

## Without Docker

Use Java 21, Maven and PostgreSQL 15. For a new empty database, apply the two canonical fresh files in order; optional demo identities are separate in `database/demo/role_workspaces.sql`. Do not apply fresh DDL to an existing schema. Configure the environment variables used by `backend/src/main/resources/application.yml`, then run `mvn -f backend/pom.xml spring-boot:run`. Standalone admission/worker/demo controls default off. Use `npm ci` and `npm start` in `frontend` with the API URL configuration coordinated to the backend port.

## Disposable verification

`bash scripts/test-role-workspaces.sh` runs the backend, real PostgreSQL migration tests, frontend tests, styles and production build. `python3 scripts/test-client-compose.py` verifies fresh/repeated Compose startup and a populated-volume upgrade using isolated projects. `python3 scripts/test-client-recovery.py` tests backup/restore, idempotency, draining and non-owner evidence protection. `python3 scripts/test-hardened-config.py` checks the retained deployment configuration without printing resolved secrets.

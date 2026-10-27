# Tadpoles

Tadpoles is a role-based investing and trading workspace. It includes a browser application, a Java/Spring API, PostgreSQL storage, and a read-only Python analytics API. The local demo uses USD accounting, internally booked trades and cash movements, and Fauxnance market data. It does not submit orders to an exchange or move money through a real bank.

Use this README to get a local copy running and find the project's test commands. The [database guide](database/README.md) explains schema setup, migrations, demo data, and database permissions. The [local deployment guide](docs/local_deploy_guide.md) has additional deployment context.

## Team Member Names
- Devon: Dates
- Grace: Grapes
- Michael: Mayo
- Timothy: Tacos
- Justin: Jalapenos

## Team Git Workflow Choice
For our project, we opted to use Git-Flow Development, for the purpose of maintaining a clean and organized codebase. This workflow allows us to manage feature development, bug fixes, and releases in a structured manner.

## Application roles

Roles are assigned to accounts. The authenticated workspace and API permissions determine which pages and records are available.

| Role | Purpose |
| --- | --- |
| `CLIENT` | Manages a personal account, views markets and portfolio information, submits eligible orders, and uses support and banking workflows. A newly registered client completes onboarding before accessing account features. |
| `TRADING_OPS` | Monitors trading and operational work, manages instrument policies and client trade access, and works support cases, exceptions, and reconciliation. Evidence access is separately scoped and audited. |
| `COMPLIANCE` | Reviews investigations and evidence within the current account grants, with access contexts and exports controlled by policy. |
| `ANALYST` | Views reporting dashboards, activity trends, and instrument analytics. |
| `ADVISOR` | Reviews assigned clients and their portfolios, and records advisory guidance and recommendations. |
| `FINANCE` | Reviews finance reporting and reconciliation information. |
| `ADMIN` | A privileged platform role represented in the identity and API authorization model. It does not imply that every trading control is bypassed. |

Local demo accounts are seeded by Compose. Their username, role, and demo password are listed in [`database/demo/role_workspaces.sql`](database/demo/role_workspaces.sql); the current demo password is `RoleDemo2026!`. These credentials and demo data are for local development only.

## Prerequisites

For the complete Docker-based application:

- Docker Engine or Docker Desktop with the Docker Compose plugin.
- Git and a terminal.
- An available Fauxnance API key for live quote data; set `FAUX_API_KEY` in the local environment file. Check with the project maintainer if you do not have a key.

For component-level development and tests:

- Java 17 and Maven 3.8 or newer for the Spring API and Java database tests.
- Node.js 22 and npm for the Angular frontend.
- Python 3.11 or newer and `pip` for the analytics API and its tests.
- A running Docker daemon for Testcontainers-based database integration tests.

## Run the complete application with Docker

From the repository root:

1. Create a local environment file from the template:

   ```sh
   cp .env.template .env
   ```

2. Edit `.env`. Replace `FAUX_API_KEY=redacted` with a valid provider key. For any shared or deployed environment, replace the example passwords and secrets with unique values. Keep `.env` private; it is ignored by Git.

3. Build and start the application:

   ```sh
   docker compose up --build -d
   ```

   On a fresh database volume Compose initializes PostgreSQL, applies the ordered migrations, imports the instrument catalog, seeds local demo accounts, then starts the application services.

4. Open the app at [http://localhost:8087](http://localhost:8087). The Nginx port can be changed with `NGINX_HOST_PORT` in `.env`.

Useful commands:

```sh
docker compose ps                 # check service status
docker compose logs -f backend    # follow API logs
docker compose logs -f frontend   # follow frontend logs
docker compose down               # stop services and keep database volumes
```

Database volumes retain user and account data. `docker compose down` keeps them. Do not remove volumes as a routine way to fix startup problems; see the [database guide](database/README.md) for fresh-install and upgrade behavior.

## Run services separately

The simplest supported full-stack setup is Docker Compose above. For code-level iteration, you can run a service directly while keeping its dependencies in Compose.

### Frontend

```sh
cd frontend
npm ci
npm start
```

Angular serves the app at [http://localhost:4200](http://localhost:4200). A working API and authentication configuration are still required for signed-in workflows.

### Backend

Start PostgreSQL and the auth stub with the Compose configuration, then run the API from `backend/` with the database, auth, JWT, and provider environment variables from `.env`:

```sh
cd backend
mvn spring-boot:run
```


### Analytics API

The analytics service expects its read-only PostgreSQL database and settings from `.env`:

```sh
cd analytics-service
python -m venv .venv
source .venv/bin/activate
pip install -e .
uvicorn app.main:app --reload --host 127.0.0.1 --port 8000
```

For API paths and reporting constraints, see [`analytics-service/README.md`](analytics-service/README.md).

## Tests and checks

Run the checks for the part of the project you changed. Commands below assume you start in the repository root.

### Frontend

```sh
cd frontend
npm ci
npm test -- --watch=false       # Angular/Vitest component and unit tests
npm run test:qof-styles         # design-token and style regression check
npm run build                   # production client/server build
```

To run one component spec, for example:

```sh
npm test -- --watch=false --include='src/app/features/operations/user-access.component.spec.ts'
```

### Spring API

```sh
cd backend
mvn test
```

This runs Java unit, controller, and mapper tests. Tests using Testcontainers also need Docker running.

### PostgreSQL schema and migration integration tests

```sh
cd testing
mvn -B test
```

These tests use Testcontainers to create disposable PostgreSQL databases and verify fresh schema and upgrade behavior; keep the Docker daemon running.

### Analytics API

```sh
cd analytics-service
python -m venv .venv
source .venv/bin/activate
pip install -e '.[test]'
python -m pytest
```

### Full local build

To build every production container from the repository root:

```sh
docker compose build
```

To start the built containers afterward:

```sh
docker compose up -d
```

## Repository map

| Path | Contents |
| --- | --- |
| `frontend/` | Angular application and frontend unit tests. |
| `backend/` | Spring Boot API, business logic, MyBatis mappers, and Java tests. |
| `database/` | Fresh PostgreSQL schema, ordered migrations, demo seed files, and grants. |
| `analytics-service/` | FastAPI read-only analytics service and Python tests. |
| `testing/` | PostgreSQL/Testcontainers schema and migration integration tests. |
| `nginx/` | Local reverse proxy configuration. |
| `docs/` | Deployment notes, operations guidance, and implementation references. |

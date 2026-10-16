# Jenkins Pipeline Evaluation and Implementation Plan

## Executive Summary

The current pipeline validates backend Maven compilation/tests, a separate PostgreSQL schema suite, backend image construction, and backend process startup. It does not validate the Angular frontend, the full Compose runtime, HTTP readiness, authentication flows, or enforce a meaningful code-quality policy.

The implementation should preserve the backend and database suites, add frontend verification, make quality analysis explicit, build immutable artifacts/images, start an isolated full stack, execute HTTP/API smoke tests, and always collect reports and tear down resources.

## Application Flow

1. PostgreSQL starts from `database/schema` SQL files in filename order and receives demo workspace data from `database/demo/role_workspaces.sql`.
2. Spring Boot starts from `backend/src/main/java/com/neueda/leap/Main.java`. Runtime configuration is supplied through `backend/src/main/resources/application.yml`.
3. Authenticated requests flow through Angular route guards and services, `AuthInterceptor` Bearer-token injection, Spring Security JWT validation and live identity checks, controllers, services, MyBatis mappers, and PostgreSQL.
4. Angular SSR starts through `frontend/src/server.ts`. Frontend API calls are built by `frontend/src/app/core/api/api-url.ts`; the current implementation hardcodes backend host port `8082`.
5. Compose includes database, demo seed, backend, frontend, and `auth-stub`. CI must test this same topology rather than a backend connected to an empty PostgreSQL container.

## Current Test Inventory

- **Backend:** controller, service, MyBatis mapper, security, JWT/live identity, and limited integration tests. Run `cd backend && mvn -B clean verify -Djacoco.skip=false`. Most database-facing tests use H2 rather than PostgreSQL.
- **Database:** the `testing` module contains PostgreSQL/Testcontainers schema, constraint, and business-flow tests. Run `cd testing && mvn -B test`. The base test currently loads only `001_tadpoles_schema.sql`.
- **Frontend:** Angular/Vitest unit tests, QOF style check, and production build are configured but were previously absent from Jenkins. Run `npm ci`, `npm test -- --watch=false`, `npm run test:qof-styles`, and `npm run build` from `frontend`.
- **Browser E2E:** no browser-level suite was found.
- **Runtime:** Dockerfiles exist for backend, frontend, and auth stub, but Jenkins previously built and smoke-tested only the backend.

## Current Jenkinsfile Assessment

### Strengths

- Backend `mvn clean verify` runs the main Java test and packaging lifecycle.
- JaCoCo and JUnit reports are collected for backend tests.
- PostgreSQL integration tests are separate from H2-backed backend tests.
- A container build and startup check provide a basic deployment signal.

### Gaps and Risks

- Frontend install, unit tests, style checks, and build are absent.
- The smoke test starts only PostgreSQL and the backend, with no schema, seed data, auth stub, frontend, or HTTP assertions.
- Startup is inferred from the `Started Main` log line instead of a readiness endpoint.
- Required runtime variables such as Fauxnance settings are not supplied by the smoke test.
- `Checkstyle` is skipped by default in `backend/pom.xml`; SpotBugs is configured with `failOnError=false`.
- No SonarQube, dependency, or container vulnerability scanning exists.
- The backend JAR is built by Maven and rebuilt inside `backend/Dockerfile` with tests skipped.
- Fixed `latest` image tags and fixed container/network names complicate traceability and concurrent builds.
- The database suite mounts the Jenkins Docker socket into a Maven test container, which is a significant CI isolation risk.
- `allowEmptyResults=true` can hide missing required test reports.
- The backend image uses Java 21 while Maven targets Java 17; this needs an explicit policy.
- Runtime contracts are inconsistent around `PORT`/`SERVER_PORT`, frontend API port `8082`, and required Fauxnance variables.

## Initial Changes Implemented

The first implementation slice adds the frontend verification stage and strengthens CI behavior:

- Frontend `npm ci`, headless unit tests, style checks, and production build run in Jenkins.
- Backend, database, and frontend test reports are archived.
- Required backend and database JUnit reports no longer silently pass when no reports exist.
- The backend artifact is archived before runtime smoke testing, so a later smoke failure does not hide a successful package.
- Pipeline timeout and concurrent-build controls are added.
- The backend image is tagged with the Jenkins commit/build identity in addition to `latest` where available.

<<<<<<< HEAD
<<<<<<< HEAD
The runtime-contract slice is now implemented: Compose owns the E2E stack, `.env.ci.template` supplies non-secret CI values, `/health` is implemented, fixed container names are removed, and Jenkins waits on HTTP endpoints before cleanup. SonarQube credentials/server naming and full-stack API test identities still need to be agreed before enabling quality-gate and authenticated API assertions.
=======
These changes are intentionally separate from the next runtime-contract work. SonarQube credentials/server naming, health endpoints, and full-stack API test identities are not currently defined in the repository and must be agreed before enabling those gates.
>>>>>>> a505320 (Added frontend tests + general improvements to jenkins)
=======
The runtime-contract slice is now implemented: Compose owns the E2E stack, `.env.ci.template` supplies non-secret CI values, `/health` is implemented, fixed container names are removed, and Jenkins waits on HTTP endpoints before cleanup. SonarQube credentials/server naming and full-stack API test identities still need to be agreed before enabling quality-gate and authenticated API assertions.
>>>>>>> fb4997d (Added Docker-Compose to Smoke Test Stage)

## Target Pipeline

1. **Checkout**
2. **Validate Toolchain and Configuration**
3. **Backend Verify** with JaCoCo and explicit Checkstyle/SpotBugs policy
4. **Frontend Verify**: install, unit tests, style checks, production build
5. **Database Integration** against isolated PostgreSQL
6. **Package and Archive Artifacts**
7. **SonarQube Analysis and Quality Gate**
8. **Dependency and Container Scans**
9. **Build Backend, Frontend, and Auth Images** with immutable commit/build tags
10. **Start Isolated Compose Environment**
11. **HTTP/API Smoke** covering readiness, login, identity, authorization, and a database-backed read
12. **Browser E2E** once deterministic identities and seeded data are available
13. **Publish Approved Artifacts/Images**
14. **Always collect logs and clean up**

## Implementation Steps

### Phase 1: CI source gates

1. Keep the frontend stage added in `Jenkinsfile`; confirm the Angular builder emits deterministic headless results and configure coverage output if SonarQube will consume it.
2. Decide whether Checkstyle and SpotBugs are blocking gates. Update `backend/pom.xml` so enabled tools have intentional thresholds rather than disabled or non-blocking defaults.
3. Add SonarQube configuration to Jenkins and repository configuration. Supply the server/token through Jenkins credentials, configure Java and frontend sources, JaCoCo XML, frontend coverage, exclusions, and quality-gate behavior.
4. Add dependency scanning and image scanning with tools available on the Jenkins agents. Start report-only if thresholds have not been agreed, then enforce thresholds.

### Phase 2: Configuration and artifacts

5. Reconcile `backend/src/main/resources/application.yml`, `docker-compose.yml`, `.env.template`, and deployment docs. Standardize `SERVER_PORT`, database names, frontend/backend ports, Fauxnance variables, and auth-stub URLs.
6. Make the frontend API base URL configurable instead of hardcoding `8082` in `frontend/src/app/core/api/api-url.ts`; update interceptor origin handling with it.
7. Decide whether Java 17 or Java 21 is the supported runtime and align Maven, Docker build, and runtime images.
8. Produce the backend JAR and frontend SSR bundle once where practical. Build runtime images from those artifacts or document the intentional duplicate build.
9. Build backend, frontend, and auth-stub images with `${GIT_COMMIT}` or a sanitized build identifier. Do not use `latest` as the only identity.

### Phase 3: Full-stack integration

<<<<<<< HEAD
<<<<<<< HEAD
The current Jenkins implementation now builds backend, frontend, and auth-stub images, starts the full Compose topology with an isolated project name, waits for auth-stub `/health`, backend `/health`, and frontend `/login`, archives Compose logs, and always runs `down -v --remove-orphans`.

=======
>>>>>>> a505320 (Added frontend tests + general improvements to jenkins)
=======
The current Jenkins implementation now builds backend, frontend, and auth-stub images, starts the full Compose topology with an isolated project name, waits for auth-stub `/health`, backend `/health`, and frontend `/login`, archives Compose logs, and always runs `down -v --remove-orphans`.

>>>>>>> fb4997d (Added Docker-Compose to Smoke Test Stage)
10. Add a CI-specific Compose override or environment file with deterministic secrets, required Fauxnance settings, isolated project naming, and seeded database initialization.
11. Add healthchecks for backend, frontend, and auth stub where meaningful. Implement or document a real unauthenticated readiness endpoint; `/health` is currently permitted by security configuration but was not found as an implemented controller endpoint.
12. Replace manual `docker run` log polling with Compose startup, health/readiness polling, HTTP assertions, and service-log capture on failure.
13. Test login, `/api/users/me`, one permitted role path, one forbidden role path, and one representative database-backed read.
14. Add Playwright or another browser runner for login, protected routing, onboarding, portfolio, and trading journeys after stable CI users and seed data exist.

### Phase 4: Hardening and rollout

15. Keep top-level timeouts and concurrency isolation. Use unique Compose project names and always remove containers, networks, and temporary volumes.
16. Replace the Docker socket mount in the database-test container with an approved Docker-capable Jenkins agent, isolated database service, or Testcontainers Cloud strategy.
17. Run source tests and quality gates on every PR. Run full Compose/E2E and image publication on main/release branches.
18. Roll out in slices: frontend CI, report-only quality scans, full-stack API smoke, browser E2E, then enforced security thresholds.

## Relevant Files

- `Jenkinsfile`: stage ordering, reports, timeouts, image tags, Compose lifecycle, and cleanup.
- `backend/pom.xml`: Java, JaCoCo, Checkstyle, SpotBugs, and Sonar configuration.
- `backend/Dockerfile`: duplicate build and Java alignment.
- `backend/src/main/resources/application.yml`: required runtime environment contract.
- `backend/src/main/java/com/neueda/leap/config/SecurityConfig.java`: readiness access, authentication, roles, and CORS.
- `frontend/package.json`, `frontend/angular.json`: frontend test/build commands.
- `frontend/src/app/core/api/api-url.ts`, `frontend/src/app/core/auth/auth.interceptor.ts`: API URL and token origin contract.
- `frontend/src/server.ts`, `frontend/Dockerfile`: SSR startup and output.
- `docker-compose.yml`, `.env.template`: service dependencies, ports, healthchecks, images, and test environment.
- `database/schema/*.sql`, `database/demo/role_workspaces.sql`: initialization and seed contract.
- `testing/pom.xml`, `testing/src/test/java/com/neueda/leap/db/AbstractPostgresIntegrationTest.java`: PostgreSQL isolation and script coverage.
- `docs/docker-containerization-guide.md`, `docs/local_deploy_guide.md`: documentation to reconcile with the actual runtime contract.

## Verification Checklist

- `cd backend && mvn -B clean verify -Djacoco.skip=false`
- `cd frontend && npm ci && npm test -- --watch=false && npm run test:qof-styles && npm run build`
- `cd testing && mvn -B test`
- `docker compose -p tadpole-ci config` with the CI environment file
- HTTP readiness checks for every Compose service
- API checks for login, identity, allowed access, forbidden access, and a database-backed read
- SonarQube analysis with coverage ingestion and quality-gate behavior
- Dependency/image scan reports archived with explicit policy behavior
- Forced Compose failure confirms logs are archived and cleanup runs

## Decisions Required Before Later Phases

1. SonarQube versus SonarCloud, project key, Jenkins server name, credentials, and blocking thresholds.
2. Approved Jenkins Docker execution strategy that does not mount the host Docker socket into an untrusted Maven image.
3. Health/readiness endpoint implementation and whether readiness includes database/auth dependencies.
4. Fauxnance strategy for CI: deterministic mock, controlled test service, or reachable external dependency.
5. Deterministic CI users, roles, and database fixtures for API and browser tests.
6. Angular coverage format and SonarQube source/test exclusions.

## Scope Boundary

This first slice changes CI source verification and documentation only. It does not commit secrets, alter production deployment behavior, invent a health endpoint, or add browser E2E without stable test identities. Those changes should follow the decisions above and be implemented as separate, verifiable slices.

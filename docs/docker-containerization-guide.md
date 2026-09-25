# Docker Containerization Guide

This document explains how the backend, database, and auth stub should be run as containers in the Tadpoles project.

## Goal

The intended runtime layout is:

- `backend` runs the Spring Boot API
- `database` runs PostgreSQL
- `auth-stub` runs as an isolated service that can be called directly or used by the backend

When `docker compose up` is run, all three services should be available on the same Compose network.

## Service responsibilities

### `backend`

The backend container should:

- build the Spring Boot application
- run the packaged JAR
- connect to the database container by service name
- call the auth stub by service name when needed

Important: inside Docker, `localhost` means the container itself, not another service.

So the backend should use the database service name in its JDBC URL, for example:

```text
jdbc:postgresql://database:5432/tadpole_db
```

### `database`

The database container should:

- run PostgreSQL
- persist data with a Docker volume
- optionally initialize schema/data from SQL files

For most cases, the official `postgres` image in `docker-compose.yml` is enough, so a custom database Dockerfile is usually unnecessary.

### `auth-stub`

The auth stub should be its own service so it can be:

- started with Compose
- used directly for testing
- isolated from backend and database implementation details

The backend should reach it by service name, for example:

```text
http://auth-stub:8081
```

## Recommended container layout

### `backend/Dockerfile`

The backend Dockerfile should be a multi-stage build:

1. build the JAR with Maven
2. copy the packaged artifact into a lightweight runtime image
3. run the JAR with `java -jar`

The final JAR name in the Dockerfile must match the `finalName` in the backend `pom.xml`.

### `docker-compose.yml`

The Compose file should define:

- `backend`
- `database`
- `auth-stub`

Recommended environment variables for `backend`:

- `DB_URL=jdbc:postgresql://database:5432/tadpole_db`
- `DB_USERNAME=tadpole_user`
- `DB_PASSWORD=tadpole_password`
- `PORT=8080`
- `AUTH_STUB_URL=http://auth-stub:8081`

Recommended environment variables for `database`:

- `POSTGRES_USER=tadpole_user`
- `POSTGRES_PASSWORD=tadpole_password`
- `POSTGRES_DB=tadpole_db`

## Suggested Compose structure

```yaml
services:
  database:
    image: postgres:15
    environment:
      POSTGRES_USER: tadpole_user
      POSTGRES_PASSWORD: tadpole_password
      POSTGRES_DB: tadpole_db
    ports:
      - "5432:5432"
    volumes:
      - tadpole-db-data:/var/lib/postgresql/data
      - ./database/schema:/docker-entrypoint-initdb.d:ro

  backend:
    build:
      context: ./backend
      dockerfile: Dockerfile
    ports:
      - "8080:8080"
    environment:
      DB_URL: jdbc:postgresql://database:5432/tadpole_db
      DB_USERNAME: tadpole_user
      DB_PASSWORD: tadpole_password
      PORT: 8080
      AUTH_STUB_URL: http://auth-stub:8081
    depends_on:
      - database
      - auth-stub

  auth-stub:
    build:
      context: ./auth_stub
      dockerfile: Dockerfile
    ports:
      - "8081:8081"

volumes:
  tadpole-db-data:
```

## Important Docker rules

### 1. Do not use `localhost` between containers

If the backend needs the database or auth stub, it must use the Compose service name, not `localhost`.

### 2. Keep the auth stub isolated

The auth stub should stay separate so it can be replaced later with a real auth service without changing the backend container layout.

### 3. Make sure the backend JAR name matches

If the backend `pom.xml` uses:

```xml
<finalName>team-skeleton</finalName>
```

then the Dockerfile should copy:

```dockerfile
COPY --from=build /build/target/team-skeleton.jar app.jar
```

If the JAR name changes, update the Dockerfile accordingly.

### 4. Use `depends_on` only for startup order

`depends_on` starts containers in order, but it does not guarantee the database is ready to accept connections. If needed, add health checks or retry logic.

## Practical startup flow

A good runtime flow is:

1. PostgreSQL starts
2. the auth stub starts
3. the backend starts and connects to both services over the Compose network
4. you access the backend through `http://localhost:8080`
5. you access the auth stub directly through `http://localhost:8081`

## Summary

Yes, the project should be containerized with:

- backend + database working together
- auth stub running as a separate service
- service-to-service communication using Compose service names
- environment variables for runtime configuration

This keeps the stack easy to run locally now and easier to replace later with real services.


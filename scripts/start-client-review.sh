#!/usr/bin/env bash
# Disposable local review environment, separate from Compose volumes and .env.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
prefix="${REVIEW_PREFIX:-tadpoles-client-review}"
[[ "$prefix" =~ ^tadpoles-[a-z0-9-]+$ ]] || { echo "Use a tadpoles- review prefix" >&2; exit 1; }
review_fixture=false
review_reuse=false
for option in "$@"; do
  case "$option" in
    --http-fixture) review_fixture=true ;;
    --reuse) review_reuse=true ;;
    *) echo "Unknown review option" >&2; exit 1 ;;
  esac
done
if [[ "$review_fixture" == true ]]; then
  export FAUX_SERVER_URL="http://$prefix-provider:8080"
  export FAUX_API_KEY=isolated-test-key
else
  : "${FAUX_SERVER_URL:?Set the configured provider URL}"
  : "${FAUX_API_KEY:?Set the provider key}"
fi
if docker container inspect "$prefix-db" >/dev/null 2>&1; then
  if [[ "$review_reuse" != true ]]; then
    echo 'Review database exists. Use --reuse to rebuild apps while preserving its orders.' >&2
    exit 1
  fi
  docker start "$prefix-db" >/dev/null
  docker rm -f "$prefix-api" "$prefix-ui" >/dev/null 2>&1 || true
else
  docker network create "$prefix" >/dev/null
  docker run -d --name "$prefix-db" --network "$prefix" -e POSTGRES_DB=tadpoles_isolated_test -e POSTGRES_USER=test -e POSTGRES_PASSWORD=test postgres:15-alpine >/dev/null
  for attempt in {1..30}; do
    if docker exec "$prefix-db" pg_isready -U test -d tadpoles_isolated_test >/dev/null 2>&1; then break; fi
    sleep 1
  done
  for sql in "$root"/database/fresh/*.sql; do
    docker exec -i "$prefix-db" psql -U test -d tadpoles_isolated_test -v ON_ERROR_STOP=1 < "$sql" >/dev/null
  done
fi
# Reuse follows the same versioned upgrade chain as Compose; it must not run a new jar
# against an earlier review schema. Migration 006 refuses undrained old work.
docker run --rm --network "$prefix" \
  -e PGHOST="$prefix-db" -e PGDATABASE=tadpoles_isolated_test -e PGUSER=test -e PGPASSWORD=test \
  -v "$root/database/migrations:/migrations:ro" -v "$root/database/migrate.sh:/migrate.sh:ro" \
  postgres:15-alpine sh /migrate.sh >/dev/null
docker exec -i "$prefix-db" psql -U test -d tadpoles_isolated_test -v ON_ERROR_STOP=1 < "$root/database/demo/role_workspaces.sql" >/dev/null
if [[ "$review_fixture" == true ]]; then
  docker rm -f "$prefix-provider" >/dev/null 2>&1 || true
  docker run -d --name "$prefix-provider" --network "$prefix" -v "$root/scripts/fixtures/fauxnance-http.mjs:/fixture.mjs:ro" node:22-bookworm-slim node /fixture.mjs >/dev/null
  echo 'TEST ONLY: isolated HTTP fixture selected explicitly for browser verification.'
fi
docker run --rm --user "$(id -u):$(id -g)" -e MAVEN_CONFIG=/tmp/maven -v "${TADPOLES_TEST_CACHE:-/tmp/tadpoles-maven-cache}:/tmp/maven" -v "$root:/workspace" -w /workspace maven:3.9-eclipse-temurin-21 mvn -B -Dmaven.repo.local=/tmp/maven/repository -f backend/pom.xml -DskipTests package > /tmp/tadpoles-review-build.log 2>&1
# Snapshot build output so a subsequent test/build cannot change files under a running server.
review_build="$root/backend/target/client-review"
mkdir -p "$review_build"
cp "$root/backend/target/team-skeleton.jar" "$review_build/app.jar"
docker run --rm --user "$(id -u):$(id -g)" -v "$root:/workspace" -w /workspace/frontend node:22-bookworm-slim sh -c 'npm ci && npm run build' > /tmp/tadpoles-review-ui-build.log 2>&1
rm -rf "$review_build/dist"
cp -R "$root/frontend/dist/pole" "$review_build/dist"
docker run -d --name "$prefix-api" --network "$prefix" -p 127.0.0.1:8082:8080 \
  -e DB_URL="jdbc:postgresql://$prefix-db:5432/tadpoles_isolated_test" -e DB_USERNAME=test -e DB_PASSWORD=test \
  -e JWT_SECRET=local-review-only-jwt-secret-at-least-32-bytes -e SERVER_PORT=8080 \
  -e TRADING_ADMISSION_ENABLED=true -e TRADING_WORKER_ENABLED=true \
  -e TRADING_LOCAL_DEMO_SETUP=true -e BANKING_ENCRYPTION_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA= \
  -e CORS_ALLOWED_ORIGINS=http://localhost:8086,http://127.0.0.1:8086 \
  -e FAUX_SERVER_URL="${FAUX_SERVER_URL:?Set the configured provider URL}" -e FAUX_API_KEY="${FAUX_API_KEY:?Set the provider key}" -e FAUX_DEFAULT_INTERVAL=1d \
  -e FAUX_CONNECT_TIMEOUT_SECONDS=1 -e FAUX_READ_TIMEOUT_SECONDS=1 \
  -v "$review_build/app.jar:/app.jar:ro" maven:3.9-eclipse-temurin-21 java -jar /app.jar >/dev/null
docker run -d --name "$prefix-ui" --network "$prefix" -p 127.0.0.1:8086:4000 -e PORT=4000 \
  -v "$review_build/dist:/app/dist:ro" node:22-bookworm-slim node /app/dist/server/server.mjs >/dev/null
echo 'Disposable provider-backed review: http://localhost:8086/login'
echo 'Stop only this review environment with scripts/stop-client-review.sh when finished.'

#!/usr/bin/env bash
# Container-only verification; no host Docker socket or production DB is mounted.
set -euo pipefail
project_dir="$(cd "$(dirname "$0")/.." && pwd)"
run_id="tadpoles-check-$$"
cache_dir="${TADPOLES_TEST_CACHE:-/tmp/tadpoles-maven-cache}"
mkdir -p "$cache_dir"
cleanup() {
  docker rm -f "$run_id-db" >/dev/null 2>&1 || true
  docker network rm "$run_id" >/dev/null 2>&1 || true
}
trap cleanup EXIT
docker network create "$run_id" >/dev/null
docker run -d --name "$run_id-db" --network "$run_id" -e POSTGRES_DB=tadpoles_isolated_test -e POSTGRES_USER=test -e POSTGRES_PASSWORD=test postgres:15-alpine >/dev/null
ready=false
for attempt in {1..30}; do
  if docker exec "$run_id-db" pg_isready -U test -d tadpoles_isolated_test >/dev/null 2>&1; then ready=true; break; fi
  sleep 1
done
if [[ "$ready" != true ]]; then echo 'Disposable PostgreSQL did not become ready' >&2; exit 1; fi
docker run --rm --user "$(id -u):$(id -g)" --network "$run_id" -e "ISOLATED_TEST_DB_URL=jdbc:postgresql://$run_id-db:5432/tadpoles_isolated_test" -e MAVEN_CONFIG=/tmp/maven -v "$cache_dir:/tmp/maven" -v "$project_dir:/workspace" -w /workspace maven:3.9-eclipse-temurin-21 mvn -B -Dmaven.repo.local=/tmp/maven/repository -f backend/pom.xml test
docker run --rm --user "$(id -u):$(id -g)" --network "$run_id" -e MAVEN_CONFIG=/tmp/maven -e "ISOLATED_TEST_DB_URL=jdbc:postgresql://$run_id-db:5432/tadpoles_isolated_test" -v "$cache_dir:/tmp/maven" -v "$project_dir:/workspace" -w /workspace maven:3.9-eclipse-temurin-21 mvn -B -Dmaven.repo.local=/tmp/maven/repository -f testing/pom.xml test
docker run --rm --user "$(id -u):$(id -g)" -e npm_config_cache=/tmp/npm -v "$project_dir:/workspace" -w /workspace/frontend node:22-bookworm-slim sh -c 'npm ci && npm test -- --watch=false && npm run test:qof-styles && npm run build'

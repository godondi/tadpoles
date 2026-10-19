#!/usr/bin/env bash
set -euo pipefail
psql --set=reader_password="$ANALYTICS_READER_PASSWORD" --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<'SQL'
CREATE ROLE analytics_reader LOGIN PASSWORD :'reader_password';
SQL

#!/bin/sh
set -eu

# One psql session holds the deployment lock across the entire ordered chain.
# /migrations is the read-only mount used by Compose and review/test containers.
script=$(mktemp)
trap 'rm -f "$script"' EXIT

cat > "$script" <<'SQL'
\set ON_ERROR_STOP on

SELECT pg_advisory_lock(7414014);

CREATE TABLE IF NOT EXISTS schema_migrations (
    version integer PRIMARY KEY,
    applied_at timestamptz NOT NULL DEFAULT now()
);
SQL

# Zero-padded filenames sort numerically. Fresh installs already record versions
# included in their snapshot; retained volumes execute only missing versions.
for migration in /migrations/*.sql; do
    version=$(basename "$migration" | cut -d_ -f1 | sed 's/^0*//')

    cat >> "$script" <<SQL
SELECT EXISTS (
    SELECT 1 FROM schema_migrations WHERE version = $version
) AS applied \\gset

\\if :applied
\\else
    \\i $migration
    INSERT INTO schema_migrations (version)
    VALUES ($version)
    ON CONFLICT DO NOTHING;
\\endif
SQL
done

printf 'SELECT pg_advisory_unlock(7414014);\n' >> "$script"
psql -f "$script"

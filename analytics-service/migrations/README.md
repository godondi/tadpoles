# Reporting migrations

Apply these migrations to the separate analytics PostgreSQL database. The serving API must use a database role with `SELECT` only on approved `reporting` views/tables. Ingestion and migration credentials are separate from the API credential.

The schema intentionally stores pseudonymous segment keys and durable source/fill event identifiers. It does not copy client names, contact data, accounts, or raw transaction records.

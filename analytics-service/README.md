# Analytics service

This is the read-only historical analytics boundary. It validates analyst JWTs independently from Angular and Spring, reads only the reporting database, and never owns orders, holdings, cash, fills, or legal transaction state.

The default local Compose configuration runs in explicit `demo` mode. A repeatable reporting seed populates fixed pseudonymous fixtures in the separate reporting database, and `demo.analyst` can view them through the read-only API. Responses are visibly marked `DEMONSTRATION` and are not authoritative trading or portfolio history.

Authoritative mode remains fail-closed: set `ANALYTICS_DATA_MODE=authoritative` and report routes return `503` until an approved reporting source, loader, and metric definitions are connected. The API never reads Spring transaction tables or exploratory notebook values.

```powershell
python -m pip install -e ".[test]"
python -m pytest -q
uvicorn app.main:app --reload --port 8090
```

## Local Compose demo

```text
docker-compose build
docker-compose up
```

The `analytics-demo-seed` job runs after the reporting database is healthy and is safe to repeat on the persistent local volume. Open the application through the configured Nginx host port and sign in as `demo.analyst` with password `RoleDemo2026!`. Analytics responses carry a demonstration warning in the freshness rail.

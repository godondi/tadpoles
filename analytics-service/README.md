# Analytics service

This is the read-only historical analytics boundary. It validates analyst JWTs independently from Angular and Spring, reads only the reporting database, and never owns orders, holdings, cash, fills, or legal transaction state.

The current release is intentionally fail-closed: the API exposes health checks and a typed route surface, but report routes return `503` until an approved reporting source and metric definitions are connected. This prevents empty or exploratory notebook values from appearing authoritative.

```powershell
python -m pip install -e ".[test]"
python -m pytest -q
uvicorn app.main:app --reload --port 8090
```

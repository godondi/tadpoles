from fastapi import FastAPI
from fastapi.responses import JSONResponse

from app.api import router

app = FastAPI(
    title="Tadpoles Analytics API",
    version="0.1.0",
    description="Read-only historical reporting. It never owns live transaction state.",
)
app.include_router(router)


@app.get("/health/live", tags=["health"])
def live() -> dict[str, str]:
    return {"status": "ok"}


@app.get("/health/ready", tags=["health"])
def ready() -> JSONResponse:
    return JSONResponse(
        status_code=503,
        content={"status": "unavailable", "message": "Reporting database is not configured."},
    )

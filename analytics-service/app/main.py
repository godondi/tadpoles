from fastapi import FastAPI
from fastapi.responses import JSONResponse
from sqlalchemy import text
from sqlalchemy.exc import SQLAlchemyError

from app.api import router
from app.config import settings
from app.db import reporting_engine

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
    try:
        with reporting_engine().connect() as connection:
            connection.execute(text("SELECT 1"))
    except (RuntimeError, SQLAlchemyError):
        return JSONResponse(
            status_code=503,
            content={"status": "unavailable", "message": "Reporting database is unavailable."},
        )
    return {"status": "ok", "data_mode": settings.data_mode}

from collections.abc import Iterator

from fastapi import HTTPException, status
from sqlalchemy import create_engine, text
from sqlalchemy.engine import Engine
from sqlalchemy.exc import SQLAlchemyError
from sqlalchemy.orm import Session

from app.config import settings


def reporting_engine() -> Engine:
    if not settings.reporting_database_url:
        raise RuntimeError("ANALYTICS_DATABASE_URL is not configured")
    return create_engine(
        settings.reporting_database_url,
        connect_args={"options": "-c default_transaction_read_only=on"},
        pool_pre_ping=True,
    )


def reporting_session() -> Iterator[Session]:
    try:
        with reporting_engine().begin() as connection:
            connection.execute(text("SET TRANSACTION READ ONLY"))
            with Session(bind=connection) as session:
                yield session
    except (RuntimeError, SQLAlchemyError):
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="The reporting database is unavailable.",
        ) from None

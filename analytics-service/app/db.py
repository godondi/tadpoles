from collections.abc import Iterator

from sqlalchemy import create_engine, text
from sqlalchemy.engine import Engine
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
    with reporting_engine().begin() as connection:
        connection.execute(text("SET TRANSACTION READ ONLY"))
        with Session(bind=connection) as session:
            yield session

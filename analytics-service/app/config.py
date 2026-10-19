from dataclasses import dataclass
import os


@dataclass(frozen=True)
class Settings:
    jwt_secret: str = os.getenv("ANALYTICS_JWT_SECRET", os.getenv("JWT_SECRET", ""))
    jwt_issuer: str | None = os.getenv("ANALYTICS_JWT_ISSUER") or None
    jwt_audience: str | None = os.getenv("ANALYTICS_JWT_AUDIENCE") or None
    reporting_database_url: str = os.getenv("ANALYTICS_DATABASE_URL", "")
    max_range_days: int = int(os.getenv("ANALYTICS_MAX_RANGE_DAYS", "366"))
    max_page_size: int = int(os.getenv("ANALYTICS_MAX_PAGE_SIZE", "100"))


settings = Settings()

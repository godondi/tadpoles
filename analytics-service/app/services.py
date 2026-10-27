from fastapi import HTTPException, status

from app.config import settings
from app.schemas import AnalyticsFilters


def validate_filters(filters: AnalyticsFilters) -> AnalyticsFilters:
    if filters.period_end < filters.period_start:
        raise HTTPException(status_code=422, detail="The end date must not precede the start date.")
    if (filters.period_end - filters.period_start).days > settings.max_range_days:
        raise HTTPException(status_code=422, detail="The requested date range is too large.")
    return filters


def unavailable() -> None:
    raise HTTPException(
        status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
        detail="Historical reporting is unavailable until the approved reporting source is connected.",
    )

from datetime import date
from typing import Annotated, Literal

from fastapi import APIRouter, Depends, Query

from app.auth import CurrentAnalyst, get_current_analyst
from app.schemas import AnalyticsFilters
from app.services import unavailable, validate_filters

router = APIRouter(prefix="/api/analytics", tags=["analytics"])
Analyst = Annotated[CurrentAnalyst, Depends(get_current_analyst)]


def filters(
    period_start: Annotated[date, Query(alias="from")],
    period_end: Annotated[date, Query(alias="to")],
    timezone: Annotated[str, Query()] = "UTC",
    granularity: Annotated[Literal["day", "week", "month"], Query()] = "day",
    market: Annotated[str | None, Query()] = None,
    asset_class: Annotated[str | None, Query()] = None,
    instrument: Annotated[str | None, Query()] = None,
    segment: Annotated[str | None, Query()] = None,
) -> AnalyticsFilters:
    return validate_filters(
        AnalyticsFilters(
            **{
                "from": period_start,
                "to": period_end,
                "timezone": timezone,
                "granularity": granularity,
                "market": market,
                "asset_class": asset_class,
                "instrument": instrument,
                "segment": segment,
            }
        )
    )


@router.get("/summary")
def summary(_: Analyst, report_filters: Annotated[AnalyticsFilters, Depends(filters)]) -> None:
    unavailable()


@router.get("/trends")
def trends(_: Analyst, report_filters: Annotated[AnalyticsFilters, Depends(filters)]) -> None:
    unavailable()


@router.get("/instruments")
def instruments(
    _: Analyst,
    report_filters: Annotated[AnalyticsFilters, Depends(filters)],
    measure: Literal["fills_booked", "orders_filled", "executed_quantity"] = "fills_booked",
    page: int = Query(default=1, ge=1),
    page_size: int = Query(default=25, ge=1, le=100),
) -> None:
    unavailable()


@router.get("/freshness")
def freshness(_: Analyst) -> None:
    unavailable()

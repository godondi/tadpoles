from datetime import date
from collections.abc import Callable
from typing import Annotated, Literal, TypeVar

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy.exc import SQLAlchemyError
from sqlalchemy.orm import Session

from app.auth import CurrentAnalyst, get_current_analyst
from app.config import settings
from app.db import reporting_session
from app.reporting import (
    ReportingUnavailable,
    freshness as report_freshness,
    holdings as report_holdings,
    instruments as report_instruments,
    portfolio_performance as report_portfolio_performance,
    summary as report_summary,
    trends as report_trends,
    validate_segment_scope,
)
from app.schemas import (
    AnalyticsFilters,
    AnalyticsMeasure,
    FreshnessResponse,
    InstrumentsResponse,
    PerformancePeriod,
    PortfolioHoldingsResponse,
    PortfolioPerformanceResponse,
    SummaryResponse,
    TrendsResponse,
)
from app.services import unavailable, validate_filters

router = APIRouter(prefix="/api/analytics", tags=["analytics"])
Analyst = Annotated[CurrentAnalyst, Depends(get_current_analyst)]
ReportingSession = Annotated[Session, Depends(reporting_session)]
ReportResult = TypeVar("ReportResult")


def run_report(operation: Callable[[Session], ReportResult]) -> ReportResult:
    session_iterator = reporting_session()
    try:
        session = next(session_iterator)
        return operation(session)
    except (ReportingUnavailable, SQLAlchemyError):
        unavailable()
    finally:
        session_iterator.close()


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


@router.get("/summary", response_model=SummaryResponse)
def summary(
    _: Analyst,
    report_filters: Annotated[AnalyticsFilters, Depends(filters)],
) -> SummaryResponse:
    validate_segment_scope(report_filters)
    return run_report(lambda session: report_summary(session, report_filters))


@router.get("/trends", response_model=TrendsResponse)
def trends(
    _: Analyst,
    report_filters: Annotated[AnalyticsFilters, Depends(filters)],
    measure: AnalyticsMeasure = Query(default="fills_booked"),
) -> TrendsResponse:
    validate_segment_scope(report_filters)
    return run_report(lambda session: report_trends(session, report_filters, measure))


@router.get("/instruments", response_model=InstrumentsResponse)
def instruments(
    _: Analyst,
    report_filters: Annotated[AnalyticsFilters, Depends(filters)],
    measure: AnalyticsMeasure = "fills_booked",
    page: int = Query(default=1, ge=1),
    page_size: int = Query(default=25, ge=1, le=100),
) -> InstrumentsResponse:
    if page_size > settings.max_page_size:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail=f"page_size must not exceed {settings.max_page_size}.",
        )
    validate_segment_scope(report_filters)
    return run_report(lambda session: report_instruments(session, report_filters, measure, page, page_size))


@router.get("/freshness", response_model=FreshnessResponse)
def freshness(_: Analyst) -> FreshnessResponse:
    return run_report(lambda session: report_freshness(session))


@router.get("/portfolio-performance", response_model=PortfolioPerformanceResponse)
def portfolio_performance(
    _: Analyst,
    report_filters: Annotated[AnalyticsFilters, Depends(filters)],
    period: PerformancePeriod = "1m",
) -> PortfolioPerformanceResponse:
    validate_segment_scope(report_filters)
    return run_report(lambda session: report_portfolio_performance(session, report_filters, period))


@router.get("/holdings", response_model=PortfolioHoldingsResponse)
def holdings(
    _: Analyst,
    report_filters: Annotated[AnalyticsFilters, Depends(filters)],
) -> PortfolioHoldingsResponse:
    validate_segment_scope(report_filters)
    return run_report(lambda session: report_holdings(session, report_filters))

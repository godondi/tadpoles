from datetime import date, timedelta
from decimal import Decimal
from typing import Literal

from fastapi import HTTPException, status
from sqlalchemy import text
from sqlalchemy.orm import Session

from app.config import settings
from app.schemas import (
    AnalyticsFilters,
    AnalyticsMeasure,
    FreshnessResponse,
    HoldingAllocation,
    InstrumentRow,
    InstrumentsResponse,
    MetricValue,
    PortfolioHoldingsResponse,
    PortfolioPerformanceResponse,
    PerformancePoint,
    ReportMetadata,
    SummaryResponse,
    TrendPoint,
    TrendsResponse,
)


class ReportingUnavailable(Exception):
    pass


_MEASURE_EXPRESSION = {
    "fills_booked": "SUM(a.fill_count)",
    "orders_filled": "SUM(a.order_count)",
    "executed_quantity": "SUM(a.executed_quantity)",
}


def _is_demo() -> bool:
    return settings.data_mode == "demo"


def _source_stream() -> str:
    return "demo-fixture" if _is_demo() else "authoritative"


def _reporting_status(row: dict) -> str:
    return str(row["reconciliation_status"]) if row["reconciliation_status"] else "CURRENT"


def _metadata(session: Session, filters: AnalyticsFilters) -> ReportMetadata:
    row = session.execute(
        text(
            """
            SELECT latest_event_time, last_successful_load_at, reconciliation_status,
                   definition_version, latest_source_event_id, warning
            FROM reporting.reporting_watermark
            WHERE source_stream = :source_stream
            """
        ),
        {"source_stream": _source_stream()},
    ).mappings().first()
    if row is None:
        raise ReportingUnavailable()

    warnings = [str(row["warning"])] if row["warning"] else []
    if _is_demo() and "Demonstration data; not authoritative reporting." not in warnings:
        warnings.append("Demonstration data; not authoritative reporting.")
    return ReportMetadata(
        reporting_status=_reporting_status(row),
        data_as_of=row["latest_event_time"],
        last_successful_load_at=row["last_successful_load_at"],
        period_start=filters.period_start,
        period_end=filters.period_end,
        reporting_timezone=filters.timezone,
        definition_version=row["definition_version"],
        source_watermark=row["latest_source_event_id"],
        warnings=warnings,
        data_mode="DEMONSTRATION" if _is_demo() else "AUTHORITATIVE",
    )


def _freshness_metadata(session: Session) -> ReportMetadata:
    row = session.execute(
        text(
            """
            SELECT latest_event_time, last_successful_load_at, reconciliation_status,
                   definition_version, latest_source_event_id, warning
            FROM reporting.reporting_watermark
            WHERE source_stream = :source_stream
            """
        ),
        {"source_stream": _source_stream()},
    ).mappings().first()
    if row is None or row["latest_event_time"] is None:
        raise ReportingUnavailable()
    data_date = row["latest_event_time"].date()
    return _metadata(
        session,
        AnalyticsFilters.model_validate(
            {
                "from": data_date,
                "to": data_date,
                "timezone": "UTC",
                "granularity": "day",
            }
        ),
    )


def validate_segment_scope(filters: AnalyticsFilters) -> None:
    if filters.segment is None:
        return
    if not _is_demo() or filters.segment != "demo-internal":
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="The requested analytics segment is not available to this analyst.",
        )


def _where(filters: AnalyticsFilters, alias: str = "a") -> tuple[str, dict[str, object]]:
    conditions = [
        f"{alias}.is_demo = :is_demo",
        f"{alias}.period_start BETWEEN :period_start AND :period_end",
    ]
    params: dict[str, object] = {
        "is_demo": _is_demo(),
        "period_start": filters.period_start,
        "period_end": filters.period_end,
    }
    if filters.market:
        conditions.append(f"{alias}.market = :market")
        params["market"] = filters.market
    if filters.asset_class:
        conditions.append(f"{alias}.asset_class = :asset_class")
        params["asset_class"] = filters.asset_class
    if filters.instrument:
        conditions.append("i.symbol = :instrument")
        params["instrument"] = filters.instrument
    if filters.segment:
        conditions.append(f"{alias}.segment_key = :segment")
        params["segment"] = filters.segment
    return " AND ".join(conditions), params


def _definition(session: Session, metric_key: str, version: str | None) -> dict[str, object]:
    version_filter = "AND version = :definition_version" if version else ""
    params: dict[str, object] = {"metric_key": metric_key}
    if version:
        params["definition_version"] = version
    row = session.execute(
        text(
            f"""
            SELECT event_date_rule, formula, unit_rule, currency_rule, version
            FROM reporting.metric_definition
            WHERE metric_key = :metric_key
              {version_filter}
            ORDER BY effective_from DESC
            LIMIT 1
            """
        ),
        params,
    ).mappings().first()
    if row is None:
        return {
            "event_date_rule": "Definition pending",
            "formula": "Definition pending",
            "unit_rule": "Definition pending",
            "currency_rule": "Definition pending",
            "version": version,
        }
    return dict(row)


def _metric(
    session: Session,
    key: str,
    label: str,
    value: int | Decimal | None,
    unit: str,
    currency: str | None,
    definition_version: str | None,
) -> MetricValue:
    definition = _definition(session, key, definition_version)
    return MetricValue(
        key=key,
        label=label,
        value=value,
        unit=unit,
        currency=currency,
        definition=str(definition["formula"]),
        definition_pending=definition["version"] is None,
        event_date=str(definition["event_date_rule"]),
        definition_version=str(definition["version"]) if definition["version"] else None,
    )


def summary(session: Session, filters: AnalyticsFilters) -> SummaryResponse:
    metadata = _metadata(session, filters)
    where, params = _where(filters)
    row = session.execute(
        text(
            f"""
            SELECT COUNT(*) AS rows,
                   COALESCE(SUM(a.order_count), 0) AS orders_filled,
                   COALESCE(SUM(a.fill_count), 0) AS fills_booked,
                   SUM(a.executed_quantity) AS executed_quantity
            FROM reporting.agg_activity_period a
            JOIN reporting.dim_instrument i ON i.instrument_key = a.instrument_key
            WHERE {where}
            """
        ),
        params,
    ).mappings().one()
    if int(row["rows"]) == 0:
        return SummaryResponse(filters=filters, metadata=metadata, metrics=[], message="No activity for these filters.")

    metrics = [
        _metric(session, "fills_booked", "Booked fills", int(row["fills_booked"]), "count", None, metadata.definition_version),
        _metric(session, "orders_filled", "Filled orders", int(row["orders_filled"]), "count", None, metadata.definition_version),
    ]
    quantity_units = session.execute(
        text(
            f"""
            SELECT DISTINCT a.quantity_unit
            FROM reporting.agg_activity_period a
            JOIN reporting.dim_instrument i ON i.instrument_key = a.instrument_key
            WHERE {where} AND a.executed_quantity IS NOT NULL
            """
        ),
        params,
    ).scalars().all()
    if len(quantity_units) == 1:
        metrics.append(
            _metric(
                session,
                "executed_quantity",
                "Executed quantity",
                row["executed_quantity"],
                str(quantity_units[0]),
                None,
                metadata.definition_version,
            )
        )

    notional_rows = session.execute(
        text(
            f"""
            SELECT a.currency, SUM(a.traded_notional) AS value
            FROM reporting.agg_activity_period a
            JOIN reporting.dim_instrument i ON i.instrument_key = a.instrument_key
            WHERE {where} AND a.traded_notional IS NOT NULL
            GROUP BY a.currency
            ORDER BY a.currency
            """
        ),
        params,
    ).mappings().all()
    for notional in notional_rows:
        currency = str(notional["currency"]) if notional["currency"] else None
        key = "traded_notional" if len(notional_rows) == 1 else f"traded_notional_{currency}"
        label = "Traded notional" if len(notional_rows) == 1 else f"Traded notional ({currency})"
        metrics.append(_metric(session, key, label, notional["value"], "currency amount", currency, metadata.definition_version))
    return SummaryResponse(filters=filters, metadata=metadata, metrics=metrics)


def trends(session: Session, filters: AnalyticsFilters, measure: AnalyticsMeasure) -> TrendsResponse:
    metadata = _metadata(session, filters)
    where, params = _where(filters)
    params["granularity"] = filters.granularity
    expression = _MEASURE_EXPRESSION[measure]
    unit_expression = "'count'" if measure != "executed_quantity" else "MAX(a.quantity_unit)"
    rows = session.execute(
        text(
            f"""
            SELECT date_trunc(:granularity, a.period_start::timestamp)::date AS period_start,
                   CASE
                       WHEN :granularity = 'day' THEN date_trunc(:granularity, a.period_start::timestamp)::date
                       WHEN :granularity = 'week' THEN (date_trunc(:granularity, a.period_start::timestamp) + INTERVAL '6 days')::date
                       ELSE (date_trunc(:granularity, a.period_start::timestamp) + INTERVAL '1 month - 1 day')::date
                   END AS period_end,
                   {expression} AS value,
                   {unit_expression} AS unit
            FROM reporting.agg_activity_period a
            JOIN reporting.dim_instrument i ON i.instrument_key = a.instrument_key
            WHERE {where}
            GROUP BY date_trunc(:granularity, a.period_start::timestamp)
            ORDER BY period_start
            """
        ),
        params,
    ).mappings().all()
    units = {str(row["unit"]) for row in rows}
    if len(units) > 1:
        metadata.warnings.append("Executed quantities use more than one unit and were omitted from this series.")
        rows = []
    unit = next(iter(units), "count" if measure != "executed_quantity" else "unit pending")
    points = [TrendPoint(period_start=row["period_start"], period_end=row["period_end"], value=row["value"]) for row in rows]
    return TrendsResponse(
        filters=filters,
        metadata=metadata,
        measure=measure,
        unit=unit,
        currency=None,
        points=points,
        message=None if points else "No activity for these filters.",
    )


def instruments(
    session: Session,
    filters: AnalyticsFilters,
    measure: AnalyticsMeasure,
    page: int,
    page_size: int,
) -> InstrumentsResponse:
    metadata = _metadata(session, filters)
    where, params = _where(filters)
    expression = _MEASURE_EXPRESSION[measure]
    unit_expression = "'count'" if measure != "executed_quantity" else "MAX(a.quantity_unit)"
    currency_expression = "NULL" if measure != "executed_quantity" else "NULL"
    grouped = f"""
        SELECT i.symbol, i.display_name, i.market, i.asset_class,
               {expression} AS value, {unit_expression} AS unit, {currency_expression} AS currency
        FROM reporting.agg_activity_period a
        JOIN reporting.dim_instrument i ON i.instrument_key = a.instrument_key
        WHERE {where}
        GROUP BY i.symbol, i.display_name, i.market, i.asset_class
    """
    total = int(session.execute(text(f"SELECT COUNT(*) FROM ({grouped}) ranked"), params).scalar_one())
    params.update({"limit": page_size, "offset": (page - 1) * page_size})
    rows = session.execute(text(f"{grouped} ORDER BY value DESC, i.symbol LIMIT :limit OFFSET :offset"), params).mappings().all()
    return InstrumentsResponse(
        filters=filters,
        metadata=metadata,
        measure=measure,
        page=page,
        page_size=page_size,
        total=total,
        rows=[InstrumentRow(**dict(row), measure=measure) for row in rows],
        message=None if rows else "No activity for these filters.",
    )


def freshness(session: Session) -> FreshnessResponse:
    metadata = _freshness_metadata(session)
    return FreshnessResponse(
        metadata=metadata,
        reconciliation_status="CURRENT",
        quality_exceptions=0,
        message="Demonstration data; not authoritative reporting." if _is_demo() else None,
    )


def portfolio_performance(
    session: Session,
    filters: AnalyticsFilters,
    period: Literal["1m", "3m", "12m"],
) -> PortfolioPerformanceResponse:
    metadata = _metadata(session, filters)
    days = {"1m": 31, "3m": 93, "12m": 366}[period]
    window_start = filters.period_end - timedelta(days=days - 1)
    rows = session.execute(
        text(
            """
            SELECT snapshot_date, portfolio_value, sp500_value, dow_jones_value,
                   nasdaq_100_value, russell_2000_value
            FROM reporting.fact_portfolio_performance
            WHERE is_demo = :is_demo
              AND snapshot_date BETWEEN :window_start AND :window_end
            ORDER BY snapshot_date
            """
        ),
        {"is_demo": _is_demo(), "window_start": window_start, "window_end": filters.period_end},
    ).mappings().all()
    points = [
        PerformancePoint(
            date=row["snapshot_date"],
            values={
                "portfolio": row["portfolio_value"],
                "sp500": row["sp500_value"],
                "dow_jones": row["dow_jones_value"],
                "nasdaq_100": row["nasdaq_100_value"],
                "russell_2000": row["russell_2000_value"],
            },
        )
        for row in rows
    ]
    return PortfolioPerformanceResponse(
        filters=filters,
        metadata=metadata,
        period=period,
        points=points,
        message=None if points else "No performance data for this period.",
    )


def holdings(session: Session, filters: AnalyticsFilters) -> PortfolioHoldingsResponse:
    metadata = _metadata(session, filters)
    snapshot_date = session.execute(
        text(
            """
            SELECT MAX(snapshot_date)
            FROM reporting.fact_holding_snapshot
            WHERE is_demo = :is_demo AND snapshot_date <= :period_end
            """
        ),
        {"is_demo": _is_demo(), "period_end": filters.period_end},
    ).scalar_one()
    if snapshot_date is None:
        return PortfolioHoldingsResponse(
            filters=filters,
            metadata=metadata,
            holdings=[],
            total_market_value=None,
            currency=None,
            message="No holdings snapshot for these filters.",
        )
    rows = session.execute(
        text(
            """
            SELECT i.symbol, i.display_name, h.market_value, h.percentage, h.currency
            FROM reporting.fact_holding_snapshot h
            JOIN reporting.dim_instrument i ON i.instrument_key = h.instrument_key
            WHERE h.is_demo = :is_demo AND h.snapshot_date = :snapshot_date
            ORDER BY h.market_value DESC, i.symbol
            """
        ),
        {"is_demo": _is_demo(), "snapshot_date": snapshot_date},
    ).mappings().all()
    currencies = {str(row["currency"]) for row in rows}
    total = sum((row["market_value"] for row in rows), Decimal("0")) if len(currencies) == 1 else None
    return PortfolioHoldingsResponse(
        filters=filters,
        metadata=metadata,
        holdings=[HoldingAllocation(**dict(row)) for row in rows],
        total_market_value=total,
        currency=next(iter(currencies)) if len(currencies) == 1 else None,
    )

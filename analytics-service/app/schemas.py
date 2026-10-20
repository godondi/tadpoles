from datetime import date, datetime
from decimal import Decimal
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field


ReportingStatus = Literal["CURRENT", "DATA_DELAYED", "LOADING", "PARTIAL", "UNAVAILABLE"]
Granularity = Literal["day", "week", "month"]


class AnalyticsFilters(BaseModel):
    model_config = ConfigDict(extra="forbid")

    period_start: date = Field(alias="from")
    period_end: date = Field(alias="to")
    timezone: str = "UTC"
    granularity: Granularity = "day"
    market: str | None = None
    asset_class: str | None = None
    instrument: str | None = None
    segment: str | None = None


class ReportMetadata(BaseModel):
    reporting_status: ReportingStatus
    data_as_of: datetime | None
    last_successful_load_at: datetime | None
    period_start: date
    period_end: date
    reporting_timezone: str
    definition_version: str | None
    source_watermark: str | None
    warnings: list[str] = Field(default_factory=list)


class MetricValue(BaseModel):
    key: str
    label: str
    value: int | Decimal | None
    unit: str
    currency: str | None = None
    definition: str
    definition_pending: bool = False


class SummaryResponse(BaseModel):
    filters: AnalyticsFilters
    metadata: ReportMetadata
    metrics: list[MetricValue] = Field(default_factory=list)
    message: str | None = None


class TrendPoint(BaseModel):
    period_start: date
    period_end: date
    value: int | Decimal


class TrendsResponse(BaseModel):
    filters: AnalyticsFilters
    metadata: ReportMetadata
    measure: str
    unit: str
    currency: str | None
    points: list[TrendPoint] = Field(default_factory=list)
    message: str | None = None


class InstrumentRow(BaseModel):
    symbol: str
    display_name: str
    market: str
    asset_class: str
    measure: str
    value: int | Decimal
    unit: str
    currency: str | None


class InstrumentsResponse(BaseModel):
    filters: AnalyticsFilters
    metadata: ReportMetadata
    measure: str
    page: int
    page_size: int
    total: int
    rows: list[InstrumentRow] = Field(default_factory=list)
    message: str | None = None


class FreshnessResponse(BaseModel):
    metadata: ReportMetadata
    reconciliation_status: str
    quality_exceptions: int
    message: str | None = None


BenchmarkKey = Literal["portfolio", "sp500", "dow_jones", "nasdaq_100", "russell_2000"]
PerformancePeriod = Literal["1m", "3m", "12m"]


class PerformancePoint(BaseModel):
    date: date
    values: dict[BenchmarkKey, Decimal | int] = Field(default_factory=dict)


class PortfolioPerformanceResponse(BaseModel):
    filters: AnalyticsFilters
    metadata: ReportMetadata
    period: PerformancePeriod
    points: list[PerformancePoint] = Field(default_factory=list)
    message: str | None = None


class HoldingAllocation(BaseModel):
    symbol: str
    display_name: str
    market_value: Decimal
    percentage: Decimal
    currency: str


class PortfolioHoldingsResponse(BaseModel):
    filters: AnalyticsFilters
    metadata: ReportMetadata
    holdings: list[HoldingAllocation] = Field(default_factory=list)
    total_market_value: Decimal
    currency: str | None
    message: str | None = None

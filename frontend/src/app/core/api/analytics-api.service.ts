import { Injectable, PLATFORM_ID, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { buildAnalyticsApiUrl } from './api-url';

export type AnalyticsView = 'overview' | 'trends' | 'instruments';
export type Granularity = 'day' | 'week' | 'month';
export type AnalyticsMeasure = 'fills_booked' | 'orders_filled' | 'executed_quantity';
export type ReportingStatus = 'CURRENT' | 'DATA_DELAYED' | 'LOADING' | 'PARTIAL' | 'UNAVAILABLE';

export interface AnalyticsFilters {
  from: string;
  to: string;
  timezone: string;
  granularity: Granularity;
  market?: string;
  asset_class?: string;
  instrument?: string;
  segment?: string;
}

export interface ReportMetadata {
  reporting_status: ReportingStatus;
  data_mode: 'DEMONSTRATION' | 'AUTHORITATIVE';
  data_as_of: string | null;
  last_successful_load_at: string | null;
  period_start: string;
  period_end: string;
  reporting_timezone: string;
  definition_version: string | null;
  source_watermark: string | null;
  warnings: string[];
}

export interface MetricValue {
  key: string;
  label: string;
  value: number | string | null;
  unit: string;
  currency: string | null;
  definition: string;
  definition_pending: boolean;
  event_date?: string | null;
  definition_version?: string | null;
}

export interface SummaryResponse {
  filters: AnalyticsFilters;
  metadata: ReportMetadata;
  metrics: MetricValue[];
  message?: string | null;
}

export interface TrendPoint {
  period_start: string;
  period_end: string;
  value: number | string;
}

export interface TrendsResponse {
  filters: AnalyticsFilters;
  metadata: ReportMetadata;
  measure: AnalyticsMeasure;
  unit: string;
  currency: string | null;
  points: TrendPoint[];
  message?: string | null;
}

export interface InstrumentRow {
  symbol: string;
  display_name: string;
  market: string;
  asset_class: string;
  measure: AnalyticsMeasure;
  value: number | string;
  unit: string;
  currency: string | null;
}

export interface InstrumentsResponse {
  filters: AnalyticsFilters;
  metadata: ReportMetadata;
  measure: AnalyticsMeasure;
  page: number;
  page_size: number;
  total: number;
  rows: InstrumentRow[];
  message?: string | null;
}

export type BenchmarkKey = 'portfolio' | 'sp500' | 'dow_jones' | 'nasdaq_100' | 'russell_2000';

export interface PerformancePoint {
  date: string;
  values: Partial<Record<BenchmarkKey, number | string>>;
}

export interface PortfolioPerformanceResponse {
  filters: AnalyticsFilters;
  metadata: ReportMetadata;
  period: '1m' | '3m' | '12m';
  points: PerformancePoint[];
  message?: string | null;
}

export interface HoldingAllocation {
  symbol: string;
  display_name: string;
  market_value: number | string;
  percentage: number | string;
  currency: string;
}

export interface PortfolioHoldingsResponse {
  filters: AnalyticsFilters;
  metadata: ReportMetadata;
  holdings: HoldingAllocation[];
  total_market_value: number | string | null;
  currency: string | null;
  message?: string | null;
}

export interface FreshnessResponse {
  metadata: ReportMetadata;
  reconciliation_status: string;
  quality_exceptions: number;
  message?: string | null;
}

@Injectable({ providedIn: 'root' })
export class AnalyticsApiService {
  private readonly http = inject(HttpClient);
  private readonly platform = inject(PLATFORM_ID);

  summary(filters: AnalyticsFilters) {
    return this.http.get<SummaryResponse>(this.url('/api/analytics/summary'), {
      params: this.params(filters),
    });
  }

  trends(filters: AnalyticsFilters, measure: AnalyticsMeasure = 'fills_booked') {
    return this.http.get<TrendsResponse>(this.url('/api/analytics/trends'), {
      params: this.params(filters).set('measure', measure),
    });
  }

  instruments(filters: AnalyticsFilters, measure: AnalyticsMeasure = 'fills_booked', page = 1, pageSize = 25) {
    return this.http.get<InstrumentsResponse>(this.url('/api/analytics/instruments'), {
      params: this.params(filters).set('measure', measure).set('page', page).set('page_size', pageSize),
    });
  }

  portfolioPerformance(filters: AnalyticsFilters, period: '1m' | '3m' | '12m' = '1m') {
    return this.http.get<PortfolioPerformanceResponse>(this.url('/api/analytics/portfolio-performance'), {
      params: this.params(filters).set('period', period),
    });
  }

  portfolioHoldings(filters: AnalyticsFilters) {
    return this.http.get<PortfolioHoldingsResponse>(this.url('/api/analytics/holdings'), {
      params: this.params(filters),
    });
  }

  freshness() {
    return this.http.get<FreshnessResponse>(this.url('/api/analytics/freshness'));
  }

  private params(filters: AnalyticsFilters) {
    let params = new HttpParams()
      .set('from', filters.from)
      .set('to', filters.to)
      .set('timezone', filters.timezone)
      .set('granularity', filters.granularity);
    for (const [key, value] of Object.entries(filters)) {
      if (value && !['from', 'to', 'timezone', 'granularity'].includes(key)) {
        params = params.set(key, value);
      }
    }
    return params;
  }

  private url(path: string) {
    return buildAnalyticsApiUrl(path, this.platform);
  }
}

import { Injectable, PLATFORM_ID, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { buildAnalyticsApiUrl } from './api-url';

export type AnalyticsView = 'overview' | 'trends' | 'instruments';
export type Granularity = 'day' | 'week' | 'month';
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
  measure: string;
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
  measure: string;
  value: number | string;
  unit: string;
  currency: string | null;
}

export interface InstrumentsResponse {
  filters: AnalyticsFilters;
  metadata: ReportMetadata;
  measure: string;
  page: number;
  page_size: number;
  total: number;
  rows: InstrumentRow[];
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

  trends(filters: AnalyticsFilters) {
    return this.http.get<TrendsResponse>(this.url('/api/analytics/trends'), {
      params: this.params(filters),
    });
  }

  instruments(filters: AnalyticsFilters, measure = 'fills_booked', page = 1) {
    return this.http.get<InstrumentsResponse>(this.url('/api/analytics/instruments'), {
      params: this.params(filters).set('measure', measure).set('page', page),
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

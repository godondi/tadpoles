import { Injectable, PLATFORM_ID, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { buildApiUrl } from './api-url';
export interface Page<T> {
  items: T[];
  page: number;
  pageSize: number;
  total: number;
}
export interface Instrument {
  instrumentId: number;
  instrumentName: string;
  ticker: string;
  currency: string;
  assetClass: string | null;
  securityType: string | null;
  isActive: boolean;
}
export interface Position {
  holdingId: number;
  instrumentId: number;
  ticker: string;
  name: string;
  currency: string;
  quantity: number;
  asOfDate: string;
}
export interface Portfolio {
  postedCash: number;
  currency: string | null;
  availableCash: number | null;
  positions: Position[];
  retrievedAt: string;
  limitation: string;
}
export interface Order {
  orderId: number;
  instrumentId: number;
  ticker: string;
  instrumentName: string;
  currency: string;
  side: string;
  quantity: number;
  recordedPrice: number;
  tradeDate: string;
  status: string;
  executedAt: string | null;
  reason: string | null;
}
export interface OrderDetail {
  order: Order;
  evidenceCompleteness: string;
  explanation: string;
}
export interface TradingAvailability {
  enabled: boolean;
  reason: string;
}
export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
}
@Injectable({ providedIn: 'root' })
export class WorkspaceApi {
  private readonly http = inject(HttpClient);
  private readonly platform = inject(PLATFORM_ID);
  portfolio() {
    return this.http.get<Portfolio>(this.url('/api/workspace/client/portfolio'));
  }
  orders(params: Record<string, string | number> = {}) {
    return this.http.get<Page<Order>>(this.url('/api/orders'), { params });
  }
  order(id: string) {
    return this.http.get<OrderDetail>(this.url(`/api/orders/${encodeURIComponent(id)}`));
  }
  instruments(params: Record<string, string | number> = {}) {
    return this.http.get<Page<Instrument>>(this.url('/api/workspace/client/instruments'), {
      params,
    });
  }
  instrument(id: string) {
    return this.http.get<Instrument>(this.url(`/api/instruments/${encodeURIComponent(id)}`));
  }
  availability() {
    return this.http.get<TradingAvailability>(
      this.url('/api/workspace/client/trading-availability'),
    );
  }
  // Staff endpoints deliberately have no data contract until source and purpose policies are approved.
  staffAvailability(path: string) {
    return this.http.get<never>(this.url(path));
  }
  private url(path: string) {
    return buildApiUrl(path, this.platform);
  }
}

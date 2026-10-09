import { HttpClient } from '@angular/common/http';
import { Injectable, PLATFORM_ID, inject } from '@angular/core';
import { buildApiUrl } from './api-url';

export interface Candle {
  date: string;
  open: number;
  high: number;
  low: number;
  close: number;
  adjclose: number;
  volume: number | null;
  synthetic: boolean;
}
export interface MarketMeta {
  asOf: string;
  disclaimer: string;
  symbol: string;
  source: string;
  stale?: boolean | null;
  partial?: boolean | null;
  availableFrom?: string | null;
}
export interface CandlesResponse {
  data: { symbol: string; interval: '1d'; currency: string; candles: Candle[] };
  meta: MarketMeta;
}
export interface QuoteResponse {
  data: {
    symbol: string;
    price: number;
    bid: number;
    ask: number;
    spreadBps: number;
    currency: string | null;
    change: number | null;
    changePercent: number | null;
    previousClose: number | null;
    asOf: string;
    marketState: 'open' | 'closed' | 'pre' | 'post' | 'unknown';
  };
  meta: MarketMeta & { spreadSource: string };
}

@Injectable({ providedIn: 'root' })
export class MarketDataService {
  private readonly http = inject(HttpClient);
  private readonly platformId = inject(PLATFORM_ID);

  getCandles(symbol: string) {
    const to = new Date();
    const from = new Date(to);
    // Fetch extra calendar days so weekends/holidays do not shrink the 30-session view.
    from.setUTCDate(from.getUTCDate() - 90);
    return this.http.get<CandlesResponse>(this.url('candles', symbol), {
      params: {
        interval: '1d',
        from: from.toISOString().slice(0, 10),
        to: to.toISOString().slice(0, 10),
      },
    });
  }
  getQuote(symbol: string) {
    return this.http.get<QuoteResponse>(this.url('quotes', symbol));
  }
  private url(kind: string, symbol: string) {
    return buildApiUrl(
      `/${kind}/${encodeURIComponent(symbol.trim().toUpperCase())}`,
      this.platformId,
    );
  }
}

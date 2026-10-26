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
  trading?: TradingAvailability;
  instrumentId: number;
  instrumentName: string;
  ticker: string;
  currency: string;
  assetClass: string | null;
  securityType: string | null;
  exchange: string | null;
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
  migrationExceptions?: { id: number; kind: string; recordedAt: string }[];
  tradingEnabled?: boolean;
  balances?: {
    currency: string;
    posted: string;
    reserved: string;
    available: string;
    updatedAt: string;
  }[];
  currentPositions?: {
    instrumentId: number;
    ticker: string;
    name: string;
    currency: string;
    owned: string;
    reserved: string;
    available: string;
    updatedAt: string;
  }[];
  postedCash: number;
  currency: string | null;
  availableCash: number | null;
  positions: Position[];
  retrievedAt: string;
  limitation: string;
}
export interface PortfolioValuation {
  currency: 'USD';
  status: 'COMPLETE' | 'UNAVAILABLE';
  totalValue: string | null;
  holdingsValue: string | null;
  cashBalance: string | null;
  snapshotAt: string;
  quoteAsOf: string | null;
  unpricedTickers: string[];
}

export interface Order {
  executionMode?: string;
  recordKind?: string;
  exactQuantity?: string;
  submittedAt?: string;
  acceptedAt?: string | null;
  rejectionStage?: string;
  version?: number;
  fill?: Fill | null;
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
export interface RetainedClientQuote {quoteId:string;stage:string;applicableSide:string;price:string|null;currency:string|null;source:string|null;providerTime:string|null;receivedAt:string|null;stale:boolean|null;usedForFill:boolean;}
export interface OrderDetail {
  quotes?:RetainedClientQuote[];
  quoteCoverage?:string;
  eventTotal?:number;
  evidenceChecks?:{code:string;state:string;explanation:string}[];
  fills?: Fill[];
  events?: OrderEvent[];
  decisions?: { stage: string; code: string; explanation: string; passed: boolean; checks?:{code:string;result:string}[];checkCoverage?:string }[];
  reservation?: { resource: string; amount: string; state: string };
  order: Order;
  evidenceCompleteness: string;
  explanation: string;
}
export interface TradingAvailability {
  buyAllowed?:boolean;
  sellAllowed?:boolean;
  code?: string;
  mode?: string;
  policyVersion?: string;
  quantityUnit?: string;
  venue?: string;
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

  portfolioValuation() {
    return this.http.get<PortfolioValuation>(this.url('/api/workspace/client/portfolio/valuation'));
  }

  orders(params: Record<string, string | number> = {}) {
    return this.http.get<Page<Order>>(this.url('/api/orders'), { params });
  }

  orderEvents(id:string,page=0) {return this.http.get<Page<OrderEvent>>(this.url('/api/orders/'+encodeURIComponent(id)+'/events'),{params:{page,pageSize:100}});}
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

  availability(instrumentId?: string) {
    return this.http.get<TradingAvailability>(
      this.url('/api/workspace/client/trading-availability'),
      { params: instrumentId ? { instrumentId } : {} },
    );
  }

  preview(command: OrderCommand) {
    return this.http.post<OrderPreview>(this.url('/api/orders/preview'), command);
  }

  submit(command: OrderCommand, key: string) {
    return this.http.post<OrderReceipt>(this.url('/api/orders'), command, {
      headers: { 'Idempotency-Key': key },
    });
  }

  byRequest(key: string) {
    return this.http.get<OrderReceipt>(
      this.url('/api/orders/by-request/' + encodeURIComponent(key)),
    );
  }

  changes(cursor: number) {
    return this.http.get<OrderChanges>(this.url('/api/workspace/client/order-events/stream'), {
      params: { cursor },
    });
  }

  // Staff endpoints deliberately have no data contract until source and purpose policies are approved.
  staffAvailability(path: string) {
    return this.http.get<never>(this.url(path));
  }

  private url(path: string) {
    return buildApiUrl(path, this.platform);
  }
}

export interface OrderCommand {
  instrumentId: number;
  side: 'BUY' | 'SELL';
  quantity: string;
  orderType: 'MARKET';
}
export interface OrderPreview {
  eligible: boolean;
  code: string;
  reason: string;
  executionMode: string;
  policyVersion: string;
  currency: string;
  quantityUnit: string;
  venue: string;
  price: string | null;
  gross: string | null;
  fee: string;
  availableCash: string;
  availableQuantity: string;
  projectedCash: string | null;
  projectedQuantity: string | null;
  quoteTime: string;
  expiresAt: string;
  source: string;
  message: string;
}
export interface OrderReceipt {
  orderId: number;
  status: string;
  acceptedAt: string | null;
  version: number;
  message: string;
  code: string | null;
  rejectionStage: string | null;
}
export interface Fill {
  fillId: string;
  quantity: string;
  price: string;
  gross: string;
  fee: string;
  currency: string;
  bookedAt: string;
  executionReference: string;
  postingGroup: string;
}
export interface OrderEvent {
  sequence?:number;
  eventId: string;
  orderId: number;
  version: number;
  cursor: number;
  status: string;
  reason?: string;
  occurredAt?: string;
}
export interface OrderChanges {
  items: OrderEvent[];
  cursor: number;
  reset: boolean;
  hasMore: boolean;
}

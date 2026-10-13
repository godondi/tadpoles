import { Injectable, PLATFORM_ID, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { buildApiUrl } from './api-url';

export interface InstrumentDto {
  instrumentId: number;
  instrumentName: string;
  ticker: string;
  currency: string;
  assetClass: string;
  securityType: string;
  isActive: boolean;
}

export interface ClientTradeDto {
  tradeId: number;
  clientId: number;
  instrumentId: number;
  submittedByUserId: number | null;
  approvedByUserId: number | null;
  tradeType: string;
  quantity: number;
  price: number;
  tradeDate: string;
  status: string;
  executedAt: string | null;
  reason: string | null;
}

export interface ClientHoldingDto {
  holdingId: number;
  clientId: number;
  instrumentId: number;
  quantity: number;
  asOfDate: string;
}

export interface AuditLogDto {
  auditLogId: number;
  userId: number | null;
  entityName: string;
  entityId: string | null;
  actionType: string;
  createdAt: string;
}

@Injectable({ providedIn: 'root' })
export class WorkspaceApiService {
  private readonly http = inject(HttpClient);
  private readonly platformId = inject(PLATFORM_ID);

  listInstruments(): Observable<{ instruments: InstrumentDto[] }> {
    return this.http.get<{ instruments: InstrumentDto[] }>(buildApiUrl('/api/instruments', this.platformId));
  }

  getInstrument(instrumentId: number): Observable<InstrumentDto> {
    return this.http.get<InstrumentDto>(buildApiUrl(`/api/instruments/${instrumentId}`, this.platformId));
  }

  getClientBalance(clientId: number): Observable<{ clientId: number; cashBalance: number }> {
    return this.http.get<{ clientId: number; cashBalance: number }>(buildApiUrl(`/api/clients/${clientId}/balance`, this.platformId));
  }

  listClientHoldings(clientId: number): Observable<{ holdings: ClientHoldingDto[] }> {
    return this.http.get<{ holdings: ClientHoldingDto[] }>(buildApiUrl(`/api/clients/${clientId}/holdings`, this.platformId));
  }

  listClientTrades(clientId: number): Observable<{ trades: ClientTradeDto[] }> {
    return this.http.get<{ trades: ClientTradeDto[] }>(buildApiUrl(`/api/clients/${clientId}/trades`, this.platformId));
  }

  getClientTrade(clientId: number, tradeId: number): Observable<ClientTradeDto> {
    return this.http.get<ClientTradeDto>(buildApiUrl(`/api/clients/${clientId}/trades/${tradeId}`, this.platformId));
  }

  listOperationsOrders(): Observable<{ trades: ClientTradeDto[] }> {
    return this.http.get<{ trades: ClientTradeDto[] }>(buildApiUrl('/api/operations/orders', this.platformId));
  }

  getOperationsOrder(orderId: number): Observable<ClientTradeDto> {
    return this.http.get<ClientTradeDto>(buildApiUrl(`/api/operations/orders/${orderId}`, this.platformId));
  }

  listComplianceInvestigations(): Observable<{ trades: ClientTradeDto[] }> {
    return this.http.get<{ trades: ClientTradeDto[] }>(buildApiUrl('/api/compliance/investigations', this.platformId));
  }

  getComplianceEvidence(orderId: number): Observable<{ trade: ClientTradeDto; auditLogs: AuditLogDto[] }> {
    return this.http.get<{ trade: ClientTradeDto; auditLogs: AuditLogDto[] }>(buildApiUrl(`/api/compliance/orders/${orderId}/evidence`, this.platformId));
  }
}

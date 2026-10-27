import { EvidencePage } from './operations-api.service';
import { Injectable, PLATFORM_ID, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { buildApiUrl } from './api-url';
export interface PolicyForm {
  ticker: string;
  name: string;
  providerSymbol: string;
  venue: string;
  quantityStep: string;
  maxQuantity: string;
  maxNotional: string;
  quoteMaxAgeSeconds: number;
  active: boolean;
  buyAllowed: boolean;
  sellAllowed: boolean;
  reason: string;
  revision: number;
}
export interface PolicyRow extends PolicyForm {
  instrumentId: number;
  currency: string;
  validatedAt: string | null;
}
export interface SymbolCheck {
  recognized: boolean;
  symbol: string;
  currency: string;
  source: string;
  asOf: string;
  quoteUsable: boolean;
  eligible: boolean;
  reason: string;
  chartAvailable: boolean;
  chartReason: string;
  disclaimer: string;
}
@Injectable({ providedIn: 'root' })
export class InstrumentPolicyApi {
  private http = inject(HttpClient);
  private platform = inject(PLATFORM_ID);

  private url(path = '') {
    return buildApiUrl('/api/workspace/operations/instruments' + path, this.platform);
  }

  list(search = '', page = 0) {
    return this.http.get<PolicyRow[]>(this.url(), { params: { search, page } });
  }

  get(id: number) {
    return this.http.get<PolicyRow>(this.url('/' + id));
  }

  history(id: number, page = 0) {
    return this.http.get<EvidencePage>(this.url('/' + id + '/history'), { params: { page } });
  }

  test(symbol: string) {
    return this.http.get<SymbolCheck>(this.url('/test'), { params: { symbol } });
  }

  save(body: PolicyForm, id?: number) {
    return id
      ? this.http.put<PolicyRow>(this.url('/' + id), body)
      : this.http.post<PolicyRow>(this.url(), body);
  }
}

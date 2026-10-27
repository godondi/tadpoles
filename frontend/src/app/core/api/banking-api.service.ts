import { Injectable, PLATFORM_ID, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { buildApiUrl } from './api-url';
export interface BankReference {
  bankId: string;
  nickname: string;
  maskedAccount: string;
  defaultAccount: boolean;
  version: number;
}
export interface BankRequest {
  nickname: string;
  routingNumber?: string;
  accountNumber?: string;
  defaultAccount: boolean;
  version?: number;
}
export interface MovementCommand {
  bankId: string;
  direction: 'DEPOSIT' | 'WITHDRAW';
  amount: string;
}
export interface CashMovement extends MovementCommand {
  movementId: string;
  currency: string;
  status: string;
  reason: string | null;
  bankSnapshot: string;
  recordedAt: string;
}
@Injectable({ providedIn: 'root' })
export class BankingApi {
  private readonly http = inject(HttpClient);
  private readonly platform = inject(PLATFORM_ID);

  private url(path: string) {
    return buildApiUrl('/api/workspace/client/' + path, this.platform);
  }

  banks() {
    return this.http.get<BankReference[]>(this.url('banks'));
  }

  save(body: BankRequest, id?: string) {
    return id
      ? this.http.put<BankReference>(this.url('banks/' + encodeURIComponent(id)), body)
      : this.http.post<BankReference>(this.url('banks'), body);
  }

  remove(bank: BankReference) {
    return this.http.delete<void>(this.url('banks/' + encodeURIComponent(bank.bankId)), {
      params: { version: bank.version },
    });
  }

  move(body: MovementCommand, key: string) {
    return this.http.post<CashMovement>(this.url('cash-movements'), body, {
      headers: { 'Idempotency-Key': key },
    });
  }

  recover(key: string) {
    return this.http.get<CashMovement>(
      this.url('cash-movements/by-request/' + encodeURIComponent(key)),
    );
  }

  history(page = 0) {
    return this.http.get<CashMovement[]>(this.url('cash-movements'), {
      params: { page, pageSize: 20 },
    });
  }
}

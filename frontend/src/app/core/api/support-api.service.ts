import { Injectable, PLATFORM_ID, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Subject, catchError, takeUntil, throwError } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { buildApiUrl } from './api-url';
import { Page } from './workspace-api.service';
export type CaseStatus = 'OPEN' | 'IN_REVIEW' | 'AWAITING_CLIENT' | 'RESOLVED';
export interface CaseEvent {
  id: number;
  version: number;
  kind: string;
  authorRole: string;
  body: string;
  previousStatus: CaseStatus | null;
  status: CaseStatus;
  recordedAt: string;
}
export interface SupportCase {
  reference: string;
  orderId: number | null;
  category: string;
  subject: string;
  status: CaseStatus;
  version: number;
  createdAt: string;
  updatedAt: string;
  detailUrl: string;
}
export interface CaseDetail extends SupportCase {
  events: CaseEvent[];
  totalEvents: number;
  page: number;
  pageSize: number;
}
export interface CaseUpdate {
  id: number;
  reference: string;
  subject: string;
  kind: string;
  status: CaseStatus;
  recordedAt: string;
}
export interface CaseUpdates {
  items: CaseUpdate[];
  cursor: number;
  hasMore: boolean;
  reset: boolean;
  unread: number;
}
export interface NewCase {
  category: string;
  subject: string;
  description: string;
  orderId?: number;
}
export const CASE_CATEGORIES = [
  'ORDER_QUESTION',
  'ACCOUNT_REVIEW',
  'HISTORICAL_RECORD',
  'TRADING_AVAILABILITY',
  'OTHER',
] as const;
@Injectable({ providedIn: 'root' })
export class SupportApiService {
  readonly denied = new Subject<void>();
  private http = inject(HttpClient);
  private platform = inject(PLATFORM_ID);
  private auth = inject(AuthService);
  private url(staff = false) {
    return buildApiUrl(staff ? '/api/operations/cases' : '/api/support/cases', this.platform);
  }
  list(staff: boolean, status = '', search = '', page = 0) {
    return this.http
      .get<Page<SupportCase>>(this.url(staff), {
        params: new HttpParams()
          .set('page', page)
          .set('pageSize', 20)
          .set('search', search)
          .set('status', status)
          .delete('status', ''),
      })
      .pipe(
        takeUntil(this.auth.sessionChanged),
        takeUntil(this.denied),
        catchError((error) => {
          if ([401, 403].includes(error.status)) this.denied.next();
          return throwError(() => error);
        }),
      );
  }
  detail(staff: boolean, reference: string, page = 0) {
    return this.http
      .get<CaseDetail>(this.url(staff) + '/' + encodeURIComponent(reference), {
        params: { page, pageSize: 20 },
      })
      .pipe(
        takeUntil(this.auth.sessionChanged),
        takeUntil(this.denied),
        catchError((error) => {
          if ([401, 403].includes(error.status)) this.denied.next();
          return throwError(() => error);
        }),
      );
  }
  create(key: string, body: NewCase) {
    return this.http
      .post<SupportCase>(this.url(), body, { headers: { 'Idempotency-Key': key } })
      .pipe(
        takeUntil(this.auth.sessionChanged),
        takeUntil(this.denied),
        catchError((error) => {
          if ([401, 403].includes(error.status)) this.denied.next();
          return throwError(() => error);
        }),
      );
  }
  recover(key: string) {
    return this.http.get<SupportCase>(this.url() + '/by-request/' + encodeURIComponent(key)).pipe(
      takeUntil(this.auth.sessionChanged),
      takeUntil(this.denied),
      catchError((error) => {
        if ([401, 403].includes(error.status)) this.denied.next();
        return throwError(() => error);
      }),
    );
  }
  write(
    staff: boolean,
    reference: string,
    action: string,
    key: string,
    body: { expectedVersion: number; body: string; status?: string },
  ) {
    return this.http
      .post<{ reference: string; version?: number; status?: CaseStatus }>(
        this.url(staff) + '/' + encodeURIComponent(reference) + '/' + action,
        body,
        { headers: { 'Idempotency-Key': key } },
      )
      .pipe(
        takeUntil(this.auth.sessionChanged),
        takeUntil(this.denied),
        catchError((error) => {
          if ([401, 403].includes(error.status)) this.denied.next();
          return throwError(() => error);
        }),
      );
  }
  notes(reference: string, page = 0) {
    return this.http
      .get<Page<{ id: string; actor: number; body: string; recordedAt: string }>>(
        this.url(true) + '/' + encodeURIComponent(reference) + '/notes',
        { params: { page, pageSize: 20 } },
      )
      .pipe(
        takeUntil(this.auth.sessionChanged),
        takeUntil(this.denied),
        catchError((error) => {
          if ([401, 403].includes(error.status)) this.denied.next();
          return throwError(() => error);
        }),
      );
  }
  recoverWrite(staff: boolean, reference: string, key: string) {
    return this.http
      .get(
        this.url(staff) +
          '/' +
          encodeURIComponent(reference) +
          '/requests/' +
          encodeURIComponent(key),
      )
      .pipe(
        takeUntil(this.auth.sessionChanged),
        takeUntil(this.denied),
        catchError((error) => {
          if ([401, 403].includes(error.status)) this.denied.next();
          return throwError(() => error);
        }),
      );
  }
  updates(cursor = 0) {
    return this.http
      .get<CaseUpdates>(buildApiUrl('/api/support/updates', this.platform), { params: { cursor } })
      .pipe(
        takeUntil(this.auth.sessionChanged),
        takeUntil(this.denied),
        catchError((error) => {
          if ([401, 403].includes(error.status)) this.denied.next();
          return throwError(() => error);
        }),
      );
  }
  markRead(cursor: number) {
    return this.http
      .post<{ unread: number }>(buildApiUrl('/api/support/updates/read', this.platform), { cursor })
      .pipe(
        takeUntil(this.auth.sessionChanged),
        takeUntil(this.denied),
        catchError((error) => {
          if ([401, 403].includes(error.status)) this.denied.next();
          return throwError(() => error);
        }),
      );
  }
}

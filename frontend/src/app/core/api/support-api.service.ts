import { Injectable, PLATFORM_ID, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Subject, catchError, takeUntil, throwError } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { buildApiUrl } from './api-url';
import { Page } from './workspace-api.service';
export type CaseStatus = 'OPEN' | 'IN_REVIEW' | 'AWAITING_CLIENT' | 'RESOLVED';
export type CaseQueueView =
  | 'ACTIONABLE'
  | 'ALL'
  | 'UNASSIGNED'
  | 'MINE'
  | 'AWAITING_CLIENT'
  | 'AWAITING_REVIEW'
  | 'RESOLVED';
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
  assignee?: number | null;
  assigneeLabel?: string | null;
  priority?: string;
  nextAction?: string | null;
  dueAt?: string | null;
  effectiveCategory?: string;
  workflowVersion?: number;
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
  workflowEnabled?: boolean;
  actions?: { reply: boolean; note: boolean; transitions: CaseStatus[] };
  events: CaseEvent[];
  totalEvents: number;
  page: number;
  pageSize: number;
}
export interface SupportCasePage extends Page<SupportCase> {
  workflowEnabled?: boolean;
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
  intakeSchemaVersion?: number;
  intake?: Record<string, string | number>;
}
export interface IntakePolicy {
  intakeEnabled: boolean;
  schemaVersion: number;
  categories: { code: string; reasons: string[] }[];
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
  intakePolicy() {
    return this.http
      .get<IntakePolicy>(this.url() + '/intake-policy')
      .pipe(takeUntil(this.auth.sessionChanged));
  }
  list(
    staff: boolean,
    status = '',
    search = '',
    page = 0,
    filters: {
      assignee?: number;
      priority?: string;
      category?: string;
      view?: CaseQueueView;
      overdueOnly?: boolean;
    } = {},
  ) {
    let params = new HttpParams()
      .set('page', page)
      .set('pageSize', 20)
      .set('search', search)
      .set('status', status)
      .delete('status', '');
    if (staff && filters.assignee !== undefined) params = params.set('assignee', filters.assignee);
    if (staff && filters.priority) params = params.set('priority', filters.priority);
    if (staff && filters.category) params = params.set('category', filters.category);
    if (staff && filters.view) params = params.set('view', filters.view);
    if (staff && filters.overdueOnly) params = params.set('overdueOnly', true);
    return this.http
      .get<SupportCasePage>(this.url(staff), {
        params,
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

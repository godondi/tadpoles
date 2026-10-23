import { Injectable, PLATFORM_ID, inject, signal } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, Subject, catchError, defer, filter, takeUntil, throwError, tap } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { buildApiUrl } from '../../core/api/api-url';
import {
  ComplianceAccessContextRequest,
  ComplianceAccessPolicy,
  ComplianceContext,
  ComplianceExportPage,
  ComplianceExportRecord,
  ComplianceEvidenceSection,
  ComplianceInvestigationDetail,
  ComplianceSearchFilters,
  ComplianceSearchPage,
} from './compliance.models';

export class ComplianceContextRequiredError extends Error {
  readonly status = 409;

  constructor() {
    super('Start an approved purpose context before requesting evidence.');
  }
}

@Injectable({ providedIn: 'root' })
export class ComplianceApi {
  readonly context = signal<ComplianceContext | null>(null);
  readonly changed = new Subject<void>();
  readonly contextMessage = signal('');
  private readonly http = inject(HttpClient);
  private readonly platform = inject(PLATFORM_ID);
  private readonly auth = inject(AuthService);
  private generation = 0;
  private expiry?: ReturnType<typeof setTimeout>;

  constructor() {
    this.auth.sessionChanged.subscribe(() => this.clear());
  }

  policy(): Observable<ComplianceAccessPolicy> {
    return this.http.get<ComplianceAccessPolicy>(this.url('/api/compliance/access-policy'), {
      headers: { 'Cache-Control': 'no-store' },
    });
  }

  createContext(request: ComplianceAccessContextRequest): Observable<ComplianceContext> {
    this.clear();
    const generation = this.generation;
    return this.http
      .post<ComplianceContext>(this.url('/api/compliance/access-contexts'), request, {
        headers: { 'Cache-Control': 'no-store' },
      })
      .pipe(
        filter(() => generation === this.generation),
        tap((context) => {
          this.context.set(context);
          this.expiry = setTimeout(
            () => this.clear('This purpose context expired. Start a new context.'),
            Math.max(0, Date.parse(context.expiresAt) - Date.now()),
          );
          this.changed.next();
        }),
      );
  }

  clear(message = '') {
    this.generation++;
    clearTimeout(this.expiry);
    this.context.set(null);
    this.contextMessage.set(message);
    this.changed.next();
  }

  search(filters: ComplianceSearchFilters): Observable<ComplianceSearchPage> {
    return this.withContext((headers) =>
      this.http.get<ComplianceSearchPage>(this.url('/api/compliance/investigations'), {
        headers,
        params: this.params(filters),
      }),
    );
  }

  investigation(id: number): Observable<ComplianceInvestigationDetail> {
    return this.withContext((headers) =>
      this.http.get<ComplianceInvestigationDetail>(
        this.url(`/api/compliance/orders/${id}/evidence`),
        { headers },
      ),
    );
  }

  section(
    id: number,
    section: string,
    page: number,
    pageSize = 25,
  ): Observable<ComplianceEvidenceSection> {
    return this.withContext((headers) =>
      this.http.get<ComplianceEvidenceSection>(
        this.url(`/api/compliance/orders/${id}/evidence/${encodeURIComponent(section)}`),
        {
          headers,
          params: { page, pageSize },
        },
      ),
    );
  }

  evidence(page = 0, pageSize = 25): Observable<ComplianceExportPage> {
    return this.withContext((headers) =>
      this.http.get<ComplianceExportPage>(this.url('/api/compliance/evidence'), {
        headers,
        params: { page, pageSize },
      }),
    );
  }

  createExport(orderId: number, idempotencyKey: string): Observable<ComplianceExportRecord> {
    const context = this.context();
    if (!context || Date.parse(context.expiresAt) <= Date.now())
      return throwError(() => new ComplianceContextRequiredError());
    if (!context.permissions.includes('EXPORT_CREATE')) {
      return throwError(() => new HttpErrorResponse({ status: 403 }));
    }
    return this.withContext((headers) =>
      this.http.post<ComplianceExportRecord>(
        this.url('/api/compliance/evidence/exports'),
        { orderId },
        {
          headers: {
            ...headers,
            'Idempotency-Key': idempotencyKey,
          },
        },
      ),
    );
  }

  downloadExport(requestReference: string): Observable<Blob> {
    const context = this.context();
    if (!context || Date.parse(context.expiresAt) <= Date.now())
      return throwError(() => new ComplianceContextRequiredError());
    if (!context.permissions.includes('EXPORT_DOWNLOAD')) {
      return throwError(() => new HttpErrorResponse({ status: 403 }));
    }
    return this.withContext((headers) =>
      this.http.get(
        this.url(
          `/api/compliance/evidence/exports/${encodeURIComponent(requestReference)}/download`,
        ),
        {
          headers,
          responseType: 'blob',
        },
      ),
    );
  }

  receipt(requestReference: string): Observable<ComplianceExportRecord> {
    return this.withContext((headers) =>
      this.http.get<ComplianceExportRecord>(
        this.url(`/api/compliance/evidence/exports/${encodeURIComponent(requestReference)}`),
        { headers },
      ),
    );
  }

  actorKey(): string | null {
    const id = this.auth.identity?.()?.userId;
    return Number.isSafeInteger(id) && id! > 0 ? String(id) : null;
  }

  private requiredContext(): ComplianceContext {
    const context = this.context();
    if (!context || Date.parse(context.expiresAt) <= Date.now())
      throw new ComplianceContextRequiredError();
    return context;
  }

  private withContext<T>(
    request: (headers: Record<string, string>) => Observable<T>,
  ): Observable<T> {
    return defer(() => {
      const generation = this.generation;
      let context: ComplianceContext;
      try {
        context = this.requiredContext();
      } catch (error) {
        return throwError(() => error);
      }
      const current = () =>
        generation === this.generation && this.context()?.contextId === context.contextId;
      return request({
        'Cache-Control': 'no-store',
        'X-Compliance-Context': context.contextId,
      }).pipe(
        filter(current),
        catchError((error: HttpErrorResponse) => {
          if (!current()) return new Observable<T>((subscriber) => subscriber.complete());
          if ([401, 403].includes(error.status) || error.error?.code === 'CONTEXT_INVALID')
            this.clear(
              error.status === 401
                ? 'Your session expired. Sign in again before reviewing evidence.'
                : 'This purpose context is no longer authorized. Start a new context.',
            );
          return throwError(() => error);
        }),
        takeUntil(this.changed),
      );
    });
  }

  private params(filters: ComplianceSearchFilters): Record<string, string | number> {
    return Object.fromEntries(
      Object.entries(filters).filter(([, value]) => value !== undefined && value !== ''),
    ) as Record<string, string | number>;
  }

  private url(path: string) {
    return buildApiUrl(path, this.platform);
  }
}

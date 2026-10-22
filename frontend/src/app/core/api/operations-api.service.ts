import { Injectable, PLATFORM_ID, inject, signal } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, Subject, catchError, filter, tap, throwError } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { buildApiUrl } from './api-url';

export interface OperationsContext {
  contextId: string;
  expiresAt: string;
  purposeCode: string;
  referenceType?: 'MANAGED' | 'EXTERNAL';
  caseReference?: string;
}
export interface AccessPolicy {
  configured: boolean;
  purposes: string[];
  policyVersion: string;
}
export interface OperationsOrder {
  orderId: number;
  accountReference: string;
  instrumentId: number;
  ticker: string;
  side: string;
  quantity: string;
  currency: string;
  recordKind: string;
  executionMode: string;
  status: string;
  submittedAt: string | null;
  acceptedAt: string | null;
  updatedAt: string | null;
  version: number;
  rejectionStage: string | null;
  rejectionCode: string | null;
  reason: string;
  evidenceStatus: string;
  queueCategory: string | null;
  deadline: string | null;
  nextAttempt: string | null;
  leaseUntil: string | null;
  attempts: number;
}
export interface OrderPage {
  items: OperationsOrder[];
  nextCursor: string | null;
  hasMore: boolean;
  retrievedAt: string;
}
export interface EvidenceRow {
  reference: string;
  recordedAt: string | null;
  values: Record<string, unknown>;
}
export interface EvidencePage {
  items: EvidenceRow[];
  page: number;
  hasMore: boolean;
}
export interface Investigation {
  order: OperationsOrder;
  submittedActor: string;
  acceptedPolicy: unknown;
  currentPolicy: unknown;
  snapshotAt: string;
  evidenceStatus: string;
  findings: { code: string; explanation: string }[];
  sections: Record<string, EvidencePage>;
  exportAllowed: boolean;
  orderingNote: string;
  historicalContext: string;
}
export interface ExportReceipt {
  exportId: string;
  state: string;
  snapshotAt: string;
  expiresAt: string;
  orderVersion: number;
  digest: string;
}
export interface ReconciliationView {
  runId: string | null;
  checkedAt: string | null;
  status: string;
  coverage: string;
  items: EvidenceRow[];
  hasMore: boolean;
  page: number;
}
export interface Readiness {
  checkedAt: string;
  schemaReady: boolean;
  referenceDataReady: boolean;
  runtimePermissions: { operationalAccess: boolean; evidenceProtected: boolean };
  workerHealth: {
    status: string;
    scope: string;
    lastPollAt: string | null;
    lastCompletedPollAt: string | null;
    lastFailureAt: string | null;
    failedJobs: number;
  };
  providerHealth: {
    status: string;
    scope: string;
    lastObservedAt: string | null;
    lastSuccessAt: string | null;
    failureCategory: string | null;
    note: string;
  };
  admissionEnabled: boolean;
  workerEnabled: boolean;
  providerConfigured: boolean;
  enabledPolicies: number;
  outboxDelivery: string;
  note: string;
}

@Injectable({ providedIn: 'root' })
export class OperationsApi {
  private readonly http = inject(HttpClient);
  private readonly platform = inject(PLATFORM_ID);
  readonly context = signal<OperationsContext | null>(null);
  readonly changed = new Subject<void>();
  readonly contextMessage = signal('');
  private generation = 0;
  private expiry?: ReturnType<typeof setTimeout>;

  constructor() {
    inject(AuthService).sessionChanged.subscribe(() => this.clear());
  }
  private url(path: string) {
    return buildApiUrl('/api/operations' + path, this.platform);
  }
  clear(message = '') {
    this.generation++;
    clearTimeout(this.expiry);
    this.context.set(null);
    this.contextMessage.set(message);
    this.changed.next();
  }
  policy() {
    return this.http.get<AccessPolicy>(this.url('/access-policy'));
  }
  createContext(purposeCode: string, caseReference: string, referenceType: 'MANAGED' | 'EXTERNAL' = 'EXTERNAL') {
    this.clear();
    const generation = this.generation;
    return this.http
      .post<OperationsContext>(this.url('/access-contexts'), { purposeCode, caseReference, referenceType })
      .pipe(
        filter(() => generation === this.generation),
        tap((context) => {
          this.context.set(context);
          this.expiry = setTimeout(
            () => this.clear('Your access purpose has expired. Start a new context.'),
            Math.max(0, Date.parse(context.expiresAt) - Date.now()),
          );
          this.changed.next();
        }),
      );
  }
  private headers() {
    return { 'X-Operations-Context': this.context()?.contextId ?? '' };
  }
  private protected<T>(request: Observable<T>) {
    return request.pipe(
      catchError((error: HttpErrorResponse) => {
        if ([401, 403].includes(error.status))
          this.clear('Access expired or permission changed. Displayed evidence has been cleared.');
        return throwError(() => error);
      }),
    );
  }
  orders(filters: Record<string, string>) {
    return this.protected(
      this.http.get<OrderPage>(this.url('/orders'), { headers: this.headers(), params: filters }),
    );
  }
  detail(id: number) {
    return this.protected(
      this.http.get<Investigation>(this.url('/orders/' + id), { headers: this.headers() }),
    );
  }
  section(id: number, section: string, page: number) {
    return this.protected(
      this.http.get<EvidencePage>(this.url(`/orders/${id}/${section}`), {
        headers: this.headers(),
        params: { page },
      }),
    );
  }
  reconciliation(runId = 'latest', page = 0) {
    return this.protected(
      this.http.get<ReconciliationView>(this.url('/reconciliations/' + runId), {
        headers: this.headers(),
        params: { page },
      }),
    );
  }
  migration(page = 0) {
    return this.protected(
      this.http.get<EvidencePage>(this.url('/migration-exceptions'), {
        headers: this.headers(),
        params: { page },
      }),
    );
  }
  readiness() {
    return this.http.get<Readiness>(this.url('/readiness'));
  }
  createExport(orderId: number, key: string) {
    return this.protected(
      this.http.post<ExportReceipt>(
        this.url('/evidence-exports'),
        { orderId },
        { headers: { ...this.headers(), 'Idempotency-Key': key } },
      ),
    );
  }
  associateCaseExport(exportId:string) {
    const context=this.context();
    if(context?.referenceType!=='MANAGED'||!context.caseReference)return throwError(()=>new Error('A managed case context is required.'));
    return this.protected(this.http.post<{digest:string}>(this.url('/cases/'+encodeURIComponent(context.caseReference)+'/exports'),{exportId},{headers:this.headers()}));
  }
  download(id: string) {
    return this.protected(
      this.http.get(this.url(`/evidence-exports/${id}/download`), {
        headers: this.headers(),
        responseType: 'blob',
      }),
    );
  }
}

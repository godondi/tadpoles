import { Injectable, PLATFORM_ID, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, Subject, catchError, takeUntil, throwError } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { buildApiUrl } from './api-url';

export interface WorkflowTask {
  code: string;
  label: string;
  state: 'PENDING' | 'COMPLETED' | 'UNAVAILABLE' | 'NOT_APPLICABLE';
  reason?: string;
  evidenceKind?: string;
  evidenceReference?: string;
}
export interface Workbench {
  reference: string;
  publicVersion: number;
  workflowVersion: number;
  checklistRevision: number;
  status: string;
  originalCategory: string;
  effectiveCategory: string;
  assignee: number | null;
  priority: string;
  nextAction: string | null;
  dueAt: string | null;
  responseTarget: string;
  intake: Record<string, unknown>;
  tasks: WorkflowTask[];
  definitions: { resolutions: string[] };
  actions: {
    reply: boolean;
    note: boolean;
    transitions: string[];
    workflow: boolean;
    resolve: boolean;
  };
  diagnosticsAllowed: { account: boolean; historical: boolean; order: boolean };
  mandatoryReferralPending: boolean;
  referrals: {
    reference: string;
    status: string;
    nextOwner: number;
    disposition?: string;
    clientSafeExplanation?: string;
    informationRequest?: string;
  }[];
  history: {
    version: number;
    actor: number | null;
    kind: string;
    facts: string;
    recordedAt: string;
  }[];
  historyPage: number;
}
export interface EligibleStaff {
  id: number;
  username: string;
}
export interface CaseExportPage {
  items: {
    exportId: string;
    orderId: number;
    digest: string;
    associatedAt: string;
    expiresAt: string;
    requester: number;
  }[];
  page: number;
  hasMore: boolean;
  note: string;
}
export interface ReviewCase {
  reference: string;
  status: string;
  version: number;
  reviewer: number;
  reasonCode: string;
  summary: string;
  orderId: number | null;
  createdAt: string;
  updatedAt: string;
  disposition?: string;
  findings?: string;
  clientSafeExplanation?: string;
  reviewers?: EligibleStaff[];
  actions?: { assign: boolean; transitions: string[] };
  history?: { version: number; actor: number; kind: string; facts: string; recordedAt: string }[];
}
@Injectable({ providedIn: 'root' })
export class CaseWorkflowApi {
  readonly denied = new Subject<void>();
  private readonly http = inject(HttpClient);
  private readonly platform = inject(PLATFORM_ID);
  private readonly auth = inject(AuthService);
  private url(path: string) {
    return buildApiUrl(path, this.platform);
  }
  private live<T>(source: Observable<T>) {
    return source.pipe(
      takeUntil(this.auth.sessionChanged),
      takeUntil(this.denied),
      catchError((error) => {
        if ([401, 403].includes(error.status)) this.denied.next();
        return throwError(() => error);
      }),
    );
  }
  private caseUrl(reference: string) {
    return `/api/operations/cases/${encodeURIComponent(reference)}`;
  }
  workbench(reference: string, page = 0) {
    return this.live(
      this.http.get<Workbench>(this.url(this.caseUrl(reference) + '/workbench'), {
        params: { page },
      }),
    );
  }
  staff(reference: string, reviewers = false) {
    return this.live(
      this.http.get<{ items: EligibleStaff[] }>(
        this.url(this.caseUrl(reference) + (reviewers ? '/reviewers' : '/assignees')),
      ),
    );
  }
  exports(reference: string, page = 0) {
    return this.live(
      this.http.get<CaseExportPage>(this.url(this.caseUrl(reference) + '/exports'), {
        params: { page },
      }),
    );
  }
  diagnostic(reference: string, kind: string, evidence: string, context?: string) {
    return this.live(
      this.http.get<Record<string, unknown>>(this.url(this.caseUrl(reference) + '/diagnostics'), {
        params: { kind, ...(evidence ? { evidence } : {}) },
        headers: context ? { 'X-Operations-Context': context } : {},
      }),
    );
  }
  command(reference: string, action: string, key: string, body: Record<string, unknown>) {
    const suffix = action === 'referral' ? '/referrals' : '/workflow/' + encodeURIComponent(action);
    return this.live(
      this.http.post<Record<string, unknown>>(this.url(this.caseUrl(reference) + suffix), body, {
        headers: { 'Idempotency-Key': key },
      }),
    );
  }
  recover(reference: string, key: string, compliance = false) {
    const path = compliance
      ? `/api/compliance/cases/${encodeURIComponent(reference)}/requests/`
      : this.caseUrl(reference) + '/workflow/requests/';
    return this.live(
      this.http.get<Record<string, unknown>>(this.url(path + encodeURIComponent(key))),
    );
  }
  reviews(status = '', page = 0) {
    return this.live(
      this.http.get<{ items: ReviewCase[]; total: number }>(this.url('/api/compliance/cases'), {
        params: { page, ...(status ? { status } : {}) },
      }),
    );
  }
  review(reference: string, page = 0) {
    return this.live(
      this.http.get<ReviewCase>(
        this.url('/api/compliance/cases/' + encodeURIComponent(reference)),
        { params: { page } },
      ),
    );
  }
  reviewCommand(reference: string, key: string, body: Record<string, unknown>) {
    return this.live(
      this.http.post<Record<string, unknown>>(
        this.url(`/api/compliance/cases/${encodeURIComponent(reference)}/actions`),
        body,
        { headers: { 'Idempotency-Key': key } },
      ),
    );
  }
}

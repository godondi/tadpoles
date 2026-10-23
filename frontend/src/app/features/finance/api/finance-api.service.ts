import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, PLATFORM_ID, inject } from '@angular/core';
import { Observable, catchError, map, throwError } from 'rxjs';
import { buildApiUrl } from '../../../core/api/api-url';

export interface ReportingScope {
  from: string;
  to: string;
}

export interface FinanceSummary {
  schemaVersion: string;
  status: 'CURRENT' | 'DELAYED' | 'PARTIAL';
  currency: 'USD';
  period: { from: string; to: string; timeZone: 'UTC' };
  dataAsOf: string;
  generatedAt?: string | null;
  definitionVersion: string;
  source: string;
  limitations: string[];
  totals: {
    settledDeposits: string;
    settledWithdrawals: string;
    grossBuyConsideration: string;
    grossSellConsideration: string;
    bookedFillCount: number;
    fees?: { amount: string; definition: string } | null;
  };
}

export interface ReconciliationException {
  kind: string;
  reference: string;
}

export interface ReconciliationReport {
  evidenceVersion?: number;
  runId: string | null;
  checkedAt?: string;
  matched?: boolean;
  exceptions?: ReconciliationException[];
  historicalExceptions?: number;
  message?: string;
  scope?: string;
}

@Injectable({ providedIn: 'root' })
export class FinanceApi {
  private readonly http = inject(HttpClient);
  private readonly platformId = inject(PLATFORM_ID);
  private readonly summaryUrl = buildApiUrl('/api/finance/summary', this.platformId);
  private readonly reconciliationUrl = buildApiUrl(
    '/api/finance/reconciliations',
    this.platformId,
  );

  /** Fetch an aggregate for the requested UTC dates and require the server to echo that scope. */
  summary(scope: ReportingScope): Observable<FinanceSummary> {
    const params = new HttpParams().set('from', scope.from).set('to', scope.to);
    return this.http.get<unknown>(this.summaryUrl, { params }).pipe(
      map((response) => {
        if (!isFinanceSummary(response, scope)) {
          throw new HttpErrorResponse({
            status: 502,
            statusText: 'Invalid reporting response',
            error: {
              message: 'The reporting response is invalid: period does not match the requested dates.',
            },
            url: this.summaryUrl,
          });
        }
        return response;
      }),
      catchError(normalizeFinanceError),
    );
  }

  latestReconciliation(): Observable<ReconciliationReport> {
    return this.http.get<ReconciliationReport>(this.reconciliationUrl).pipe(
      map((report) => {
        if (!isReconciliationReport(report)) {
          throw new HttpErrorResponse({
            status: 502,
            statusText: 'Invalid reconciliation response',
            error: { message: 'The reconciliation service returned an invalid response.' },
            url: this.reconciliationUrl,
          });
        }
        return report;
      }),
      catchError(normalizeFinanceError),
    );
  }

  runReconciliation(): Observable<ReconciliationReport> {
    return this.http.post<ReconciliationReport>(this.reconciliationUrl, {}).pipe(
      map((report) => {
        if (!isReconciliationReport(report) || !report.runId) {
          throw new HttpErrorResponse({
            status: 502,
            statusText: 'Invalid reconciliation response',
            error: { message: 'The reconciliation service returned an invalid run result.' },
            url: this.reconciliationUrl,
          });
        }
        return report;
      }),
      catchError(normalizeFinanceError),
    );
  }
}

function isFinanceSummary(value: unknown, requestedScope: ReportingScope): value is FinanceSummary {
  if (!isRecord(value) || !isRecord(value['period']) || !isRecord(value['totals'])) return false;
  const period = value['period'];
  const totals = value['totals'];
  const fees = totals['fees'];
  return (
    typeof value['schemaVersion'] === 'string' &&
    ['CURRENT', 'DELAYED', 'PARTIAL'].includes(String(value['status'])) &&
    value['currency'] === 'USD' &&
    period['from'] === requestedScope.from &&
    period['to'] === requestedScope.to &&
    period['timeZone'] === 'UTC' &&
    isUtcInstant(value['dataAsOf']) &&
    (value['generatedAt'] == null || isUtcInstant(value['generatedAt'])) &&
    typeof value['definitionVersion'] === 'string' &&
    typeof value['source'] === 'string' &&
    Array.isArray(value['limitations']) &&
    value['limitations'].every((item) => typeof item === 'string') &&
    isDecimalString(totals['settledDeposits']) &&
    isDecimalString(totals['settledWithdrawals']) &&
    isDecimalString(totals['grossBuyConsideration']) &&
    isDecimalString(totals['grossSellConsideration']) &&
    Number.isSafeInteger(totals['bookedFillCount']) &&
    (fees == null ||
      (isRecord(fees) && isDecimalString(fees['amount']) && typeof fees['definition'] === 'string'))
  );
}

function isReconciliationReport(value: unknown): value is ReconciliationReport {
  return (
    isRecord(value) &&
    (value['runId'] === null || typeof value['runId'] === 'string') &&
    (value['runId'] === null
      ? typeof value['message'] === 'string'
      : isUtcInstant(value['checkedAt']) &&
        typeof value['matched'] === 'boolean' &&
        Array.isArray(value['exceptions']) &&
        value['exceptions'].every(
          (item) =>
            isRecord(item) && typeof item['kind'] === 'string' && typeof item['reference'] === 'string',
        ) &&
        Number.isSafeInteger(value['historicalExceptions']) &&
        typeof value['scope'] === 'string')
  );
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function isDecimalString(value: unknown): value is string {
  return typeof value === 'string' && /^-?\d+(?:\.\d+)?$/.test(value);
}

function isUtcInstant(value: unknown): value is string {
  return typeof value === 'string' && /^\d{4}-\d{2}-\d{2}T.*(?:Z|\+00:00)$/.test(value);
}

function normalizeFinanceError(error: unknown): Observable<never> {
  if (!(error instanceof HttpErrorResponse)) return throwError(() => error);
  const body = error.error;
  const message = isRecord(body)
    ? typeof body['message'] === 'string'
      ? body['message']
      : typeof body['detail'] === 'string'
        ? body['detail']
        : undefined
    : typeof body === 'string'
      ? body
      : undefined;
  return throwError(
    () =>
      new HttpErrorResponse({
        error: { message: message ?? defaultErrorMessage(error.status) },
        status: error.status,
        statusText: error.statusText,
        url: error.url ?? undefined,
      }),
  );
}

function defaultErrorMessage(status: number): string {
  switch (status) {
    case 0:
      return 'The service could not be reached. Check your connection and try again.';
    case 401:
      return 'Your session has expired. Sign in again.';
    case 403:
      return 'You do not have access to this Finance information.';
    case 404:
      return 'This Finance record could not be found.';
    case 409:
      return 'The request conflicts with current Finance evidence. Refresh before continuing.';
    case 422:
      return 'The requested reporting scope is invalid.';
    case 429:
      return 'Too many requests. Wait before trying again.';
    case 503:
      return 'The approved reporting source is not available yet.';
    default:
      return status >= 500
        ? 'The Finance service is temporarily unavailable.'
        : 'The Finance request could not be completed.';
  }
}

import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { PLATFORM_ID } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FinanceApi } from './finance-api.service';

describe('FinanceApi', () => {
  let api: FinanceApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: PLATFORM_ID, useValue: 'browser' }],
    });
    api = TestBed.inject(FinanceApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('requests a typed summary with the selected UTC calendar scope', () => {
    api.summary({ from: '2026-10-01', to: '2026-10-22' }).subscribe();
    const request = http.expectOne((candidate) => candidate.url === '/api/finance/summary');
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('from')).toBe('2026-10-01');
    expect(request.request.params.get('to')).toBe('2026-10-22');
    request.flush({
      schemaVersion: 'finance-summary-v1',
      status: 'CURRENT',
      currency: 'USD',
      period: { from: '2026-10-01', to: '2026-10-22', timeZone: 'UTC' },
      dataAsOf: '2026-10-22T18:00:00Z',
      generatedAt: '2026-10-22T18:01:00Z',
      definitionVersion: 'draft-v1',
      source: 'approved reporting source',
      limitations: [],
      totals: {
        settledDeposits: '100.00',
        settledWithdrawals: '0.00',
        grossBuyConsideration: '25.00',
        grossSellConsideration: '0.00',
        bookedFillCount: 1,
        fees: { amount: '0.00', definition: 'Project fee' },
      },
    });
  });

  it('rejects a valid summary whose returned period differs from the requested dates', () => {
    let error: unknown;
    api.summary({ from: '2026-10-01', to: '2026-10-22' }).subscribe({
      error: (value) => (error = value),
    });
    http.expectOne((candidate) => candidate.url === '/api/finance/summary').flush({
      schemaVersion: 'finance-summary-v1',
      status: 'CURRENT',
      currency: 'USD',
      period: { from: '2026-10-02', to: '2026-10-22', timeZone: 'UTC' },
      dataAsOf: '2026-10-22T18:00:00Z',
      generatedAt: '2026-10-22T18:01:00Z',
      definitionVersion: 'finance-summary-v1',
      source: 'primary-postgresql-ledger',
      limitations: [],
      totals: {
        settledDeposits: '100.00',
        settledWithdrawals: '0.00',
        grossBuyConsideration: '25.00',
        grossSellConsideration: '0.00',
        bookedFillCount: 1,
      },
    });
    expect(error).toBeInstanceOf(HttpErrorResponse);
    expect((error as HttpErrorResponse).status).toBe(502);
    expect((error as HttpErrorResponse).error.message).toContain('period does not match');
  });

  it('keeps the current reconciliation GET and POST contracts unchanged', () => {
    api.latestReconciliation().subscribe();
    const latest = http.expectOne('/api/finance/reconciliations');
    expect(latest.request.method).toBe('GET');
    latest.flush({ runId: null, message: 'No stored run.' });

    api.runReconciliation().subscribe();
    const run = http.expectOne('/api/finance/reconciliations');
    expect(run.request.method).toBe('POST');
    expect(run.request.body).toEqual({});
    run.flush({
      evidenceVersion: 2,
      runId: 'run-1',
      checkedAt: '2026-10-22T18:00:00Z',
      matched: true,
      exceptions: [],
      historicalExceptions: 1,
      scope: 'Internal records only',
    });
  });

  it('normalizes Spring message and FastAPI detail without losing the HTTP status', () => {
    let springError: unknown;
    api.latestReconciliation().subscribe({ error: (error) => (springError = error) });
    http.expectOne('/api/finance/reconciliations').flush(
      { message: 'Service unavailable' },
      { status: 503, statusText: 'Service Unavailable' },
    );
    expect(springError).toBeInstanceOf(HttpErrorResponse);
    expect((springError as HttpErrorResponse).status).toBe(503);
    expect((springError as HttpErrorResponse).error.message).toBe('Service unavailable');

    let fastApiError: unknown;
    api.summary({ from: '2026-10-01', to: '2026-10-22' }).subscribe({
      error: (error) => (fastApiError = error),
    });
    http.expectOne((request) => request.url === '/api/finance/summary').flush(
      { detail: 'Reporting source delayed' },
      { status: 503, statusText: 'Service Unavailable' },
    );
    expect((fastApiError as HttpErrorResponse).error.message).toBe('Reporting source delayed');
  });

  it.each([400, 401, 403, 404, 409, 422, 429, 503])(
    'preserves authorization, validation, conflict, rate-limit, and unavailable status %i',
    (status) => {
      let error: unknown;
      api.latestReconciliation().subscribe({ error: (value) => (error = value) });
      http.expectOne('/api/finance/reconciliations').flush(
        { detail: 'Finance request rejected.' },
        { status, statusText: 'Finance request rejected' },
      );
      expect((error as HttpErrorResponse).status).toBe(status);
      expect((error as HttpErrorResponse).error.message).toBe('Finance request rejected.');
    },
  );

  it('normalizes a network failure as offline rather than an empty report', () => {
    let error: unknown;
    api.latestReconciliation().subscribe({ error: (value) => (error = value) });
    http.expectOne('/api/finance/reconciliations').error(new ProgressEvent('error'));
    expect((error as HttpErrorResponse).status).toBe(0);
    expect((error as HttpErrorResponse).error.message).toContain('could not be reached');
  });

  it('rejects malformed success payloads rather than treating them as zero totals', () => {
    let error: unknown;
    api.summary({ from: '2026-10-01', to: '2026-10-22' }).subscribe({
      error: (value) => (error = value),
    });
    http.expectOne((request) => request.url === '/api/finance/summary').flush({
      status: 'CURRENT',
      totals: {},
    });
    expect(error).toBeInstanceOf(HttpErrorResponse);
    expect((error as HttpErrorResponse).status).toBe(502);
  });
});

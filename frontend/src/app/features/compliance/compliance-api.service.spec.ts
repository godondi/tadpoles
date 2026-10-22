import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { Subject } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { ComplianceApi } from './compliance-api.service';

describe('ComplianceApi', () => {
  let api: ComplianceApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        ComplianceApi,
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { sessionChanged: new Subject<void>() } },
      ],
    });
    api = TestBed.inject(ComplianceApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    try {
      http.verify();
    } finally {
      TestBed.resetTestingModule();
    }
  });

  it('does not issue a protected request without a server-issued context', () => {
    let status = 0;
    api
      .search({ page: 0, pageSize: 25 })
      .subscribe({ error: (error) => (status = error.status) });

    expect(status).toBe(409);
  });

  it('uses the exact investigation path and opaque context header', () => {
    api
      .createContext({ purposeCode: 'ORDER_REVIEW', caseReference: 'CASE-12345678' })
      .subscribe();
    const contextRequest = http.expectOne('/api/compliance/access-contexts');
    contextRequest.flush({
      contextId: 'ctx-server-issued',
      purposeCode: 'ORDER_REVIEW',
      caseReference: 'CASE-12345678',
      scopeLabel: 'Assigned accounts only',
      permissions: ['EVIDENCE_READ'],
      expiresAt: new Date(Date.now() + 60_000).toISOString(),
      policyVersion: 'policy-1',
    });

    api
      .search({
        orderId: 42,
        status: 'REJECTED',
        page: 1,
        pageSize: 25,
      })
      .subscribe();
    const request = http.expectOne(
      (candidate) => candidate.url === '/api/compliance/investigations',
    );
    expect(request.request.headers.get('X-Compliance-Context')).toBe('ctx-server-issued');
    expect(request.request.headers.get('Cache-Control')).toBe('no-store');
    expect(request.request.params.get('orderId')).toBe('42');
    expect(request.request.params.get('status')).toBe('REJECTED');
    expect(request.request.params.get('page')).toBe('1');
    request.flush({ items: [], page: 1, pageSize: 25, total: 0, scope: 'Assigned', asOf: '' });
  });

  it('maps detail section reads to independently paged evidence paths', () => {
    api.context.set({
      contextId: 'ctx-server-issued',
      purposeCode: 'ORDER_REVIEW',
      caseReference: 'CASE-12345678',
      scopeLabel: 'Assigned accounts only',
      permissions: ['EVIDENCE_READ'],
      expiresAt: new Date(Date.now() + 60_000).toISOString(),
      policyVersion: 'policy-1',
    });

    api.section(42, 'quote observations', 2).subscribe();
    const request = http.expectOne(
      (candidate) => candidate.url === '/api/compliance/orders/42/evidence/quote%20observations',
    );
    expect(request.request.params.get('page')).toBe('2');
    expect(request.request.params.get('pageSize')).toBe('25');
    request.flush({
      name: 'quote observations',
      items: [],
      page: 2,
      pageSize: 25,
      total: 0,
      hasMore: false,
      coverage: 'No quote retained',
      source: 'provider observations',
      asOf: new Date().toISOString(),
      completeness: 'NOT_EVALUATED',
    });
  });
});

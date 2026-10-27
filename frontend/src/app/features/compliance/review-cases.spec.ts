import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { BehaviorSubject, Subject, of } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { ReviewCase } from '../../core/api/case-workflow-api.service';
import { ComplianceApi } from './compliance-api.service';
import { ReviewCasesComponent } from './review-cases.component';

const reference = 'REVIEW-11111111-1111-4111-8111-111111111111';
const detail: ReviewCase = {
  reference,
  status: 'IN_REVIEW',
  version: 2,
  reviewer: 23,
  reasonCode: 'POLICY',
  summary: 'Approved source summary only',
  orderId: null,
  createdAt: '2026-10-23T09:00:00Z',
  updatedAt: '2026-10-23T09:00:00Z',
  reviewers: [{ id: 23, username: 'Independently eligible reviewer' }],
  actions: { assign: true, transitions: ['NEEDS_INFORMATION', 'DECIDED'] },
  history: [],
};

describe('private Compliance review cases', () => {
  beforeEach(() => sessionStorage.clear());
  function setup() {
    const route = new BehaviorSubject(convertToParamMap({ reference }));
    const sessionChanged = new Subject<void>();
    const contexts = {
      context: signal(null),
      contextMessage: signal(''),
      clear: vi.fn(),
      policy: () =>
        of({
          configured: false,
          purposes: [],
          policyVersion: 'unconfigured',
          scopeLabel: 'Assigned accounts only',
        }),
    };
    TestBed.configureTestingModule({
      imports: [ReviewCasesComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: { paramMap: route } },
        {
          provide: AuthService,
          useValue: { identity: signal({ userId: 23, roles: ['COMPLIANCE'] }), sessionChanged },
        },
        { provide: ComplianceApi, useValue: contexts },
      ],
    });
    const fixture = TestBed.createComponent(ReviewCasesComponent);
    fixture.detectChanges();
    return {
      fixture,
      http: TestBed.inject(HttpTestingController),
      contexts,
      route,
      sessionChanged,
    };
  }
  function flush(http: HttpTestingController, value = detail) {
    http
      .expectOne((request) => request.url.endsWith('/cases/' + value.reference))
      .flush(structuredClone(value));
  }

  it('retains decisions privately, invalidates the prior purpose and never calls public Support APIs', () => {
    const { fixture: f, http, contexts } = setup();
    flush(http);
    f.detectChanges();
    f.componentInstance.target = 'DECIDED';
    f.componentInstance.reason = 'Private evidence findings reviewed by the assigned reviewer.';
    f.componentInstance.disposition = 'EXPLAINED';
    f.componentInstance.proposal = 'Private proposal for deliberate Operations publication.';
    f.componentInstance.transition();
    const request = http.expectOne((r) => r.url.endsWith('/actions'));
    expect(request.request.body.expectedVersion).toBe(2);
    expect(request.request.body.clientSafeExplanation).toContain('Private proposal');
    request.flush({ status: 'DECIDED', version: 3 });
    expect(contexts.clear).toHaveBeenCalled();
    flush(http, {
      ...detail,
      status: 'DECIDED',
      version: 3,
      findings: 'Private findings',
      clientSafeExplanation: 'Unpublished private proposal',
      actions: { assign: true, transitions: ['CLOSED', 'IN_REVIEW'] },
    });
    f.detectChanges();
    expect(f.nativeElement.textContent).toContain('Proposed explanation - not published');
    expect(f.nativeElement.textContent).toContain('Account-only review');
    expect(f.componentInstance.target).toBe('');
    http.expectNone((r) => r.url.includes('/support/') || r.url.includes('/operations/'));
    http.verify();
  });

  it('retains the original key across unknown outcome and an unconfirmed recovery lookup', () => {
    const { fixture: f, http } = setup();
    flush(http);
    f.componentInstance.target = 'NEEDS_INFORMATION';
    f.componentInstance.reason = 'Please provide the approved retained source facts.';
    f.componentInstance.transition();
    const first = http.expectOne((r) => r.url.endsWith('/actions'));
    const key = first.request.headers.get('Idempotency-Key');
    first.flush({}, { status: 503, statusText: 'Unavailable' });
    f.componentInstance.recover();
    http
      .expectOne((r) => r.url.endsWith('/requests/' + key))
      .flush({}, { status: 404, statusText: 'Unconfirmed' });
    expect(f.componentInstance.pending()).toBe(true);
    f.componentInstance.retry();
    const retry = http.expectOne((r) => r.url.endsWith('/actions'));
    expect(retry.request.headers.get('Idempotency-Key')).toBe(key);
    expect(retry.request.body).toEqual(first.request.body);
    retry.flush({ version: 3 });
    flush(http, { ...detail, version: 3 });
    http.verify();
  });

  it('cancels replaced-route reads and clears protected content after session loss', () => {
    const { fixture: f, http, route, sessionChanged } = setup();
    const old = http.expectOne((r) => r.url.endsWith('/cases/' + reference));
    const next = reference.replace('11111111', '22222222');
    route.next(convertToParamMap({ reference: next }));
    expect(old.cancelled).toBe(true);
    flush(http, { ...detail, reference: next });
    f.detectChanges();
    sessionChanged.next();
    f.detectChanges();
    expect(f.nativeElement.textContent).not.toContain('Approved source summary only');
    http.verify();
  });

  it('does not offer or submit update actions for read-only review grants', () => {
    const { fixture: f, http } = setup();
    flush(http, { ...detail, actions: { assign: false, transitions: [] } });
    f.detectChanges();
    expect(f.nativeElement.textContent).not.toContain('Retain reviewer reassignment');
    f.componentInstance.target = 'DECIDED';
    f.componentInstance.transition();
    f.componentInstance.assign();
    http.expectNone((r) => r.method === 'POST');
    http.verify();
  });
});

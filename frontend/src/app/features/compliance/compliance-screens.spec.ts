import { signal } from '@angular/core';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, Subject } from 'rxjs';
import { ComplianceApi } from './compliance-api.service';
import { ComplianceEvidencePageComponent } from './evidence-page.component';
import { ComplianceInvestigationDetailComponent } from './investigation-detail.component';
import { ComplianceInvestigationsPageComponent } from './investigations-page.component';
import { routes } from '../../app.routes';
import { landingPage, routeRole, safeReturnUrl } from '../../core/auth/workspace-policy';

const context = {
  contextId: 'ctx-server-issued',
  purposeCode: 'ORDER_REVIEW',
  caseReference: 'CASE-12345678',
  scopeLabel: 'Assigned accounts only',
  permissions: ['EVIDENCE_READ'],
  expiresAt: new Date(Date.now() + 60_000).toISOString(),
  policyVersion: 'policy-1',
};

const policy = {
  configured: true,
  purposes: ['ORDER_REVIEW'],
  scopeLabel: 'Assigned accounts only',
  policyVersion: 'policy-1',
};

function apiMock(overrides: Record<string, unknown> = {}) {
  return {
    context: signal(context),
    changed: new Subject<void>(),
    contextMessage: signal(''),
    policy: () => of(policy),
    search: vi.fn(() =>
      of({
        items: [
          {
            orderId: 42,
            clientReference: 'client-ref-42',
            instrument: 'SIM-USD',
            side: 'BUY',
            quantity: '1.2500',
            unit: 'shares',
            status: 'REJECTED',
            submittedAt: '2026-10-21T10:00:00Z',
            acceptedAt: null,
            outcomeAt: '2026-10-21T10:00:03Z',
            executionMode: 'PROVIDER_BACKED',
            recordKind: 'SELF_DIRECTED',
            evidenceCompleteness: 'PARTIAL',
          },
        ],
        page: 0,
        pageSize: 25,
        total: 1,
        scope: 'Assigned accounts only',
        asOf: '2026-10-21T10:01:00Z',
      }),
    ),
    investigation: vi.fn(() =>
      of({
        summary: {
          orderId: 42,
          clientReference: 'client-ref-42',
          instrument: 'SIM-USD',
          side: 'BUY',
          quantity: '1.2500',
          unit: 'shares',
          status: 'REJECTED',
          submittedAt: '2026-10-21T10:00:00Z',
          acceptedAt: null,
          outcomeAt: '2026-10-21T10:00:03Z',
          executionMode: 'PROVIDER_BACKED',
          recordKind: 'SELF_DIRECTED',
          evidenceCompleteness: 'PARTIAL',
        },
        authenticatedActor: 'compliance.actor',
        accountReference: 'acct-scoped-42',
        acceptedPolicy: null,
        currentPolicy: { version: 'current-2' },
        snapshotAt: '2026-10-21T10:01:00Z',
        sections: [
          {
            name: 'Acceptance evidence',
            items: [],
            page: 0,
            pageSize: 25,
            total: 0,
            hasMore: false,
            coverage: 'No retained acceptance record',
            source: 'order events',
            asOf: '2026-10-21T10:01:00Z',
            completeness: 'NOT_EVALUATED',
          },
        ],
        findings: [
          {
            code: 'QUOTE_MISSING',
            explanation: 'Provider quote was not retained.',
            status: 'UNKNOWN',
          },
        ],
        exportAllowed: false,
        limitations: ['Historical record is partial.'],
      }),
    ),
    section: vi.fn(),
    evidence: vi.fn(() =>
      of({
        items: [
          {
            requestReference: 'export-42',
            scope: 'Assigned accounts only',
            actor: 'compliance.actor',
            createdAt: '2026-10-21T11:00:00Z',
            asOf: '2026-10-21T10:01:00Z',
            purposeReference: 'CASE-12345678',
            state: 'READY',
            expiresAt: '2026-10-22T11:00:00Z',
            manifest: {
              schemaVersion: 'compliance-export-v1',
              sourceSnapshot: '2026-10-21T10:01:00Z',
              sectionCounts: { events: 3 },
              contentDigest: 'digest-from-server',
              signatureStatus: 'NOT_SUPPLIED',
              anchorStatus: 'NOT_SUPPLIED',
              verification: 'UNKNOWN',
            },
            authorizedActions: [],
          },
        ],
        page: 0,
        pageSize: 25,
        total: 1,
        scope: 'Assigned accounts only',
        asOf: '2026-10-21T11:00:00Z',
      }),
    ),
    createContext: vi.fn(),
    createExport: vi.fn(),
    ...overrides,
  };
}

describe('Compliance screen states', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('renders scoped search results without deriving a fill from rejection state', () => {
    const api = apiMock();
    TestBed.configureTestingModule({
      imports: [ComplianceInvestigationsPageComponent],
      providers: [provideRouter([]), { provide: ComplianceApi, useValue: api }],
    });
    const fixture = TestBed.createComponent(ComplianceInvestigationsPageComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('REJECTED');
    expect(fixture.nativeElement.textContent).toContain('PARTIAL');
    expect(fixture.nativeElement.textContent).toContain('Not recorded');
    expect(fixture.nativeElement.querySelector('tbody')?.textContent).not.toContain('FILLED');
  });

  it('rejects invalid positive-ID filters before making a search call', () => {
    const api = apiMock();
    TestBed.configureTestingModule({
      imports: [ComplianceInvestigationsPageComponent],
      providers: [provideRouter([]), { provide: ComplianceApi, useValue: api }],
    });
    const fixture = TestBed.createComponent(ComplianceInvestigationsPageComponent);
    fixture.detectChanges();
    api.search.mockClear();
    fixture.componentInstance.filters.orderId = '0';
    fixture.componentInstance.search();

    expect(api.search).not.toHaveBeenCalled();
    expect(fixture.componentInstance.validation()).toContain('positive integer');
  });

  it('renders partial detail evidence and keeps export disabled without entitlement', () => {
    const api = apiMock();
    TestBed.configureTestingModule({
      imports: [ComplianceInvestigationDetailComponent],
      providers: [
        provideRouter([]),
        { provide: ComplianceApi, useValue: api },
        {
          provide: ActivatedRoute,
          useValue: {
            paramMap: of(convertToParamMap({ id: '42' })),
            snapshot: { queryParamMap: convertToParamMap({ status: 'REJECTED' }) },
          },
        },
      ],
    });
    const fixture = TestBed.createComponent(ComplianceInvestigationDetailComponent);
    fixture.detectChanges();

    expect(api.investigation).toHaveBeenCalledWith(42);
    expect(fixture.nativeElement.textContent).toContain('NOT_EVALUATED');
    expect(fixture.nativeElement.textContent).toContain('QUOTE_MISSING');
    expect(fixture.nativeElement.textContent).toContain('Export is disabled');
    expect(fixture.nativeElement.textContent).not.toContain('tamper-proof');
  });

  it('shows server manifest metadata without creating or downloading an export', () => {
    const api = apiMock();
    TestBed.configureTestingModule({
      imports: [ComplianceEvidencePageComponent],
      providers: [provideRouter([]), { provide: ComplianceApi, useValue: api }],
    });
    const fixture = TestBed.createComponent(ComplianceEvidencePageComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('digest-from-server');
    expect(fixture.nativeElement.textContent).toContain('NOT_SUPPLIED');
    expect(api.createExport).not.toHaveBeenCalled();
  });
});

describe('Compliance routes', () => {
  it('uses dedicated lazy components for all three existing URLs', async () => {
    const shell = routes.find((route) => route.path === '');
    const children = shell?.children?.flatMap((route) => route.children ?? []) ?? [];
    const compliance = children.filter((route) => route.path?.startsWith('compliance/'));

    expect(compliance.map((route) => route.path)).toEqual([
      'compliance/cases/:reference',
      'compliance/cases',
      'compliance/investigations',
      'compliance/investigations/:id',
      'compliance/evidence',
    ]);
    expect(await compliance.find(r=>r.path==='compliance/investigations')!.loadComponent!()).toBe(ComplianceInvestigationsPageComponent);
    expect(await compliance.find(r=>r.path==='compliance/investigations/:id')!.loadComponent!()).toBe(ComplianceInvestigationDetailComponent);
    expect(await compliance.find(r=>r.path==='compliance/evidence')!.loadComponent!()).toBe(ComplianceEvidencePageComponent);
  });

  it.each(['CLIENT', 'ANALYST', 'FINANCE'])(
    'keeps Compliance URLs outside the %s workspace',
    (role) => {
      expect(routeRole('/compliance/investigations')).toBe('COMPLIANCE');
      expect(safeReturnUrl('/compliance/investigations', [role])).toBe(landingPage([role]));
    },
  );
});

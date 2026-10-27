import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject, Observable, Subject, of, throwError } from 'rxjs';
import { ComplianceApi } from './compliance-api.service';
import { ComplianceInvestigationDetailComponent } from './investigation-detail.component';
import { ComplianceInvestigationsPageComponent } from './investigations-page.component';
import { ComplianceEvidencePageComponent } from './evidence-page.component';
import {
  ComplianceEvidenceSection,
  ComplianceExportRecord,
  ComplianceInvestigationDetail,
} from './compliance.models';
import { ComplianceSearchState, restoreSearch, safeSearchQuery } from './search-state';
import { readExportRecovery, saveExportRecovery } from './export-recovery';

const context = {
  contextId: 'ctx-a',
  purposeCode: 'ORDER_REVIEW',
  caseReference: 'PRIVATE-CASE',
  scopeLabel: 'Assigned',
  permissions: ['EVIDENCE_READ', 'EXPORT_CREATE', 'EXPORT_DOWNLOAD'],
  expiresAt: new Date(Date.now() + 3600000).toISOString(),
  policyVersion: 'v1',
};
function section(name = 'events'): ComplianceEvidenceSection {
  return {
    name,
    page: 0,
    pageSize: 25,
    items: [],
    total: 50,
    hasMore: true,
    coverage: 'Retained',
    source: name,
    asOf: '',
    completeness: 'NOT_EVALUATED',
  };
}
function detail(orderId = 42): ComplianceInvestigationDetail {
  return {
    summary: {
      orderId,
      clientReference: 'PRIVATE-ACCOUNT',
      instrument: 'TEST',
      side: 'BUY',
      quantity: '1',
      unit: 'shares',
      status: 'EXECUTING',
      submittedAt: null,
      acceptedAt: null,
      outcomeAt: null,
      executionMode: 'PROVIDER_BACKED',
      recordKind: 'SELF_DIRECTED',
      evidenceCompleteness: 'UNKNOWN',
    },
    authenticatedActor: 'actor',
    accountReference: 'PRIVATE-ACCOUNT',
    acceptedPolicy: null,
    currentPolicy: null,
    snapshotAt: '',
    sections: [section(), section('attempts')],
    findings: [],
    exportAllowed: true,
    limitations: [],
  };
}
const record: ComplianceExportRecord = {
  requestReference: 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
  scope: 'Assigned',
  actor: 'actor',
  createdAt: '',
  asOf: '',
  purposeReference: 'PRIVATE-CASE',
  state: 'READY',
  expiresAt: null,
  manifest: null,
  authorizedActions: ['DOWNLOAD', 'RECOVER_RECEIPT'],
};
function mockApi() {
  return {
    context: signal<typeof context | null>(context),
    changed: new Subject<void>(),
    contextMessage: signal(''),
    actorKey: vi.fn(() => '7'),
    policy: () =>
      of({
        configured: true,
        purposes: ['ORDER_REVIEW'],
        scopeLabel: 'Assigned',
        policyVersion: 'v1',
      }),
    search: vi.fn(() =>
      of({ items: [], page: 0, pageSize: 25, total: 100, scope: 'Assigned', asOf: '' }),
    ),
    investigation: vi.fn((id: number) => of(detail(id))),
    section: vi.fn(
      (id: number, name: string, page: number): Observable<ComplianceEvidenceSection> =>
        new Subject<ComplianceEvidenceSection>(),
    ),
    createExport: vi.fn(
      (id: number, key: string): Observable<ComplianceExportRecord> =>
        new Subject<ComplianceExportRecord>(),
    ),
    receipt: vi.fn((reference: string): Observable<ComplianceExportRecord> => of(record)),
    downloadExport: vi.fn((reference: string): Observable<Blob> => of(new Blob(['server-only']))),
    evidence: vi.fn(() =>
      of({ items: [record], page: 0, pageSize: 25, total: 1, scope: 'Assigned', asOf: '' }),
    ),
  };
}
function setup<T>(
  component:
    | typeof ComplianceInvestigationDetailComponent
    | typeof ComplianceInvestigationsPageComponent
    | typeof ComplianceEvidencePageComponent,
  api = mockApi(),
  query: Record<string, string> = {},
) {
  const params = new BehaviorSubject(convertToParamMap({ id: '42' }));
  TestBed.configureTestingModule({
    imports: [component],
    providers: [
      provideRouter([]),
      { provide: ComplianceApi, useValue: api },
      {
        provide: ActivatedRoute,
        useValue: { paramMap: params, snapshot: { queryParamMap: convertToParamMap(query) } },
      },
    ],
  });
  const fixture = TestBed.createComponent(
    component as typeof ComplianceInvestigationDetailComponent,
  );
  fixture.detectChanges();
  return { fixture, api, params };
}

describe('Compliance correctness', () => {
  beforeEach(() => sessionStorage.clear());
  afterEach(() => {
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
    sessionStorage.clear();
  });

  it('applies validated UTC values and keeps draft edits out of pagination and URLs', () => {
    const { fixture, api } = setup(ComplianceInvestigationsPageComponent);
    const component = fixture.componentInstance as unknown as ComplianceInvestigationsPageComponent;
    Object.assign(component.filters, {
      from: '2026-03-08T02:30',
      to: '2026-03-09T02:30',
      orderId: '42',
      clientReference: 'PRIVATE-ACCOUNT',
      status: 'EXECUTING',
    });
    component.search();
    component.filters.status = 'REJECTED';
    component.filters.from = 'invalid-draft';
    component.loadPage(2);
    expect(api.search).toHaveBeenLastCalledWith(
      expect.objectContaining({
        page: 2,
        status: 'EXECUTING',
        orderId: 42,
        clientReference: 'PRIVATE-ACCOUNT',
        from: '2026-03-08T02:30:00.000Z',
        to: '2026-03-09T02:30:00.000Z',
      }),
    );
    expect(component.filterQuery()).toEqual({
      from: '2026-03-08T02:30:00.000Z',
      to: '2026-03-09T02:30:00.000Z',
      status: 'EXECUTING',
      page: '2',
      pageSize: '25',
    });
  });

  it('restores safe refresh filters and page, rejecting malformed saved state', () => {
    const { api } = setup(ComplianceInvestigationsPageComponent, mockApi(), {
      page: '3',
      status: 'EXECUTING',
      orderId: '99',
      clientReference: 'PRIVATE',
      caseReference: 'PRIVATE',
      from: '2026-03-08T02:30:00.000Z',
      to: '2026-03-09T02:30:00.000Z',
    });
    expect(api.search).toHaveBeenLastCalledWith({
      page: 3,
      pageSize: 25,
      status: 'EXECUTING',
      from: '2026-03-08T02:30:00.000Z',
      to: '2026-03-09T02:30:00.000Z',
    });
    expect(() => restoreSearch(convertToParamMap({ page: '-1' }))).toThrow();
    expect(() => restoreSearch(convertToParamMap({ from: '2026-02-29T12:00' }))).toThrow();
  });

  it('restores private applied filters from memory on back navigation and drops them on context change', () => {
    const { fixture, api } = setup(ComplianceInvestigationsPageComponent);
    const first = fixture.componentInstance as unknown as ComplianceInvestigationsPageComponent;
    Object.assign(first.filters, {
      orderId: '42',
      clientReference: 'PRIVATE-ACCOUNT',
      status: 'EXECUTING',
    });
    first.search();
    first.loadPage(2);
    expect(TestBed.inject(ComplianceSearchState).applied?.clientReference).toBe('PRIVATE-ACCOUNT');
    fixture.destroy();
    const second = TestBed.createComponent(ComplianceInvestigationsPageComponent);
    expect(second.componentInstance.filters.clientReference).toBe('PRIVATE-ACCOUNT');
    expect(second.componentInstance.page).toBe(2);
    api.context.set({ ...context, contextId: 'ctx-b' });
    api.changed.next();
    expect(second.componentInstance.filters.clientReference).toBe('');
    expect(api.search).toHaveBeenLastCalledWith({ page: 0, pageSize: 25 });
  });

  it('only serializes approved URL fields even when a caller supplies private values', () => {
    expect(
      safeSearchQuery({
        orderId: 42,
        clientReference: 'PRIVATE',
        status: 'EXECUTING',
        page: 1,
        pageSize: 25,
      }),
    ).toEqual({ status: 'EXECUTING', page: '1', pageSize: '25' });
  });

  it('keeps sections independent and cancels stale successes and errors on order replacement', () => {
    const { fixture, api, params } = setup(ComplianceInvestigationDetailComponent);
    const component = fixture.componentInstance;
    const events = new Subject<ComplianceEvidenceSection>();
    const attempts = new Subject<ComplianceEvidenceSection>();
    api.section.mockReturnValueOnce(events).mockReturnValueOnce(attempts);
    component.page(section(), 1);
    component.page(section('attempts'), 1);
    expect(component.sectionLoading('events')).toBe(true);
    expect(component.sectionLoading('attempts')).toBe(true);
    events.error(new Error('events failed'));
    expect(component.sectionError('events')).toContain('Unable');
    expect(component.sectionError('attempts')).toBe('');
    expect(component.sectionLoading('attempts')).toBe(true);
    params.next(convertToParamMap({ id: '43' }));
    attempts.next({ ...section('attempts'), page: 1 });
    expect(component.resource.data()?.summary.orderId).toBe(43);
    expect(component.resource.data()?.sections[1].page).toBe(0);
    expect(component.sectionStates()).toEqual({});
  });

  it('cancels section and export subscriptions on context removal, including old errors', () => {
    const { fixture, api } = setup(ComplianceInvestigationDetailComponent);
    const component = fixture.componentInstance;
    const pending = new Subject<ComplianceExportRecord>();
    api.createExport.mockReturnValue(pending);
    component.page(section(), 1);
    component.requestExport(42);
    api.context.set(null);
    api.changed.next();
    pending.error({ status: 403 });
    expect(component.resource.data()).toBeNull();
    expect(component.receipt()).toBeNull();
    expect(component.exporting()).toBe(false);
    expect(component.sectionStates()).toEqual({});
    expect(component.exportMessage()).toBe('');
  });

  it('makes one POST on busy re-entry and replays the original actor/order key after component recreation', () => {
    const { fixture, api } = setup(ComplianceInvestigationDetailComponent);
    const component = fixture.componentInstance;
    component.requestExport(42);
    component.requestExport(42);
    expect(api.createExport).toHaveBeenCalledTimes(1);
    const key = api.createExport.mock.calls[0][1];
    expect(readExportRecovery('7', 42)?.key).toBe(key);
    expect(readExportRecovery('8', 42)).toBeNull();
    expect(readExportRecovery('7', 43)).toBeNull();
    expect(JSON.stringify(sessionStorage)).not.toContain('PRIVATE');
    fixture.destroy();
    const reloaded = TestBed.createComponent(ComplianceInvestigationDetailComponent);
    reloaded.componentInstance.requestExport(42);
    expect(api.createExport).toHaveBeenLastCalledWith(42, key);
  });

  it('does not publish old export receipts or start downloads after route replacement', () => {
    const { fixture, api, params } = setup(ComplianceInvestigationDetailComponent);
    const pending = new Subject<ComplianceExportRecord>();
    api.createExport.mockReturnValue(pending);
    fixture.componentInstance.requestExport(42);
    params.next(convertToParamMap({ id: '43' }));
    pending.next(record);
    expect(fixture.componentInstance.receipt()).toBeNull();
    expect(fixture.componentInstance.exportMessage()).toBe('');
    expect(api.downloadExport).not.toHaveBeenCalled();
  });

  it('recovers an explicit server receipt and downloads exact server bytes with object URL cleanup', () => {
    saveExportRecovery({
      actor: '7',
      order: 42,
      action: 'EXPORT_CREATE',
      key: 'original-key',
      reference: record.requestReference,
    });
    const { fixture, api } = setup(ComplianceInvestigationDetailComponent);
    const archive = new Blob(['exact-server-bytes']);
    api.downloadExport.mockReturnValue(of(archive));
    const create = vi.fn(() => 'blob:server-archive');
    const revoke = vi.fn();
    vi.stubGlobal(
      'URL',
      class extends URL {
        static override createObjectURL = create;
        static override revokeObjectURL = revoke;
      },
    );
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    fixture.componentInstance.recoverReceipt(record.requestReference);
    fixture.componentInstance.download(record);
    expect(api.receipt).toHaveBeenCalledWith(record.requestReference);
    expect(api.downloadExport).toHaveBeenCalledWith(record.requestReference);
    expect(create).toHaveBeenCalledWith(archive);
    expect(click).toHaveBeenCalledTimes(1);
    expect(revoke).toHaveBeenCalledWith('blob:server-archive');
    expect(document.querySelector('a[download]')).toBeNull();
  });

  it.each(['IDEMPOTENCY_CONFLICT', 'EVIDENCE_LIMIT', 'ARCHIVE_LIMIT'])(
    'retains original recovery metadata for typed %s',
    (code) => {
      const { fixture, api } = setup(ComplianceInvestigationDetailComponent);
      api.createExport.mockReturnValue(throwError(() => ({ status: 409, error: { code } })));
      fixture.componentInstance.requestExport(42);
      const key = readExportRecovery('7', 42)?.key;
      fixture.componentInstance.requestExport(42);
      expect(api.createExport).toHaveBeenLastCalledWith(42, key);
      expect(api.context()?.contextId).toBe('ctx-a');
      expect(fixture.componentInstance.exportMessage()).not.toContain('unknown');
    },
  );

  it('cancels history downloads when the page or context changes', () => {
    const { fixture, api } = setup(ComplianceEvidencePageComponent);
    const component = fixture.componentInstance as unknown as ComplianceEvidencePageComponent;
    const pending = new Subject<Blob>();
    api.downloadExport.mockReturnValue(pending);
    component.download(record);
    expect(pending.observed).toBe(true);
    component.load(1);
    expect(pending.observed).toBe(false);
    pending.next(new Blob(['old']));
    expect(component.busy()).toEqual({});
  });

  it('recovery persistence whitelists opaque metadata and rejects tampered or cross-actor records', () => {
    saveExportRecovery({
      actor: '7',
      order: 42,
      action: 'EXPORT_CREATE',
      key: 'original-key',
      purpose: 'PRIVATE',
    } as Parameters<typeof saveExportRecovery>[0]);
    expect(JSON.stringify(sessionStorage)).not.toContain('PRIVATE');
    expect(readExportRecovery('7', 42)?.key).toBe('original-key');
    sessionStorage.setItem(
      'tadpoles.compliance.export.v1.7.42',
      JSON.stringify({
        actor: '8',
        order: 42,
        action: 'EXPORT_CREATE',
        key: 'other-key',
      }),
    );
    expect(() => readExportRecovery('7', 42)).toThrow('No replacement request key');
  });

  it('blocks new POSTs rather than silently replacing corrupt unknown-outcome metadata', () => {
    sessionStorage.setItem('tadpoles.compliance.export.v1.7.42', '{broken');
    const { fixture, api } = setup(ComplianceInvestigationDetailComponent);
    fixture.componentInstance.requestExport(42);
    expect(api.createExport).not.toHaveBeenCalled();
    expect(fixture.componentInstance.recoveryBlocked()).toBe(true);
    expect(fixture.componentInstance.exportMessage()).toContain('No replacement request key');
  });
});

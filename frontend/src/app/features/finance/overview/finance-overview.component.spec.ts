import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { Subject } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { FinanceOverviewComponent } from './finance-overview.component';

const summary = {
  schemaVersion: 'finance-summary-v1',
  status: 'CURRENT',
  currency: 'USD',
  period: { from: '2026-10-01', to: '2026-10-22', timeZone: 'UTC' },
  dataAsOf: '2026-10-22T18:00:00Z',
  generatedAt: '2026-10-22T18:01:00Z',
  definitionVersion: 'draft-v1',
  source: 'approved reporting source',
  limitations: ['Internal booking only'],
  totals: {
    settledDeposits: '100.00',
    settledWithdrawals: '0.00',
    grossBuyConsideration: '25.00',
    grossSellConsideration: '0.00',
    bookedFillCount: 1,
    fees: { amount: '0.00', definition: 'Project fee' },
  },
};

describe('Finance overview', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  let sessionChanged = new Subject<void>();

  beforeEach(async () => {
    sessionChanged = new Subject<void>();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'finance/overview', component: FinanceOverviewComponent }]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { sessionChanged } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => http.verify());

  it('keeps reconciliation usable when summary reporting returns 503', async () => {
    await harness.navigateByUrl(
      '/finance/overview?from=2026-10-01&to=2026-10-22',
      FinanceOverviewComponent,
    );
    const totals = http.expectOne((request) => request.url.endsWith('/api/finance/summary'));
    expect(totals.request.params.get('from')).toBe('2026-10-01');
    totals.flush(
      { message: 'Period totals are not available yet.' },
      { status: 503, statusText: 'Service Unavailable' },
    );
    http.expectOne((request) => request.url.endsWith('/api/finance/reconciliations')).flush({
      runId: null,
      message: 'No stored run yet.',
    });

    const page = harness.routeNativeElement!;
    harness.detectChanges();
    expect(page.textContent).toContain('Reporting source unavailable');
    expect(page.textContent).toContain('No stored run yet.');
    expect(page.textContent).not.toContain('0.00 USD');
    expect(page.textContent).toContain('Export unavailable');
  });

  it('renders only authoritative populated values and source timestamps', async () => {
    await harness.navigateByUrl(
      '/finance/overview?from=2026-10-01&to=2026-10-22',
      FinanceOverviewComponent,
    );
    http.expectOne((request) => request.url.endsWith('/api/finance/summary')).flush(summary);
    http.expectOne((request) => request.url.endsWith('/api/finance/reconciliations')).flush({
      evidenceVersion: 2,
      runId: 'run-22',
      checkedAt: '2026-10-22T18:00:00Z',
      matched: true,
      exceptions: [],
      historicalExceptions: 2,
      scope: 'Internal journals and processing evidence',
    });
    harness.detectChanges();
    const text = harness.routeNativeElement!.textContent ?? '';
    expect(text).toContain('100.00 USD');
    expect(text).toContain('25.00 USD');
    expect(text).toContain('0.00 USD');
    expect(text).toContain('approved reporting source');
    expect(text).toContain('Data as of');
    expect(text).toContain('Historical migration exceptions');
    expect(text).toContain('Internal booking only');
  });

  it('distinguishes a successful empty report from an unavailable report', async () => {
    await harness.navigateByUrl(
      '/finance/overview?from=2026-09-22&to=2026-10-22',
      FinanceOverviewComponent,
    );
    http.expectOne((request) => request.url.endsWith('/api/finance/summary')).flush({
      ...summary,
      period: { from: '2026-09-22', to: '2026-10-22', timeZone: 'UTC' },
      totals: {
        settledDeposits: '0.00',
        settledWithdrawals: '0.00',
        grossBuyConsideration: '0.00',
        grossSellConsideration: '0.00',
        bookedFillCount: 0,
      },
    });
    http.expectOne((request) => request.url.endsWith('/api/finance/reconciliations')).flush({
      runId: null,
      message: 'No stored run yet.',
    });
    harness.detectChanges();
    expect(harness.routeNativeElement!.textContent).toContain('No activity was returned');
    expect(harness.routeNativeElement!.textContent).toContain('0.00 USD');
    expect(harness.routeNativeElement!.textContent).toContain('Not reported');
    expect(harness.routeNativeElement!.textContent).toContain('Selected UTC dates are sent');
  });

  it('shows delayed source status alongside authoritative as-of metadata', async () => {
    await harness.navigateByUrl(
      '/finance/overview?from=2026-10-01&to=2026-10-22',
      FinanceOverviewComponent,
    );
    http.expectOne((request) => request.url.endsWith('/api/finance/summary')).flush({
      ...summary,
      status: 'DELAYED',
      dataAsOf: '2026-10-20T18:00:00Z',
    });
    http.expectOne((request) => request.url.endsWith('/api/finance/reconciliations')).flush({
      runId: null,
      message: 'No stored run yet.',
    });
    harness.detectChanges();
    expect(harness.routeNativeElement!.textContent).toContain('DELAYED');
    expect(harness.routeNativeElement!.textContent).toContain('Oct 20, 2026');
  });

  it('clears the independent reconciliation report when summary permission is denied', async () => {
    await harness.navigateByUrl('/finance/overview', FinanceOverviewComponent);
    const pendingReport = http.expectOne((request) =>
      request.url.endsWith('/api/finance/reconciliations'),
    );
    http.expectOne((request) => request.url.endsWith('/api/finance/summary')).flush(
      { message: 'Finance access denied.' },
      { status: 403, statusText: 'Forbidden' },
    );
    expect(pendingReport.cancelled).toBe(true);
    harness.detectChanges();
    expect(harness.routeNativeElement!.textContent).toContain('Access denied');
    http.verify({ ignoreCancelled: true });
  });

  it('updates query parameters for a valid date scope and rejects reversed dates', async () => {
    const component = await harness.navigateByUrl(
      '/finance/overview?from=2026-10-01&to=2026-10-22',
      FinanceOverviewComponent,
    );
    http.expectOne((request) => request.url.endsWith('/api/finance/summary')).flush(summary);
    http.expectOne((request) => request.url.endsWith('/api/finance/reconciliations')).flush({
      runId: null,
      message: 'No stored run yet.',
    });
    component.draftFrom = '2026-10-20';
    component.draftTo = '2026-10-10';
    component.applyScope(new Event('submit'));
    expect(component.scopeError()).toContain('not after');
    component.draftTo = '2026-10-22';
    component.applyScope(new Event('submit'));
    await harness.fixture.whenStable();
    expect(TestBed.inject(Router).url).toContain('from=2026-10-20');
    expect(TestBed.inject(Router).url).toContain('to=2026-10-22');
    const next = http.expectOne((request) => request.url.endsWith('/api/finance/summary'));
    expect(next.request.params.get('from')).toBe('2026-10-20');
    next.flush({
      ...summary,
      period: { from: '2026-10-20', to: '2026-10-22', timeZone: 'UTC' },
    });
  });

  it('cancels pending Finance reads and clears prior identity data on session change', async () => {
    const page = await harness.navigateByUrl('/finance/overview', FinanceOverviewComponent);
    const oldSummary = http.expectOne((request) => request.url.endsWith('/api/finance/summary'));
    const oldReport = http.expectOne((request) => request.url.endsWith('/api/finance/reconciliations'));
    sessionChanged.next();
    expect(oldSummary.cancelled).toBe(true);
    expect(oldReport.cancelled).toBe(true);
    expect(page.summary.data()).toBeNull();
    expect(page.reconciliation.data()).toBeNull();
  });
});

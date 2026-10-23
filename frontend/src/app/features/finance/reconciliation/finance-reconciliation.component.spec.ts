import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { Subject } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { FinanceReconciliationComponent } from './finance-reconciliation.component';

describe('Finance reconciliation', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  let sessionChanged = new Subject<void>();

  beforeEach(async () => {
    sessionChanged = new Subject<void>();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'finance/reconciliation', component: FinanceReconciliationComponent }]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { sessionChanged } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => http.verify());

  it('distinguishes no stored report from a matched run and historical exceptions', async () => {
    await harness.navigateByUrl('/finance/reconciliation', FinanceReconciliationComponent);
    http.expectOne((request) => request.method === 'GET').flush({
      runId: null,
      message: 'Run a reconciliation to inspect a consistent snapshot',
    });
    harness.detectChanges();
    expect(harness.routeNativeElement!.textContent).toContain('No reconciliation run is stored');

    const page = harness.routeDebugElement!.componentInstance as FinanceReconciliationComponent;
    page.load();
    http.expectOne((request) => request.method === 'GET').flush({
      evidenceVersion: 2,
      runId: 'persisted-1',
      checkedAt: '2026-10-22T18:00:00Z',
      matched: true,
      exceptions: [],
      historicalExceptions: 4,
      scope: 'Internal cash/position journals, holds and posting links',
    });
    harness.detectChanges();
    const text = harness.routeNativeElement!.textContent ?? '';
    expect(text).toContain('No differences found in this run');
    expect(text).toContain('persisted-1');
    expect(text).toContain('Historical migration exceptions');
    expect(text).toContain('4');
    expect(text).toContain('does not establish external bank settlement');
  });

  it('posts once on rapid repeat activation and renders current findings separately', async () => {
    const page = await harness.navigateByUrl('/finance/reconciliation', FinanceReconciliationComponent);
    http.expectOne((request) => request.method === 'GET').flush({
      runId: 'previous',
      checkedAt: '2026-10-21T18:00:00Z',
      matched: true,
      exceptions: [],
      historicalExceptions: 3,
      scope: 'Internal records',
    });
    const button = harness.routeNativeElement!.querySelector<HTMLButtonElement>('.primary-button')!;
    button.click();
    page.run();
    const posts = http.match((request) => request.method === 'POST');
    expect(posts).toHaveLength(1);
    expect(posts[0].request.url).toContain('/api/finance/reconciliations');
    expect(posts[0].request.body).toEqual({});
    posts[0].flush({
      evidenceVersion: 2,
      runId: 'new-run',
      checkedAt: '2026-10-22T18:30:00Z',
      matched: false,
      exceptions: [
        { kind: 'CASH_JOURNAL', reference: 'cash:7:USD' },
        { kind: 'FILL_POSTING', reference: 'fill:abc-123' },
      ],
      historicalExceptions: 3,
      scope: 'Current internal posting and event evidence',
    });
    harness.detectChanges();
    expect(harness.routeNativeElement!.textContent).toContain('Differences require review');
    expect(harness.routeNativeElement!.textContent).toContain('cash:7:USD');
    expect(harness.routeNativeElement!.textContent).toContain('Historical migration exceptions');
    expect(http.match(() => true)).toHaveLength(0);
  });

  it('filters only loaded current exceptions by kind and reference', async () => {
    await harness.navigateByUrl('/finance/reconciliation', FinanceReconciliationComponent);
    http.expectOne((request) => request.method === 'GET').flush({
      runId: 'run-filter',
      checkedAt: '2026-10-22T18:00:00Z',
      matched: false,
      exceptions: [
        { kind: 'CASH_JOURNAL', reference: 'cash:7:USD' },
        { kind: 'EVENT_OUTBOX', reference: 'event:xyz' },
      ],
      historicalExceptions: 9,
      scope: 'Current internal checks',
    });
    harness.detectChanges();
    const search = harness.routeNativeElement!.querySelector<HTMLInputElement>('#exception-search')!;
    search.value = 'event:';
    search.dispatchEvent(new Event('input'));
    harness.detectChanges();
    const text = harness.routeNativeElement!.textContent ?? '';
    expect(text).toContain('event:xyz');
    expect(text).not.toContain('cash:7:USD');
    expect(text).toContain('Search filters only these loaded rows');
    expect(text).toContain('9');
  });

  it('preserves the report after an unavailable POST and does not retry automatically', async () => {
    const page = await harness.navigateByUrl('/finance/reconciliation', FinanceReconciliationComponent);
    http.expectOne((request) => request.method === 'GET').flush({
      runId: 'last-known',
      checkedAt: '2026-10-22T18:00:00Z',
      matched: true,
      exceptions: [],
      historicalExceptions: 0,
      scope: 'Internal checks',
    });
    page.run();
    http.expectOne((request) => request.method === 'POST').flush(
      { message: 'The response was interrupted.' },
      { status: 503, statusText: 'Service Unavailable' },
    );
    harness.detectChanges();
    expect(harness.routeNativeElement!.textContent).toContain('last-known');
    expect(harness.routeNativeElement!.textContent).toContain('No automatic retry was made');
    expect(http.match((request) => request.method === 'POST')).toHaveLength(0);
  });

  it('cancels an in-flight run and clears protected state when the identity changes', async () => {
    const page = await harness.navigateByUrl('/finance/reconciliation', FinanceReconciliationComponent);
    http.expectOne((request) => request.method === 'GET').flush({
      runId: 'old-identity-report',
      checkedAt: '2026-10-22T18:00:00Z',
      matched: true,
      exceptions: [],
      historicalExceptions: 0,
      scope: 'Internal checks',
    });
    page.run();
    const pendingPost = http.expectOne((request) => request.method === 'POST');
    sessionChanged.next();
    expect(pendingPost.cancelled).toBe(true);
    expect(page.runBusy()).toBe(false);
    expect(page.report.data()).toBeNull();
  });
});

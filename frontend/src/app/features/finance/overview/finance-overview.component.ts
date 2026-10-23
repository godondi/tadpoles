import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { catchError, Observable, throwError } from 'rxjs';
import { FinanceApi, FinanceSummary, ReconciliationReport, ReportingScope } from '../api/finance-api.service';
import { PageResource } from '../../../shared/workspace/page-resource';
import { RequestStateComponent } from '../../../shared/workspace/request-state.component';

@Component({
  selector: 'app-finance-overview',
  imports: [DatePipe, RouterLink, RequestStateComponent],
  template: `
    <header class="workspace-heading">
      <p class="eyebrow">FINANCE · READ-ONLY</p>
      <h1 tabindex="-1">Finance overview</h1>
      <p>Review internal USD activity and durable reconciliation evidence. Reporting is shown only when an approved source returns a valid response.</p>
    </header>

    <form class="panel scope-form" aria-label="Reporting period" (submit)="applyScope($event)">
      <div class="section-heading">
        <div><p class="eyebrow">Reporting scope</p><h2>Choose a UTC date range</h2></div>
        <button type="button" class="secondary-button" (click)="resetScope()">Reset</button>
      </div>
      <div class="scope-fields">
        <label for="finance-from">From (UTC date)</label>
        <input id="finance-from" type="date" required [value]="draftFrom" (input)="draftFrom = inputValue($event)" />
        <label for="finance-to">To (UTC date)</label>
        <input id="finance-to" type="date" required [value]="draftTo" (input)="draftTo = inputValue($event)" />
        <span class="currency-label">Currency</span><strong>USD</strong>
      </div>
      <div class="scope-actions">
        <span>Selected UTC dates are sent to the reporting service. The response period below is authoritative.</span>
        <button type="submit" class="primary-button">Apply period</button>
      </div>
      @if (scopeError()) { <p class="notice" role="alert">{{ scopeError() }}</p> }
    </form>

    <section class="panel summary-panel" aria-labelledby="activity-heading">
      <div class="section-heading"><div><p class="eyebrow">Historical reporting</p><h2 id="activity-heading">Activity summary</h2></div><span class="state-label">{{ summaryState() }}</span></div>
      @if (summary.loading()) {
        <p role="status" aria-live="polite">Loading summary for {{ appliedScope().from }} through {{ appliedScope().to }} UTC…</p>
      } @else if (summary.error()) {
        <div class="notice" role="alert">
          <h3>{{ errorHeading(summary.status()) }}</h3>
          <p>{{ summary.error() }}</p>
          <p>No totals are displayed because an unavailable or invalid response is not a zero-activity report.</p>
          <button type="button" class="secondary-button" (click)="reloadSummary()">Try again</button>
        </div>
      } @else if (summary.data(); as report) {
        <div class="metadata-grid">
          <p><strong>Report status</strong><span>{{ report.status }}</span></p>
          <p><strong>Reporting source</strong><span>{{ report.source }}</span></p>
          <p><strong>Definition</strong><span>{{ report.definitionVersion }}</span></p>
          <p><strong>Data as of</strong><span>{{ report.dataAsOf | date: 'medium' : 'UTC' }} UTC</span></p>
          <p><strong>Generated at</strong><span>{{ report.generatedAt ? (report.generatedAt | date: 'medium' : 'UTC') + ' UTC' : 'Not supplied' }}</span></p>
          <p><strong>Response period</strong><span>{{ report.period.from }} – {{ report.period.to }} ({{ report.period.timeZone }})</span></p>
        </div>
        @if (!hasActivity(report)) { <p class="notice" role="status">No activity was returned for this scope by the reporting source.</p> }
        <div class="metrics-grid" aria-label="Reported activity totals">
          <article class="metric-card"><h3>Settled internal deposits</h3><p>{{ report.totals.settledDeposits }} {{ report.currency }}</p></article>
          <article class="metric-card"><h3>Settled internal withdrawals</h3><p>{{ report.totals.settledWithdrawals }} {{ report.currency }}</p></article>
          <article class="metric-card"><h3>Gross booked buy consideration</h3><p>{{ report.totals.grossBuyConsideration }} {{ report.currency }}</p></article>
          <article class="metric-card"><h3>Gross booked sell consideration</h3><p>{{ report.totals.grossSellConsideration }} {{ report.currency }}</p></article>
          <article class="metric-card"><h3>Booked fills</h3><p>{{ report.totals.bookedFillCount }}</p></article>
          @if (report.totals.fees; as fees) {
            <article class="metric-card"><h3>Fees · {{ fees.definition }}</h3><p>{{ fees.amount }} {{ report.currency }}</p></article>
          } @else {
            <article class="metric-card"><h3>Fees</h3><p>Not reported</p></article>
          }
        </div>
        @if (report.limitations.length) { <aside class="notice"><h3>Report limitations</h3><ul>@for (limitation of report.limitations; track limitation) { <li>{{ limitation }}</li> }</ul></aside> }
      }
    </section>

    <section class="panel reconciliation-panel" aria-labelledby="latest-heading">
      <div class="section-heading"><div><p class="eyebrow">Independent internal consistency check</p><h2 id="latest-heading">Latest reconciliation</h2></div><a routerLink="/finance/reconciliation">Open reconciliation</a></div>
      <app-request-state [loading]="reconciliation.loading()" [error]="reconciliation.error()" [status]="reconciliation.status()" (retry)="loadReconciliation()" />
      @if (reconciliation.data(); as report) {
        @if (report.runId) {
          <p class="state-label">{{ report.matched ? 'No differences found in this run’s stated scope' : 'Differences require review' }}</p>
          <dl class="report-facts"><div><dt>Run ID</dt><dd>{{ report.runId }}</dd></div><div><dt>Checked at</dt><dd>{{ report.checkedAt | date: 'medium' : 'UTC' }} UTC</dd></div><div><dt>Current exceptions in this run</dt><dd>{{ report.exceptions?.length ?? 0 }}</dd></div><div><dt>Historical migration exceptions</dt><dd>{{ report.historicalExceptions ?? 'Not supplied' }}</dd></div></dl>
          <p>{{ report.scope }}</p>
        } @else { <p role="status">{{ report.message }}</p> }
      }
    </section>

    <section class="panel records-panel" aria-labelledby="records-heading">
      <p class="eyebrow">Read-only records</p><h2 id="records-heading">Fill, movement and posting details</h2>
      <p>Record-level Finance detail endpoints are not available. No Operations-only links or inferred accounting records are shown here.</p>
      <h3>Accounting export</h3>
      <p>Export is disabled until an authorized server-side contract defines approved scope, purpose, manifest, redaction, download authorization and retention.</p>
      <button type="button" disabled aria-describedby="export-unavailable">Export unavailable</button>
      <span id="export-unavailable" class="sr-only">No approved Finance export endpoint is connected.</span>
    </section>

    <aside class="notice limitation-note">
      <h2>Accounting boundary</h2>
      <p>Internal cash and position journals are not a complete general ledger. A booked fill is internal booking, not evidence of an external exchange execution, bank transfer or custody settlement. Reconciliation records evidence and does not repair balances; historical migration exceptions remain separate.</p>
    </aside>
  `,
  styles: [`
    :host { display: block; min-width: 0; }
    .scope-form, .summary-panel, .reconciliation-panel, .records-panel { margin-bottom: 20px; }
    .section-heading { display:flex; justify-content:space-between; align-items:flex-start; gap:16px; margin-bottom:16px; }
    .section-heading h2, .records-panel h2, .limitation-note h2 { margin:5px 0 0; }
    .scope-fields { display:grid; grid-template-columns: minmax(110px, 1fr) minmax(140px, 2fr) minmax(110px, 1fr) minmax(140px, 2fr) auto auto; align-items:center; gap:10px; }
    label, .currency-label { color:var(--text-secondary); font-weight:700; }
    input { min-height:42px; border:1px solid var(--border-gray); padding:8px; color:var(--text-primary); background:var(--surface); }
    .scope-actions { display:flex; justify-content:space-between; align-items:center; gap:16px; margin-top:16px; color:var(--text-secondary); font-size:.9rem; }
    .primary-button, .secondary-button { padding:10px 14px; border:1px solid var(--dark-green); background:var(--dark-green); color:white; font-weight:700; cursor:pointer; }
    .secondary-button { background:var(--surface); color:var(--dark-green); }
    .metadata-grid, .report-facts { display:grid; grid-template-columns:repeat(auto-fit,minmax(190px,1fr)); gap:12px; }
    .metadata-grid p, .report-facts div { display:grid; gap:5px; margin:0; padding:12px; background:var(--surface-muted); overflow-wrap:anywhere; }
    .metadata-grid strong, .report-facts dt { color:var(--text-secondary); font-size:.85rem; }
    .report-facts dd { margin:0; overflow-wrap:anywhere; }
    .metrics-grid { display:grid; grid-template-columns:repeat(auto-fit,minmax(190px,1fr)); gap:12px; margin:18px 0; }
    .metric-card { border:1px solid var(--border-gray); padding:16px; min-width:0; }
    .metric-card h3 { margin:0; font-size:.95rem; }
    .metric-card p { margin:12px 0 0; font-family:var(--font-family-mono); overflow-wrap:anywhere; }
    .state-label { color:var(--text-primary); font-weight:700; }
    .notice { margin:14px 0; }
    button:disabled { cursor:not-allowed; opacity:.65; }
    @media (max-width:760px) { .scope-fields { grid-template-columns:1fr 1fr; } .scope-fields label, .scope-fields .currency-label { grid-column:1; } .scope-fields input, .scope-fields strong { grid-column:2; } .scope-actions { align-items:flex-start; flex-direction:column; } }
  `],
})
export class FinanceOverviewComponent {
  private readonly api = inject(FinanceApi);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly summary = new PageResource<FinanceSummary>();
  readonly reconciliation = new PageResource<ReconciliationReport>();
  readonly appliedScope = signal<ReportingScope>(defaultScope());
  readonly scopeError = signal('');
  draftFrom = defaultScope().from;
  draftTo = defaultScope().to;

  constructor() {
    this.route.queryParamMap.pipe(takeUntilDestroyed()).subscribe((params) => {
      const fallback = defaultScope();
      const from = params.get('from') ?? fallback.from;
      const to = params.get('to') ?? fallback.to;
      const scope = isDate(from) && isDate(to) && from <= to ? { from, to } : fallback;
      this.draftFrom = scope.from;
      this.draftTo = scope.to;
      this.scopeError.set('');
      this.appliedScope.set(scope);
      this.summary.load(this.clearPeerOnDenial(this.api.summary(scope), this.reconciliation));
    });
    this.loadReconciliation();
  }

  inputValue(event: Event): string {
    return (event.target as HTMLInputElement).value;
  }

  applyScope(event: Event): void {
    event.preventDefault();
    if (!isDate(this.draftFrom) || !isDate(this.draftTo) || this.draftFrom > this.draftTo) {
      this.scopeError.set('Enter valid UTC calendar dates and ensure the start date is not after the end date.');
      return;
    }
    this.scopeError.set('');
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { from: this.draftFrom, to: this.draftTo },
      queryParamsHandling: 'merge',
    });
  }

  resetScope(): void {
    const scope = defaultScope();
    this.draftFrom = scope.from;
    this.draftTo = scope.to;
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { from: null, to: null },
      queryParamsHandling: 'merge',
    });
  }

  reloadSummary(): void {
    this.summary.load(
      this.clearPeerOnDenial(this.api.summary(this.appliedScope()), this.reconciliation),
    );
  }

  loadReconciliation(): void {
    this.reconciliation.load(
      this.clearPeerOnDenial(this.api.latestReconciliation(), this.summary),
    );
  }

  summaryState(): string {
    if (this.summary.loading()) return 'Loading';
    if (this.summary.error()) return this.summary.status() === 503 ? 'Unavailable' : 'Not available';
    return this.summary.data()?.status ?? 'Not available';
  }

  errorHeading(status: number): string {
    if (status === 401) return 'Sign in required';
    if (status === 403) return 'Access denied';
    if (status === 404) return 'Report unavailable';
    if (status === 409) return 'Scope conflict';
    if (status === 422 || status === 400) return 'Invalid reporting scope';
    if (status === 429) return 'Please wait before retrying';
    if (status === 503) return 'Reporting source unavailable';
    if (status === 0) return 'Connection unavailable';
    return 'Summary unavailable';
  }

  hasActivity(report: FinanceSummary): boolean {
    const totals = report.totals;
    return [
      totals.settledDeposits,
      totals.settledWithdrawals,
      totals.grossBuyConsideration,
      totals.grossSellConsideration,
    ].some(isNonZeroDecimal) || totals.bookedFillCount > 0;
  }

  private clearPeerOnDenial<T>(source: Observable<T>, peer: PageResource<unknown>): Observable<T> {
    return source.pipe(
      catchError((error: HttpErrorResponse) => {
        if (error.status === 401 || error.status === 403) {
          peer.load(throwError(() => error));
        }
        return throwError(() => error);
      }),
    );
  }
}

function defaultScope(): ReportingScope {
  const to = new Date().toISOString().slice(0, 10);
  const start = new Date(`${to}T00:00:00.000Z`);
  start.setUTCDate(start.getUTCDate() - 30);
  return { from: start.toISOString().slice(0, 10), to };
}

function isDate(value: string): boolean {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const parsed = new Date(`${value}T00:00:00.000Z`);
  return !Number.isNaN(parsed.valueOf()) && parsed.toISOString().slice(0, 10) === value;
}

function isNonZeroDecimal(value: string): boolean {
  return /[1-9]/.test(value.replace(/[.\-]/g, ''));
}

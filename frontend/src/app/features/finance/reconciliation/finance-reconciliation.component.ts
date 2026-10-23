import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { of, throwError } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AuthService } from '../../../core/auth/auth.service';
import { FinanceApi, ReconciliationException, ReconciliationReport } from '../api/finance-api.service';
import { PageResource } from '../../../shared/workspace/page-resource';
import { RequestStateComponent } from '../../../shared/workspace/request-state.component';

@Component({
  selector: 'app-finance-reconciliation',
  imports: [DatePipe, RouterLink, RequestStateComponent],
  template: `
    <header class="workspace-heading">
      <p class="eyebrow">FINANCE · INTERNAL EVIDENCE</p>
      <h1 tabindex="-1">Reconciliation</h1>
      <p>Review the latest persisted internal consistency report or explicitly record a new snapshot. This action does not repair balances.</p>
    </header>

    <section class="panel controls-panel" aria-label="Reconciliation controls">
      <button type="button" class="primary-button" [disabled]="runBusy()" (click)="run()">
        {{ runBusy() ? 'Running reconciliation…' : 'Run reconciliation' }}
      </button>
      <button type="button" class="secondary-button" [disabled]="report.loading()" (click)="load()">Read latest stored run</button>
      @if (runError()) { <div class="notice" role="alert"><h2>{{ runErrorTitle() }}</h2><p>{{ runError() }}</p>@if (uncertainRun()) { <p>The POST outcome is not confirmed. No automatic retry was made. Read the latest stored run before choosing another run.</p> }</div> }
      @if (runSuccess()) { <p class="notice" role="status" aria-live="polite">{{ runSuccess() }}</p> }
    </section>

    <app-request-state [loading]="report.loading()" [error]="report.error()" [status]="report.status()" (retry)="load()" />

    @if (report.data(); as snapshot) {
      @if (snapshot.runId) {
        <section class="panel report-panel" aria-labelledby="report-title">
          <p class="eyebrow">Persisted report · {{ snapshot.evidenceVersion ? 'evidence v' + snapshot.evidenceVersion : 'version not supplied' }}</p>
          <h2 id="report-title">{{ snapshot.matched ? 'No differences found in this run’s stated scope' : 'Differences require review' }}</h2>
          <dl class="report-facts">
            <div><dt>Run ID</dt><dd>{{ snapshot.runId }}</dd></div>
            <div><dt>Checked at</dt><dd>{{ snapshot.checkedAt | date: 'medium' : 'UTC' }} UTC</dd></div>
            <div><dt>Current exceptions in this run</dt><dd>{{ exceptions(snapshot).length }}</dd></div>
            <div><dt>Historical migration exceptions</dt><dd>{{ snapshot.historicalExceptions ?? 'Not supplied' }}</dd></div>
          </dl>
          <p class="scope-copy"><strong>Check scope:</strong> {{ snapshot.scope }}</p>
          <p class="notice">A matched result applies only to the checks and internal records named above. It does not establish external bank settlement, exchange execution, custody or a complete general ledger. Historical exceptions remain a separate obligation.</p>
        </section>

        <section class="panel exceptions-panel" aria-labelledby="exceptions-title">
          <div class="section-heading"><div><p class="eyebrow">Current findings only</p><h2 id="exceptions-title">Exceptions in this run</h2></div><span class="result-count">{{ filteredExceptions().length }} shown · {{ exceptions(snapshot).length }} total in run</span></div>
          <label for="exception-search">Filter this run by kind or reference</label>
          <input id="exception-search" type="search" [value]="search()" (input)="setSearch($event)" autocomplete="off" />
          @if (!exceptions(snapshot).length) {
            <p role="status">This run returned no current exceptions. Historical migration exceptions are counted separately above.</p>
          } @else if (!filteredExceptions().length) {
            <p role="status">No current exceptions in this run match the filter.</p>
          } @else {
            <div class="table-wrapper"><table>
              <caption>Current reconciliation exceptions returned for run {{ snapshot.runId }}. Search filters only these loaded rows.</caption>
              <thead><tr><th scope="col">Kind</th><th scope="col">Reference</th></tr></thead>
              <tbody>@for (item of filteredExceptions(); track rowKey(item)) { <tr><th scope="row">{{ item.kind }}</th><td class="mono-value">{{ item.reference }}</td></tr> }</tbody>
            </table></div>
          }
        </section>
      } @else {
        <section class="panel no-run-panel" role="status"><h2>No reconciliation run is stored</h2><p>{{ snapshot.message }}</p><p>Running a reconciliation records a report; it does not make financial corrections.</p></section>
      }
    }

    <section class="panel scope-panel" aria-labelledby="scope-title">
      <p class="eyebrow">Accounting boundary</p><h2 id="scope-title">Internal records, not external settlement</h2>
      <p>Current checks cover the exact scope in the persisted report, including internal cash and position journal totals, holds, order processing, fill/posting links, movement postings and event/outbox links. This is not a full trial balance, bank statement reconciliation or custody confirmation.</p>
      <p>Finance has no balance repair, hold release, posted-record edit or evidence deletion action in this workspace.</p>
      <a routerLink="/finance/overview">Return to Finance overview</a>
    </section>
  `,
  styles: [`
    :host { display:block; min-width:0; }
    .controls-panel, .report-panel, .exceptions-panel, .scope-panel, .no-run-panel { margin-bottom:20px; }
    .controls-panel { display:flex; align-items:center; flex-wrap:wrap; gap:12px; }
    .controls-panel .notice { flex-basis:100%; margin-bottom:0; }
    .primary-button, .secondary-button { padding:10px 14px; border:1px solid var(--dark-green); background:var(--dark-green); color:white; font-weight:700; cursor:pointer; }
    .secondary-button { background:var(--surface); color:var(--dark-green); }
    button:disabled { cursor:not-allowed; opacity:.65; }
    .report-panel h2, .scope-panel h2, .no-run-panel h2 { margin:6px 0 16px; }
    .report-facts { display:grid; grid-template-columns:repeat(auto-fit,minmax(190px,1fr)); gap:12px; }
    .report-facts div { padding:12px; background:var(--surface-muted); min-width:0; }
    .report-facts dt { color:var(--text-secondary); font-size:.85rem; }
    .report-facts dd { margin:6px 0 0; overflow-wrap:anywhere; }
    .scope-copy { margin-top:18px; }
    .notice { margin-top:16px; }
    .section-heading { display:flex; justify-content:space-between; align-items:flex-end; gap:14px; }
    .section-heading h2 { margin:5px 0 14px; }
    .result-count { color:var(--text-secondary); font-weight:700; }
    label { display:block; margin:8px 0 6px; font-weight:700; }
    input[type='search'] { width:min(100%, 460px); min-height:42px; margin-bottom:16px; padding:8px; border:1px solid var(--border-gray); background:var(--surface); color:var(--text-primary); }
    .table-wrapper { overflow-x:auto; }
    table { width:100%; border-collapse:collapse; text-align:left; }
    caption { text-align:left; color:var(--text-secondary); padding:8px 0; }
    th, td { border-bottom:1px solid var(--border-gray); padding:10px; vertical-align:top; }
    th { text-align:left; }
    td { overflow-wrap:anywhere; }
    @media (max-width:620px) { .section-heading { align-items:flex-start; flex-direction:column; gap:0; } }
  `],
})
export class FinanceReconciliationComponent {
  private readonly api = inject(FinanceApi);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);
  readonly report = new PageResource<ReconciliationReport>();
  readonly runBusy = signal(false);
  readonly runError = signal('');
  readonly runStatus = signal(0);
  readonly runSuccess = signal('');
  readonly uncertainRun = signal(false);
  readonly search = signal('');

  constructor() {
    this.auth.sessionChanged.pipe(takeUntilDestroyed()).subscribe(() => {
      this.runBusy.set(false);
      this.runError.set('');
      this.runSuccess.set('');
      this.uncertainRun.set(false);
      this.search.set('');
    });
    this.load();
  }

  load(): void {
    this.runSuccess.set('');
    this.report.load(this.api.latestReconciliation());
  }

  run(): void {
    if (this.runBusy()) return;
    this.runBusy.set(true);
    this.runError.set('');
    this.runStatus.set(0);
    this.runSuccess.set('');
    this.uncertainRun.set(false);
    this.api
      .runReconciliation()
      .pipe(takeUntil(this.auth.sessionChanged), takeUntilDestroyed(this.destroyRef))
      .subscribe({
      next: (result) => {
        this.runBusy.set(false);
        this.runSuccess.set('A new reconciliation report was stored.');
        this.report.load(of(result));
      },
      error: (error: HttpErrorResponse) => {
        this.runBusy.set(false);
        this.runStatus.set(error.status);
        this.runError.set(error.error?.message ?? 'The reconciliation run could not be confirmed.');
        this.uncertainRun.set(error.status === 0 || error.status === 408 || error.status >= 500);
        if (error.status === 401 || error.status === 403) this.report.load(throwError(() => error));
      },
      });
  }

  exceptions(snapshot: ReconciliationReport): ReconciliationException[] {
    return snapshot.exceptions ?? [];
  }

  filteredExceptions(): ReconciliationException[] {
    const snapshot = this.report.data();
    const query = this.search().trim().toLocaleLowerCase();
    return this.exceptions(snapshot ?? { runId: null }).filter((item) =>
      `${item.kind} ${item.reference}`.toLocaleLowerCase().includes(query),
    );
  }

  setSearch(event: Event): void {
    this.search.set((event.target as HTMLInputElement).value);
  }

  rowKey(item: ReconciliationException): string {
    return `${item.kind}:${item.reference}`;
  }

  runErrorTitle(): string {
    if (this.runStatus() === 401) return 'Sign in required';
    if (this.runStatus() === 403) return 'Access denied';
    if (this.runStatus() === 404) return 'Report unavailable';
    if (this.runStatus() === 409) return 'Run conflict';
    if (this.runStatus() === 422 || this.runStatus() === 400) return 'Invalid request';
    if (this.runStatus() === 429) return 'Too many requests';
    if (this.runStatus() === 503) return 'Reconciliation unavailable';
    if (this.runStatus() === 0) return 'Connection unavailable';
    return 'Run failed';
  }
}

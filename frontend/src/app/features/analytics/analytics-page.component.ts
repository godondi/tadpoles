import { AfterViewInit, Component, ElementRef, OnDestroy, PLATFORM_ID, ViewChild, inject, signal } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink, RouterLinkActive } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { combineLatest, forkJoin } from 'rxjs';
import { Chart, CategoryScale, LinearScale, PointElement, LineElement, LineController, DoughnutController, Tooltip, Legend, ArcElement, Filler } from 'chart.js';
import {
  AnalyticsApiService,
  AnalyticsFilters,
  AnalyticsView,
  InstrumentsResponse,
  MetricValue,
  ReportMetadata,
  SummaryResponse,
  TrendsResponse,
  BenchmarkKey,
  HoldingAllocation,
  PerformancePoint,
  PortfolioHoldingsResponse,
  PortfolioPerformanceResponse,
} from '../../core/api/analytics-api.service';
import { Observable } from 'rxjs';

Chart.register(CategoryScale, LinearScale, PointElement, LineElement, LineController, DoughnutController, Tooltip, Legend, ArcElement, Filler);

@Component({
  selector: 'app-analytics-page',
  imports: [FormsModule, RouterLink, RouterLinkActive],
  template: `
    <header class="analytics-header">
      <div>
        <p class="eyebrow">Business analytics / read-only</p>
        <h1 tabindex="-1">{{ title }}</h1>
        <p class="lede">{{ description }}</p>
      </div>
      <div class="status-chip" [class.status-chip--bad]="status() !== 'CURRENT'">
        <span class="status-dot"></span>{{ statusLabel() }}
      </div>
    </header>

    <nav class="analytics-tabs" aria-label="Analytics views">
      <a routerLink="/analytics/overview" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: true }">Overview</a>
      <a routerLink="/analytics/trends" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: true }">Trends</a>
      <a routerLink="/analytics/instruments" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: true }">Instruments</a>
    </nav>

    <form class="filter-panel" (ngSubmit)="applyFilters()">
      <div class="filter-heading">
        <div>
          <p class="eyebrow">Report scope</p>
          <h2>Choose the period and lens</h2>
        </div>
        <button class="text-button" type="button" (click)="resetFilters()">Reset filters</button>
      </div>
      <div class="filter-grid">
        <label>From <input type="date" [(ngModel)]="draftFilters.from" name="from" required /></label>
        <label>To <input type="date" [(ngModel)]="draftFilters.to" name="to" required /></label>
        <label>Granularity
          <select [(ngModel)]="draftFilters.granularity" name="granularity">
            <option value="day">Daily</option><option value="week">Weekly</option><option value="month">Monthly</option>
          </select>
        </label>
        <label>Market <input [(ngModel)]="draftFilters.market" name="market" placeholder="All markets" /></label>
        <label>Asset class <input [(ngModel)]="draftFilters.asset_class" name="asset_class" placeholder="All asset classes" /></label>
        <label>Instrument <input [(ngModel)]="draftFilters.instrument" name="instrument" placeholder="Symbol" /></label>
      </div>
      <div class="filter-footer">
        <span>Timezone: {{ draftFilters.timezone }}</span>
        <button class="primary-button" type="submit">Apply filters</button>
      </div>
    </form>

    @if (loading()) {
      <section class="state-panel" aria-live="polite"><span class="loader"></span><p>Loading reporting data...</p></section>
    } @else if (error()) {
      <section class="state-panel state-panel--warning" role="alert">
        <p class="eyebrow">{{ statusLabel() }}</p>
        <h2>Historical reporting is not connected</h2>
        <p>{{ error() }}</p>
        <p class="state-detail">Your filters are preserved. No figures are shown until an approved reporting source and metric definitions are available.</p>
        <button class="secondary-button" type="button" (click)="load()">Try again</button>
      </section>
    } @else {
      <section class="metadata-strip">
        <span><strong>Data as of</strong> {{ metadata()?.data_as_of ?? 'Not available' }}</span>
        <span><strong>Definition</strong> {{ metadata()?.definition_version ?? 'Pending approval' }}</span>
        <span><strong>Timezone</strong> {{ metadata()?.reporting_timezone ?? draftFilters.timezone }}</span>
      </section>
      <section class="metric-grid" aria-label="Summary metrics">
        @for (metric of metrics(); track metric.key) {
          <article class="metric-tile"><p>{{ metric.label }}</p><strong>{{ metric.value ?? '—' }}</strong><span>{{ metric.unit }}{{ metric.currency ? ' · ' + metric.currency : '' }}</span></article>
        } @empty {
          <article class="metric-tile metric-tile--empty"><p>No activity</p><strong>—</strong><span>There is no approved activity for this scope.</span></article>
        }
      </section>
    }

    @if (view === 'overview') {
      <section class="visualization-grid" aria-label="Portfolio visualizations">
        <article class="content-panel visualization-panel">
          <div class="panel-heading"><div><p class="eyebrow">Portfolio performance</p><h2>Portfolio vs market benchmarks</h2></div><div class="period-controls" role="group" aria-label="Performance period"><button type="button" [class.active]="performancePeriod() === '1m'" (click)="selectPerformancePeriod('1m')">1M</button><button type="button" [class.active]="performancePeriod() === '3m'" (click)="selectPerformancePeriod('3m')">3M</button><button type="button" [class.active]="performancePeriod() === '12m'" (click)="selectPerformancePeriod('12m')">12M</button></div></div>
          <p class="panel-note">Indexed to 100 at the start of the selected period. Values require approved portfolio and benchmark history.</p>
          @if (performance().length) { <div class="canvas-frame"><canvas #performanceCanvas aria-label="Line chart comparing portfolio performance with benchmark indexes"></canvas></div><div class="table-wrapper"><table><caption>Exact normalized performance values</caption><thead><tr><th scope="col">Date</th><th scope="col">Portfolio</th><th scope="col">S&amp;P 500</th><th scope="col">Dow Jones</th><th scope="col">Nasdaq-100</th><th scope="col">Russell 2000</th></tr></thead><tbody>@for (point of performance(); track point.date) {<tr><th scope="row">{{ point.date }}</th><td>{{ value(point.values['portfolio']) }}</td><td>{{ value(point.values['sp500']) }}</td><td>{{ value(point.values['dow_jones']) }}</td><td>{{ value(point.values['nasdaq_100']) }}</td><td>{{ value(point.values['russell_2000']) }}</td></tr>}</tbody></table></div> } @else { <div class="visualization-empty">Performance data is unavailable until the approved portfolio and benchmark history is connected.</div> }
        </article>
        <article class="content-panel visualization-panel">
          <div class="panel-heading"><div><p class="eyebrow">Portfolio allocation</p><h2>Largest holdings</h2></div><span class="panel-note">Top 5 + Other</span></div>
          @if (holdings().length) { <div class="canvas-frame canvas-frame--donut"><canvas #holdingsCanvas aria-label="Doughnut chart showing the top five portfolio holdings and other holdings"></canvas></div><div class="table-wrapper"><table><caption>Exact portfolio allocation</caption><thead><tr><th scope="col">Holding</th><th scope="col">Value</th><th scope="col">Share</th></tr></thead><tbody>@for (holding of holdings(); track holding.symbol) {<tr><th scope="row">{{ holding.display_name }}</th><td>{{ holding.market_value }} {{ holding.currency }}</td><td>{{ holding.percentage }}%</td></tr>}</tbody></table></div> } @else { <div class="visualization-empty">Holdings data is unavailable until an approved reporting snapshot is connected.</div> }
        </article>
      </section>
    }

    <section class="lower-grid">
      <article class="content-panel">
        <div class="panel-heading"><div><p class="eyebrow">{{ view === 'instruments' ? 'Ranking' : 'Time series' }}</p><h2>{{ view === 'instruments' ? 'Instrument activity' : 'Activity over time' }}</h2></div><span class="panel-note">Exact values stay visible</span></div>
        @if (error()) { <p class="muted">Connect reporting data to populate this view.</p> }
        @else if (view === 'instruments') {
          <div class="empty-table"><strong>Instrument ranking is waiting for source data.</strong><span>Measure, unit, and currency will be shown together.</span></div>
        } @else {
          <div class="chart-placeholder"><div class="chart-line"></div><span>Trend points will appear here with the matching exact-value table.</span></div>
        }
      </article>
      <aside class="content-panel definitions"><p class="eyebrow">Reading this report</p><h2>Freshness & definitions</h2><dl><div><dt>Status</dt><dd>{{ statusLabel() }}</dd></div><div><dt>Event date</dt><dd>Business event time</dd></div><div><dt>Quantities</dt><dd>Kept separate by unit</dd></div><div><dt>Notional</dt><dd>Kept separate by currency</dd></div></dl></aside>
    </section>
  `,
  styles: [
    `:host { display: block; color: #173042; } .analytics-header { display:flex; justify-content:space-between; gap:24px; align-items:flex-start; margin-bottom:24px; } h1 { margin:6px 0 10px; font-size:clamp(1.8rem,3vw,2.6rem); letter-spacing:-.02em; } .lede { max-width:62ch; color:#5b6774; line-height:1.55; } .status-chip { white-space:nowrap; display:flex; align-items:center; gap:8px; padding:9px 12px; border:1px solid #b9d9c4; background:#eff9f1; color:#21633a; font-size:.82rem; font-weight:700; } .status-chip--bad { border-color:#e5c38c; background:#fff8e9; color:#855b18; } .status-dot { width:8px; height:8px; background:currentColor; border-radius:0; } .eyebrow { margin:0; color:#006044; font-size:.72rem; font-weight:800; letter-spacing:.1em; text-transform:uppercase; } .analytics-tabs { display:flex; gap:4px; border-bottom:1px solid #dbe2e8; margin-bottom:20px; } .analytics-tabs a { padding:11px 15px; color:#5b6774; text-decoration:none; border-bottom:3px solid transparent; font-weight:700; } .analytics-tabs a.active { color:#006044; border-bottom-color:#006044; } .filter-panel, .content-panel, .state-panel { background:#fff; border:1px solid #dbe2e8; padding:22px; } .filter-panel { margin-bottom:20px; } .filter-heading, .filter-footer, .panel-heading { display:flex; justify-content:space-between; align-items:center; gap:16px; } h2 { margin:5px 0 0; font-size:1.18rem; } .filter-grid { display:grid; grid-template-columns:repeat(6,minmax(0,1fr)); gap:14px; margin:20px 0; } label { display:grid; gap:6px; color:#5b6774; font-size:.8rem; font-weight:700; } input, select { width:100%; min-height:40px; border:1px solid #bcc9d1; background:#fff; color:#173042; padding:9px 10px; font:inherit; font-weight:400; } .filter-footer { border-top:1px solid #edf0f2; padding-top:16px; color:#5b6774; font-size:.82rem; } button { font:inherit; cursor:pointer; } .primary-button, .secondary-button { border:0; padding:10px 15px; font-weight:800; } .primary-button { color:#fff; background:#006044; } .secondary-button { color:#006044; background:#e9f1ed; } .text-button { border:0; background:transparent; color:#006044; text-decoration:underline; text-underline-offset:3px; font-weight:700; } .metadata-strip { display:flex; flex-wrap:wrap; gap:10px 28px; padding:14px 0; color:#5b6774; font-size:.82rem; } .metadata-strip strong { color:#173042; margin-right:5px; } .metric-grid { display:grid; grid-template-columns:repeat(4,minmax(0,1fr)); gap:14px; margin-bottom:20px; } .metric-tile { min-height:122px; border-top:3px solid #af8a49; background:#fff; border-left:1px solid #dbe2e8; border-right:1px solid #dbe2e8; border-bottom:1px solid #dbe2e8; padding:17px; } .metric-tile p { margin:0 0 16px; color:#5b6774; font-size:.8rem; font-weight:700; } .metric-tile strong { display:block; font-size:1.7rem; } .metric-tile span { display:block; margin-top:8px; color:#5b6774; font-size:.78rem; } .metric-tile--empty { border-top-color:#c8d0d5; } .lower-grid { display:grid; grid-template-columns:minmax(0,2fr) minmax(260px,1fr); gap:20px; } .panel-note, .muted { color:#5b6774; font-size:.82rem; } .chart-placeholder, .empty-table { min-height:230px; display:grid; place-items:center; align-content:center; gap:10px; margin-top:20px; border:1px dashed #c5d0d6; background:#fbfcfc; text-align:center; color:#5b6774; padding:20px; } .chart-line { width:65%; height:70px; border-top:3px solid #006044; transform:skewY(-7deg) translateY(22px); opacity:.25; } .definitions dl { margin:22px 0 0; } .definitions dl div { display:flex; justify-content:space-between; gap:15px; padding:12px 0; border-top:1px solid #edf0f2; } dt { color:#5b6774; } dd { margin:0; text-align:right; font-weight:700; } .state-panel { display:flex; align-items:flex-start; gap:14px; margin-bottom:20px; } .state-panel h2 { margin-top:0; } .state-panel p { margin:0; line-height:1.5; } .state-panel--warning { display:block; border-left:4px solid #af8a49; } .state-panel--warning .eyebrow, .state-panel--warning h2, .state-panel--warning p { margin-bottom:10px; } .state-detail { color:#5b6774; } .loader { width:18px; height:18px; border:3px solid #dbe2e8; border-top-color:#006044; border-radius:0; animation:spin 1s linear infinite; } @keyframes spin { to { transform:rotate(360deg); } } @media (max-width:900px) { .filter-grid { grid-template-columns:repeat(3,minmax(0,1fr)); } .metric-grid { grid-template-columns:repeat(2,minmax(0,1fr)); } } @media (max-width:640px) { .analytics-header, .filter-heading, .filter-footer, .panel-heading { display:block; } .status-chip { display:inline-flex; margin-top:14px; } .analytics-tabs { overflow-x:auto; } .filter-grid { grid-template-columns:1fr; } .filter-footer > * { display:block; margin-top:10px; } .primary-button { width:100%; } .lower-grid { grid-template-columns:1fr; } }`,
    `:host { --analytics-green:#006044; } .visualization-grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:20px; margin-bottom:20px; } .visualization-panel { min-width:0; } .period-controls { display:flex; gap:4px; } .period-controls button { border:1px solid #bcc9d1; background:#fff; color:#5b6774; padding:7px 10px; font-weight:800; } .period-controls button.active { background:var(--analytics-green); border-color:var(--analytics-green); color:#fff; } .canvas-frame { height:260px; margin:20px 0; position:relative; } .canvas-frame--donut { height:220px; } .table-wrapper { overflow-x:auto; } table { width:100%; border-collapse:collapse; font-size:.8rem; } caption { text-align:left; color:#6c7881; margin-bottom:8px; } th, td { border-bottom:1px solid #edf0f2; padding:8px; text-align:right; white-space:nowrap; } th:first-child, td:first-child { text-align:left; } .visualization-empty { display:grid; place-content:center; min-height:150px; border:1px dashed #c8d3d8; padding:24px; color:#6c7881; text-align:center; } @media (max-width:900px) { .visualization-grid { grid-template-columns:1fr; } } @media (max-width:600px) { .panel-heading { align-items:flex-start; flex-direction:column; } .period-controls { width:100%; } .period-controls button { flex:1; } }`,
  ],
})
export class AnalyticsPageComponent implements AfterViewInit, OnDestroy {
  private readonly api = inject(AnalyticsApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly loading = signal(false);
  readonly error = signal('');
  readonly metrics = signal<MetricValue[]>([]);
  readonly metadata = signal<ReportMetadata | null>(null);
  readonly performance = signal<PerformancePoint[]>([]);
  readonly holdings = signal<HoldingAllocation[]>([]);
  readonly performancePeriod = signal<'1m' | '3m' | '12m'>('1m');
  @ViewChild('performanceCanvas') private performanceCanvas?: ElementRef<HTMLCanvasElement>;
  @ViewChild('holdingsCanvas') private holdingsCanvas?: ElementRef<HTMLCanvasElement>;
  private readonly platformId = inject(PLATFORM_ID);
  private performanceChart?: Chart;
  private holdingsChart?: Chart;
  view: AnalyticsView = 'overview';
  title = 'Analytics overview';
  description = 'A read-only view of aggregate trading activity with explicit freshness and metric definitions.';
  draftFilters: AnalyticsFilters = this.defaultFilters();

  ngAfterViewInit() {
    this.renderCharts();
  }

  ngOnDestroy() {
    this.performanceChart?.destroy();
    this.holdingsChart?.destroy();
  }

  constructor() {
    combineLatest([this.route.data, this.route.queryParamMap]).pipe(takeUntilDestroyed()).subscribe(([data, params]) => {
      this.view = data['analyticsView'] as AnalyticsView;
      this.title = data['title'];
      this.description = data['description'];
      const defaults = this.defaultFilters();
      this.draftFilters = {
        ...defaults,
        from: params.get('from') ?? defaults.from,
        to: params.get('to') ?? defaults.to,
        timezone: params.get('timezone') ?? defaults.timezone,
        granularity: (params.get('granularity') as AnalyticsFilters['granularity']) ?? defaults.granularity,
        market: params.get('market') ?? undefined,
        asset_class: params.get('asset_class') ?? undefined,
        instrument: params.get('instrument') ?? undefined,
        segment: params.get('segment') ?? undefined,
      };
      this.load();
    });
  }

  applyFilters() {
    void this.router.navigate([], { relativeTo: this.route, queryParams: this.draftFilters, queryParamsHandling: 'merge' });
  }

  resetFilters() {
    this.draftFilters = this.defaultFilters();
    this.applyFilters();
  }

  selectPerformancePeriod(period: '1m' | '3m' | '12m') {
    this.performancePeriod.set(period);
    if (this.view === 'overview') this.load();
  }

  value(value: number | string | undefined): string {
    return value === undefined ? '—' : String(value);
  }

  load() {
    this.loading.set(true);
    this.error.set('');
    if (this.view === 'overview') {
      forkJoin({
        summary: this.api.summary(this.draftFilters),
        performance: this.api.portfolioPerformance(this.draftFilters, this.performancePeriod()),
        holdings: this.api.portfolioHoldings(this.draftFilters),
      }).subscribe({
        next: ({ summary, performance, holdings }) => {
          this.metadata.set(summary.metadata);
          this.metrics.set(summary.metrics);
          this.performance.set(performance.points);
          this.holdings.set(this.topHoldings(holdings.holdings));
          this.loading.set(false);
          this.renderCharts();
        },
        error: (response: { error?: { detail?: string }; message?: string }) => {
          this.error.set(response.error?.detail ?? response.message ?? 'The reporting service could not be reached.');
          this.performance.set([]);
          this.holdings.set([]);
          this.loading.set(false);
        },
      });
      return;
    }
    const request: Observable<SummaryResponse | TrendsResponse | InstrumentsResponse> = this.view === 'trends' ? this.api.trends(this.draftFilters) : this.api.instruments(this.draftFilters);
    request.subscribe({
      next: (response) => {
        this.metadata.set(response.metadata);
        this.metrics.set('metrics' in response ? response.metrics : []);
        this.loading.set(false);
      },
      error: (response: { status?: number; error?: { detail?: string }; message?: string }) => {
        this.error.set(response.error?.detail ?? response.message ?? 'The reporting service could not be reached.');
        this.loading.set(false);
      },
    });
  }

  topHoldings(holdings: HoldingAllocation[]): HoldingAllocation[] {
    const sorted = [...holdings].sort((left, right) => Number(right.market_value) - Number(left.market_value));
    if (sorted.length <= 5) return sorted;
    const top = sorted.slice(0, 5);
    const remainder = sorted.slice(5);
    const otherValue = remainder.reduce((sum, holding) => sum + Number(holding.market_value), 0);
    const otherPercentage = remainder.reduce((sum, holding) => sum + Number(holding.percentage), 0);
    return [...top, { symbol: 'OTHER', display_name: 'Other', market_value: otherValue, percentage: otherPercentage, currency: remainder[0].currency }];
  }

  private renderCharts() {
    if (!isPlatformBrowser(this.platformId)) return;
    this.performanceChart?.destroy();
    this.holdingsChart?.destroy();
    const performanceContext = this.performanceCanvas?.nativeElement.getContext('2d');
    if (performanceContext && this.performance().length) {
      const keys: BenchmarkKey[] = ['portfolio', 'sp500', 'dow_jones', 'nasdaq_100', 'russell_2000'];
      const labels: Record<BenchmarkKey, string> = { portfolio: 'Portfolio', sp500: 'S&P 500', dow_jones: 'Dow Jones Industrial Average', nasdaq_100: 'Nasdaq-100', russell_2000: 'Russell 2000' };
      const colors = ['#006044', '#2364aa', '#af8a49', '#d1495b', '#5b6774'];
      this.performanceChart = new Chart(performanceContext, { type: 'line', data: { labels: this.performance().map((point) => point.date), datasets: keys.map((key, index) => ({ label: labels[key], data: this.performance().map((point) => Number(point.values[key] ?? NaN)), borderColor: colors[index], backgroundColor: colors[index], tension: 0.25, pointRadius: 2, spanGaps: false })) }, options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { position: 'bottom' }, tooltip: { mode: 'index', intersect: false } }, scales: { y: { title: { display: true, text: 'Indexed performance' } } } } });
    }
    const holdingsContext = this.holdingsCanvas?.nativeElement.getContext('2d');
    if (holdingsContext && this.holdings().length) {
      this.holdingsChart = new Chart(holdingsContext, { type: 'doughnut', data: { labels: this.holdings().map((holding) => holding.display_name), datasets: [{ data: this.holdings().map((holding) => Number(holding.market_value)), backgroundColor: ['#006044', '#76a923', '#2364aa', '#af8a49', '#d1495b', '#5b6774'], borderColor: '#ffffff', borderWidth: 2 }] }, options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { position: 'bottom' } } } });
    }
  }

  status(): string {
    return this.metadata()?.reporting_status ?? (this.error() ? 'UNAVAILABLE' : 'LOADING');
  }

  statusLabel(): string {
    return this.status() === 'CURRENT' ? 'Current' : this.status() === 'DATA_DELAYED' ? 'Data delayed' : this.status() === 'PARTIAL' ? 'Partial report' : this.status() === 'LOADING' ? 'Loading' : 'Unavailable';
  }

  private defaultFilters(): AnalyticsFilters {
    const end = new Date();
    const start = new Date(end);
    start.setDate(end.getDate() - 30);
    return { from: this.isoDate(start), to: this.isoDate(end), timezone: 'UTC', granularity: 'day' };
  }

  private isoDate(value: Date): string {
    return value.toISOString().slice(0, 10);
  }
}

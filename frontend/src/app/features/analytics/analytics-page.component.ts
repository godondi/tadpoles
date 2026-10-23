import { AfterViewInit, Component, ElementRef, OnDestroy, PLATFORM_ID, ViewChild, inject, signal } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink, RouterLinkActive } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Observable, catchError, combineLatest, forkJoin, of } from 'rxjs';
import { Chart, CategoryScale, LinearScale, PointElement, LineElement, LineController, DoughnutController, Tooltip, Legend, ArcElement, Filler } from 'chart.js';
import {
  AnalyticsApiService,
  AnalyticsMeasure,
  AnalyticsFilters,
  AnalyticsView,
  FreshnessResponse,
  InstrumentRow,
  InstrumentsResponse,
  MetricValue,
  ReportMetadata,
  SummaryResponse,
  TrendPoint,
  TrendsResponse,
  BenchmarkKey,
  HoldingAllocation,
  PerformancePoint,
  PortfolioHoldingsResponse,
  PortfolioPerformanceResponse,
} from '../../core/api/analytics-api.service';

Chart.register(CategoryScale, LinearScale, PointElement, LineElement, LineController, DoughnutController, Tooltip, Legend, ArcElement, Filler);

type InstrumentSortKey = 'symbol' | 'market' | 'asset_class' | 'value';

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

    <section class="freshness-rail" aria-label="Report freshness">
      <div class="freshness-status"><p class="eyebrow">Data health</p><strong>{{ statusLabel() }}</strong></div>
      <dl>
        <div><dt>Data as of</dt><dd>{{ metadata()?.data_as_of ?? 'Not available' }}</dd></div>
        <div><dt>Last successful load</dt><dd>{{ metadata()?.last_successful_load_at ?? 'Not available' }}</dd></div>
        <div><dt>Timezone</dt><dd>{{ metadata()?.reporting_timezone ?? draftFilters.timezone }}</dd></div>
        <div><dt>Watermark</dt><dd>{{ metadata()?.source_watermark ?? 'Not available' }}</dd></div>
        <div><dt>Definition</dt><dd>{{ metadata()?.definition_version ?? 'Pending approval' }}</dd></div>
      </dl>
      @if (warnings().length) { <ul class="freshness-warnings">@for (warning of warnings(); track warning) { <li>{{ warning }}</li> }</ul> }
    </section>
    @if (metadata()?.data_mode === 'DEMONSTRATION') { <section class="demo-banner" role="note"><strong>Demonstration data</strong><span>These fixed reporting fixtures are for local review and are not authoritative trading or portfolio history.</span></section> }

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
          <article class="metric-tile"><p>{{ metric.label }}</p><strong>{{ metric.value ?? '—' }}</strong><span>{{ metric.unit }}{{ metric.currency ? ' · ' + metric.currency : '' }}</span><small>Event date: {{ metric.event_date ?? 'Business event time' }}</small><small>Definition: {{ metric.definition_version ?? metadata()?.definition_version ?? 'Pending approval' }}</small></article>
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
        <div class="panel-heading"><div><p class="eyebrow">{{ view === 'trends' ? 'Time series' : view === 'instruments' ? 'Ranking' : 'Preview' }}</p><h2>{{ view === 'trends' ? 'Activity over time' : view === 'instruments' ? 'Instrument activity' : 'Instrument preview' }}</h2></div><span class="panel-note">Exact values stay visible</span></div>
        @if (view === 'trends') {
          <div class="panel-controls"><label>Measure <select [ngModel]="selectedMeasure()" (ngModelChange)="selectMeasure($event)" name="trend-measure"><option value="fills_booked">Booked fills</option><option value="orders_filled">Filled orders</option><option value="executed_quantity">Executed quantity</option></select></label><span class="panel-note">{{ measureLabel(selectedMeasure()) }} · {{ metadata()?.reporting_timezone ?? draftFilters.timezone }}</span></div>
          @if (trendPoints().length) {
            <div class="canvas-frame"><canvas #trendCanvas aria-label="Line chart showing exact analytics trend values"></canvas></div>
            <div class="table-wrapper"><table><caption>{{ measureLabel(selectedMeasure()) }} exact values</caption><thead><tr><th scope="col">Period start</th><th scope="col">Period end</th><th scope="col">Value</th></tr></thead><tbody>@for (point of trendPoints(); track point.period_start) {<tr><th scope="row">{{ point.period_start }}</th><td>{{ point.period_end }}</td><td>{{ value(point.value) }} {{ metadata()?.reporting_timezone ?? draftFilters.timezone }}</td></tr>}</tbody></table></div>
          } @else {
            <div class="visualization-empty">{{ metadata() ? 'No activity for these filters.' : 'Trend data is unavailable until the approved reporting source is connected.' }}</div>
          }
        } @else if (view === 'instruments') {
          <div class="panel-controls"><label>Measure <select [ngModel]="selectedMeasure()" (ngModelChange)="selectMeasure($event)" name="instrument-measure"><option value="fills_booked">Booked fills</option><option value="orders_filled">Filled orders</option><option value="executed_quantity">Executed quantity</option></select></label><span class="panel-note">{{ measureLabel(selectedMeasure()) }} · page {{ instrumentPage() }} of {{ pageCount() }}</span></div>
          @if (instrumentRows().length) {
            <div class="table-wrapper"><table><caption>{{ measureLabel(selectedMeasure()) }} by instrument</caption><thead><tr><th scope="col" [attr.aria-sort]="instrumentSort() === 'symbol' ? (instrumentSortDirection() === 'asc' ? 'ascending' : 'descending') : 'none'"><button class="sort-button" type="button" (click)="sortInstruments('symbol')">Symbol</button></th><th scope="col" [attr.aria-sort]="instrumentSort() === 'market' ? (instrumentSortDirection() === 'asc' ? 'ascending' : 'descending') : 'none'"><button class="sort-button" type="button" (click)="sortInstruments('market')">Market</button></th><th scope="col">Asset class</th><th scope="col">Measure</th><th scope="col" [attr.aria-sort]="instrumentSort() === 'value' ? (instrumentSortDirection() === 'asc' ? 'ascending' : 'descending') : 'none'"><button class="sort-button" type="button" (click)="sortInstruments('value')">Value</button></th><th scope="col">Unit</th><th scope="col">Currency</th></tr></thead><tbody>@for (row of sortedInstrumentRows(); track row.symbol) {<tr><th scope="row">{{ row.symbol }}</th><td>{{ row.market }}</td><td>{{ row.asset_class }}</td><td>{{ measureLabel(row.measure) }}</td><td>{{ row.value }}</td><td>{{ row.unit }}</td><td>{{ row.currency ?? '—' }}</td></tr>}</tbody></table></div>
            <div class="pagination"><button class="secondary-button" type="button" [disabled]="instrumentPage() === 1" (click)="changeInstrumentPage(-1)">Previous</button><span>Page {{ instrumentPage() }} of {{ pageCount() }}</span><button class="secondary-button" type="button" [disabled]="instrumentPage() >= pageCount()" (click)="changeInstrumentPage(1)">Next</button></div>
          } @else {
            <div class="visualization-empty">{{ metadata() ? 'No activity for these filters.' : 'Instrument data is unavailable until the approved reporting source is connected.' }}</div>
          }
        } @else {
          @if (instrumentRows().length) {
            <div class="table-wrapper"><table><caption>Top instruments by {{ measureLabel(selectedMeasure()) }}</caption><thead><tr><th scope="col">Symbol</th><th scope="col">Market</th><th scope="col">Asset class</th><th scope="col">Value</th><th scope="col">Unit</th><th scope="col">Currency</th></tr></thead><tbody>@for (row of instrumentRows(); track row.symbol) {<tr><th scope="row">{{ row.symbol }}</th><td>{{ row.market }}</td><td>{{ row.asset_class }}</td><td>{{ row.value }}</td><td>{{ row.unit }}</td><td>{{ row.currency ?? '—' }}</td></tr>}</tbody></table></div>
          } @else {
            <div class="visualization-empty">{{ metadata() ? 'No activity for these filters.' : 'Instrument preview is unavailable until the approved reporting source is connected.' }}</div>
          }
        }
      </article>
      <aside class="content-panel definitions"><p class="eyebrow">Reading this report</p><h2>Freshness & definitions</h2><dl><div><dt>Status</dt><dd>{{ statusLabel() }}</dd></div><div><dt>Event date</dt><dd>Business event time</dd></div><div><dt>Quantities</dt><dd>Kept separate by unit</dd></div><div><dt>Notional</dt><dd>Kept separate by currency</dd></div></dl></aside>
    </section>
  `,
  styles: [
    `.freshness-rail { display:grid; grid-template-columns:150px minmax(0,1fr); gap:20px; align-items:start; margin-bottom:20px; padding:16px 20px; background:#0d4b39; color:#f9fcfb; } .freshness-rail .eyebrow { color:#b9d9c4; } .freshness-status { display:grid; gap:6px; } .freshness-rail dl { display:grid; grid-template-columns:repeat(5,minmax(0,1fr)); gap:12px; margin:0; } .freshness-rail dt { color:#b9d9c4; font-size:.72rem; text-transform:uppercase; } .freshness-rail dd { margin:5px 0 0; overflow-wrap:anywhere; font-size:.82rem; } .freshness-warnings { grid-column:2; margin:0; padding-left:18px; color:#f0d7a9; font-size:.82rem; } .demo-banner { display:flex; gap:12px; align-items:baseline; margin-bottom:20px; padding:12px 16px; border-left:4px solid #af8a49; background:#fff8e9; color:#5c4317; font-size:.84rem; } .demo-banner span { color:#765f35; } .metric-tile small { display:block; margin-top:5px; color:#6c7881; font-size:.7rem; } .panel-controls { display:flex; justify-content:space-between; align-items:end; gap:16px; margin:18px 0; } .panel-controls label { min-width:190px; } .sort-button { border:0; background:transparent; color:inherit; padding:0; font-weight:800; text-align:inherit; } .sort-button:hover { color:#006044; } .pagination { display:flex; justify-content:space-between; align-items:center; gap:12px; margin-top:16px; font-size:.82rem; color:#5b6774; } .secondary-button:disabled { cursor:not-allowed; opacity:.5; } @media (max-width:900px) { .freshness-rail { grid-template-columns:1fr; } .freshness-rail dl { grid-template-columns:repeat(2,minmax(0,1fr)); } .freshness-warnings { grid-column:auto; } } @media (max-width:600px) { .panel-controls { align-items:stretch; flex-direction:column; } .panel-controls label { min-width:0; } .demo-banner { align-items:flex-start; flex-direction:column; gap:4px; } }`,
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
  readonly freshnessMetadata = signal<FreshnessResponse | null>(null);
  readonly performance = signal<PerformancePoint[]>([]);
  readonly holdings = signal<HoldingAllocation[]>([]);
  readonly trendPoints = signal<TrendPoint[]>([]);
  readonly instrumentRows = signal<InstrumentRow[]>([]);
  readonly selectedMeasure = signal<AnalyticsMeasure>('fills_booked');
  readonly instrumentPage = signal(1);
  readonly instrumentPageSize = signal(25);
  readonly instrumentTotal = signal(0);
  readonly instrumentSort = signal<InstrumentSortKey>('value');
  readonly instrumentSortDirection = signal<'asc' | 'desc'>('desc');
  readonly performancePeriod = signal<'1m' | '3m' | '12m'>('1m');
  @ViewChild('performanceCanvas') private performanceCanvas?: ElementRef<HTMLCanvasElement>;
  @ViewChild('holdingsCanvas') private holdingsCanvas?: ElementRef<HTMLCanvasElement>;
  @ViewChild('trendCanvas') private trendCanvas?: ElementRef<HTMLCanvasElement>;
  private readonly platformId = inject(PLATFORM_ID);
  private performanceChart?: Chart;
  private holdingsChart?: Chart;
  private trendChart?: Chart;
  private chartRenderTimer?: ReturnType<typeof setTimeout>;
  view: AnalyticsView = 'overview';
  title = 'Analytics overview';
  description = 'A read-only view of aggregate trading activity with explicit freshness and metric definitions.';
  draftFilters: AnalyticsFilters = this.defaultFilters();

  ngAfterViewInit() {
    this.scheduleChartRender();
  }

  ngOnDestroy() {
    if (this.chartRenderTimer) clearTimeout(this.chartRenderTimer);
    this.performanceChart?.destroy();
    this.holdingsChart?.destroy();
    this.trendChart?.destroy();
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
      const measure = params.get('measure');
      this.selectedMeasure.set(this.isMeasure(measure) ? measure : 'fills_booked');
      const page = Number(params.get('page'));
      this.instrumentPage.set(Number.isInteger(page) && page > 0 ? page : 1);
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

  selectMeasure(measure: string) {
    if (!this.isMeasure(measure)) return;
    this.selectedMeasure.set(measure);
    this.instrumentPage.set(1);
    void this.router.navigate([], { relativeTo: this.route, queryParams: { measure, page: null }, queryParamsHandling: 'merge' });
    this.load();
  }

  changeInstrumentPage(delta: number) {
    const nextPage = this.instrumentPage() + delta;
    if (nextPage < 1 || nextPage > this.pageCount()) return;
    this.instrumentPage.set(nextPage);
    void this.router.navigate([], { relativeTo: this.route, queryParams: { page: nextPage }, queryParamsHandling: 'merge' });
    this.load();
  }

  sortInstruments(key: InstrumentSortKey) {
    if (this.instrumentSort() === key) {
      this.instrumentSortDirection.update((direction) => (direction === 'asc' ? 'desc' : 'asc'));
      return;
    }
    this.instrumentSort.set(key);
    this.instrumentSortDirection.set(key === 'value' ? 'desc' : 'asc');
  }

  sortedInstrumentRows(): InstrumentRow[] {
    const key = this.instrumentSort();
    const direction = this.instrumentSortDirection() === 'asc' ? 1 : -1;
    return [...this.instrumentRows()].sort((left, right) => {
      const leftValue = key === 'value' ? Number(left.value) : String(left[key]).toLowerCase();
      const rightValue = key === 'value' ? Number(right.value) : String(right[key]).toLowerCase();
      if (leftValue < rightValue) return -1 * direction;
      if (leftValue > rightValue) return 1 * direction;
      return 0;
    });
  }

  pageCount(): number {
    return Math.max(1, Math.ceil(this.instrumentTotal() / this.instrumentPageSize()));
  }

  warnings(): string[] {
    return this.metadata()?.warnings ?? this.freshnessMetadata()?.metadata.warnings ?? [];
  }

  measureLabel(measure: AnalyticsMeasure | string): string {
    return measure === 'fills_booked' ? 'Booked fills' : measure === 'orders_filled' ? 'Filled orders' : 'Executed quantity';
  }

  value(value: number | string | null | undefined): string {
    return value === undefined || value === null ? '—' : String(value);
  }

  load() {
    this.loading.set(true);
    this.error.set('');
    this.metadata.set(null);
    this.freshnessMetadata.set(null);
    this.metrics.set([]);
    this.trendPoints.set([]);
    this.instrumentRows.set([]);
    if (this.view === 'overview') {
      forkJoin({
        summary: this.api.summary(this.draftFilters),
        performance: this.api.portfolioPerformance(this.draftFilters, this.performancePeriod()),
        holdings: this.api.portfolioHoldings(this.draftFilters),
        instruments: this.api.instruments(this.draftFilters, this.selectedMeasure(), 1, 5),
        freshness: this.api.freshness().pipe(catchError(() => of(null))),
      }).subscribe({
        next: ({ summary, performance, holdings, instruments, freshness }) => {
          this.freshnessMetadata.set(freshness);
          this.metadata.set(freshness?.metadata ?? summary.metadata);
          this.metrics.set(summary.metrics);
          this.performance.set(performance.points);
          this.holdings.set(this.topHoldings(holdings.holdings));
          this.instrumentRows.set(instruments.rows);
          this.instrumentTotal.set(instruments.total);
          this.loading.set(false);
          this.scheduleChartRender();
        },
        error: (response: { error?: { detail?: string }; message?: string }) => {
          this.error.set(response.error?.detail ?? response.message ?? 'The reporting service could not be reached.');
          this.performance.set([]);
          this.holdings.set([]);
          this.instrumentRows.set([]);
          this.loading.set(false);
          this.scheduleChartRender();
        },
      });
      return;
    }
    const freshnessRequest = this.api.freshness().pipe(catchError(() => of(null)));
    if (this.view === 'trends') {
      forkJoin({ report: this.api.trends(this.draftFilters, this.selectedMeasure()), freshness: freshnessRequest }).subscribe({
        next: ({ report, freshness }) => {
          this.freshnessMetadata.set(freshness);
          this.metadata.set(freshness?.metadata ?? report.metadata);
          this.trendPoints.set(report.points);
          this.loading.set(false);
          this.scheduleChartRender();
        },
        error: (response: { error?: { detail?: string }; message?: string }) => this.setLoadError(response),
      });
      return;
    }
    forkJoin({ report: this.api.instruments(this.draftFilters, this.selectedMeasure(), this.instrumentPage()), freshness: freshnessRequest }).subscribe({
      next: ({ report, freshness }) => {
        this.freshnessMetadata.set(freshness);
        this.metadata.set(freshness?.metadata ?? report.metadata);
        this.instrumentRows.set(report.rows);
        this.instrumentPageSize.set(report.page_size);
        this.instrumentTotal.set(report.total);
        this.loading.set(false);
      },
      error: (response: { error?: { detail?: string }; message?: string }) => this.setLoadError(response),
    });
  }

  topHoldings(holdings: HoldingAllocation[]): HoldingAllocation[] {
    const sorted = [...holdings].sort((left, right) => Number(right.market_value) - Number(left.market_value));
    if (sorted.length <= 5) return sorted;
    const top = sorted.slice(0, 5);
    const remainder = sorted.slice(5);
    const byCurrency = new Map<string, HoldingAllocation[]>();
    for (const holding of remainder) {
      const group = byCurrency.get(holding.currency) ?? [];
      group.push(holding);
      byCurrency.set(holding.currency, group);
    }
    const otherRows = [...byCurrency.entries()].map(([currency, holdingsForCurrency]) => ({
      symbol: `OTHER-${currency}`,
      display_name: byCurrency.size === 1 ? 'Other' : `Other (${currency})`,
      market_value: holdingsForCurrency.reduce((sum, holding) => sum + Number(holding.market_value), 0),
      percentage: holdingsForCurrency.reduce((sum, holding) => sum + Number(holding.percentage), 0),
      currency,
    }));
    return [...top, ...otherRows];
  }

  private renderCharts() {
    if (!isPlatformBrowser(this.platformId)) return;
    this.performanceChart?.destroy();
    this.holdingsChart?.destroy();
    this.trendChart?.destroy();
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
    const trendContext = this.trendCanvas?.nativeElement.getContext('2d');
    if (trendContext && this.trendPoints().length) {
      this.trendChart = new Chart(trendContext, { type: 'line', data: { labels: this.trendPoints().map((point) => point.period_start), datasets: [{ label: this.measureLabel(this.selectedMeasure()), data: this.trendPoints().map((point) => Number(point.value)), borderColor: '#006044', backgroundColor: '#006044', tension: 0.25, pointRadius: 2, spanGaps: false }] }, options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { display: false }, tooltip: { mode: 'index', intersect: false } }, scales: { y: { title: { display: true, text: `${this.measureLabel(this.selectedMeasure())} (${this.metadata()?.reporting_timezone ?? 'UTC'})` } } } } });
    }
  }

  status(): string {
    if (this.loading()) return 'LOADING';
    if (this.error()) return 'UNAVAILABLE';
    return this.metadata()?.reporting_status ?? 'LOADING';
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

  private isMeasure(value: string | null): value is AnalyticsMeasure {
    return value === 'fills_booked' || value === 'orders_filled' || value === 'executed_quantity';
  }

  private setLoadError(response: { error?: { detail?: string }; message?: string }) {
    this.error.set(response.error?.detail ?? response.message ?? 'The reporting service could not be reached.');
    this.loading.set(false);
    this.scheduleChartRender();
  }

  private scheduleChartRender() {
    if (!isPlatformBrowser(this.platformId)) return;
    if (this.chartRenderTimer) clearTimeout(this.chartRenderTimer);
    this.chartRenderTimer = setTimeout(() => {
      this.chartRenderTimer = undefined;
      this.renderCharts();
    });
  }
}

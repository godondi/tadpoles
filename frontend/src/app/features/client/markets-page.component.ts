import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { forkJoin, map } from 'rxjs';
import { WorkspaceApi, Instrument, Page } from '../../core/api/workspace-api.service';
import { CatalogRefreshService } from '../../core/api/catalog-refresh.service';
import { PageResource } from '../../shared/workspace/page-resource';
import { RequestStateComponent } from '../../shared/workspace/request-state.component';

type Market = '' | 'STOCK_ETF' | 'CRYPTO' | 'FOREX';
type MarketGroup = { key: Exclude<Market, ''>; title: string; hint: string; symbols: string[]; items: Instrument[] };
const FEATURED = [
  { key: 'STOCK_ETF' as const, title: 'Stocks & ETFs', hint: 'US MARKETS', symbols: ['SPY', 'QQQ', 'IWM'] },
  { key: 'CRYPTO' as const, title: 'Crypto', hint: 'DIGITAL ASSETS', symbols: ['X:BTC-USD', 'X:ETH-USD', 'X:BNB-USD'] },
  { key: 'FOREX' as const, title: 'Forex', hint: 'CURRENCY PAIRS', symbols: ['FX:EURUSD', 'FX:USDJPY', 'FX:GBPUSD'] },
];

@Component({
  selector: 'app-markets-page',
  imports: [RouterLink, FormsModule, RequestStateComponent],
  template: `
    <header class="workspace-heading market-heading">
      <div><p class="eyebrow">EXPLORE</p><h1 tabindex="-1">Markets</h1>
        <p>Browse the catalog or search stocks, crypto and currency pairs together.</p></div>
      <span class="catalog-count">{{ searchMode() ? (resource.data()?.total ?? 0) + ' results' : '3 markets' }}</span>
    </header>
    <form class="market-search panel" (ngSubmit)="search()" role="search">
      <label class="market-query">Search instruments
        <input name="search" [(ngModel)]="query" maxlength="100" placeholder="Ticker, instrument name or pair" />
      </label>
      <label>Market type
        <select name="market" [(ngModel)]="market">
          <option value="">All markets</option><option value="STOCK_ETF">Stocks &amp; ETFs</option>
          <option value="CRYPTO">Crypto</option><option value="FOREX">Forex</option>
        </select>
      </label>
      <label>Exchange
        <select name="exchange" [(ngModel)]="exchange">
          <option value="">All exchanges</option><option value="NYSE">NYSE</option>
          <option value="Nasdaq">Nasdaq</option><option value="Nasdaq Global Market">Nasdaq Global Market</option>
          <option value="Nasdaq Global Select Market">Nasdaq Global Select Market</option>
          <option value="NYSE Arca">NYSE Arca</option><option value="Cboe BZX">Cboe BZX</option>
          <option value="OTC">OTC</option>
        </select>
      </label>
      <button type="submit">Search</button>
      @if (searchMode()) { <button class="market-clear" type="button" (click)="clear()">Clear</button> }
    </form>

    <app-request-state [loading]="searchMode() ? resource.loading() && !resource.data() : featuredResource.loading() && !featuredResource.data()"
      [error]="searchMode() ? resource.error() : featuredResource.error()"
      [status]="searchMode() ? resource.status() : featuredResource.status()" (retry)="reload()" />

    @if (!searchMode()) {
      <section class="market-groups" aria-label="Featured instruments by market">
        @for (group of groups(); track group.key) {
          <section class="market-group panel">
            <header><div><p class="eyebrow">{{ group.hint }}</p><h2>{{ group.title }}</h2></div>
              <span>{{ group.items.length }} shown</span></header>
            @if (!group.items.length) { <p class="muted">{{ featuredResource.loading() ? 'Loading featured instruments…' : 'No featured instruments available.' }}</p> }
            @for (item of group.items; track item.instrumentId) {
              <a class="market-instrument" [routerLink]="['/app/instruments', item.instrumentId]">
                <span class="ticker">{{ featuredSymbol(item.ticker) }}</span><span class="instrument-name">{{ item.instrumentName }}</span>
                <span class="instrument-meta">{{ item.exchange || '—' }} · {{ item.currency }}</span><span aria-hidden="true">→</span>
              </a>
            }
            @if (group.items.length) { <button class="market-view" type="button" (click)="browse(group.key)">Browse {{ group.title.toLowerCase() }}</button> }
          </section>
        }
      </section>
      <p class="market-note">Prices and availability are shown on each instrument page. Quotes are indicative until an order is reviewed.</p>
    } @else if (resource.data(); as data) {
      <section class="panel market-results">
        <div class="results-heading"><div><p class="eyebrow">CATALOG RESULTS</p><h2>{{ data.total }} matching instruments</h2></div>
          <span>Search across all market types</span></div>
        @if (!data.items.length) { <div class="empty"><h2>No matching instruments</h2><p>Try another symbol or adjust your filters.</p></div> }
        @else { <div class="market-result-list">
          @for (item of data.items; track item.instrumentId) {
            <a class="market-result" [routerLink]="['/app/instruments', item.instrumentId]">
              <span class="ticker">{{ item.ticker }}</span><span>{{ item.instrumentName }}</span>
              <span class="market-tag">{{ marketLabel(item) }}</span><span class="instrument-meta">{{ item.exchange || '—' }} · {{ item.currency }}</span>
              <span aria-hidden="true">→</span>
            </a>
          }
        </div>
          <nav class="pagination" aria-label="Catalog pages">
            <button [disabled]="page === 0" (click)="go(page - 1)">Previous</button><span>Page {{ page + 1 }} of {{ pageCount() }}</span>
            <button [disabled]="(page + 1) * 20 >= data.total" (click)="go(page + 1)">Next</button>
          </nav>
        }
      </section>
    }
  `,
  styles: [`
    .market-heading { display:flex; justify-content:space-between; align-items:end; gap:20px; }
    .catalog-count,.market-group header>span,.results-heading>span { color:var(--text-secondary); font-size:.85rem; }
    .market-search { display:grid; grid-template-columns:minmax(220px,2fr) repeat(2,minmax(160px,1fr)) auto auto; align-items:end; gap:14px; margin-bottom:22px; }
    .market-search label { display:grid; gap:7px; font-size:.82rem; font-weight:700; color:var(--text-secondary); }
    .market-search input,.market-search select { min-width:0; width:100%; min-height:44px; padding:10px 12px; border:1px solid var(--border-gray); background:var(--surface); color:var(--text-primary); font:inherit; font-weight:400; }
    .market-search button,.market-view { min-height:44px; padding:10px 18px; border:1px solid var(--dark-green); background:var(--action-bg); color:var(--action-text); font:inherit; font-weight:700; cursor:pointer; }
    .market-search .market-clear { color:var(--dark-green); background:var(--surface); }
    .market-groups { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:18px; }
    .market-group { padding:20px; margin:0; }
    .market-group header,.results-heading { display:flex; justify-content:space-between; align-items:start; gap:12px; padding-bottom:12px; border-bottom:1px solid var(--border-gray); }
    .market-group header h2,.results-heading h2 { margin:4px 0 0; }
    .market-group .eyebrow,.results-heading .eyebrow { margin:0; }
    .market-instrument,.market-result { display:grid; align-items:center; gap:10px; color:var(--text-primary)!important; text-decoration:none; }
    .market-instrument { grid-template-columns:1fr auto; padding:14px 0; border-bottom:1px solid var(--border-gray); }
    .market-instrument .ticker,.market-instrument .instrument-name,.market-instrument .instrument-meta { grid-column:1; }
    .ticker { font-weight:800; letter-spacing:.01em; }
    .instrument-name,.instrument-meta { font-size:.82rem; color:var(--text-secondary); }
    .market-instrument>span:last-child { grid-column:2; grid-row:1 / span 3; color:var(--dark-green); font-size:1.2rem; }
    .market-result>span:last-child { grid-column:5; grid-row:1; color:var(--dark-green); font-size:1.2rem; }
    .market-view { width:100%; margin-top:16px; background:var(--surface-muted); color:var(--dark-green); }
    .market-note { margin:18px 0; color:var(--text-secondary); font-size:.88rem; }
    .market-results { padding:20px; }
    .market-result-list { display:grid; }
    .market-result { grid-template-columns:110px minmax(120px,1fr) 110px minmax(150px,.55fr) 20px; min-height:64px; padding:12px 8px; border-bottom:1px solid var(--border-gray); }
    .market-result>span { min-width:0; overflow-wrap:anywhere; }
    .market-tag { width:max-content; border-radius: 0; padding:5px 9px; color:var(--dark-green); background:var(--surface-muted); font-size:.75rem; font-weight:700; }
    .pagination button { min-height:40px; padding:8px 14px; border:1px solid var(--border-gray); background:var(--surface); color:var(--text-primary); cursor:pointer; }
    @media(max-width:1100px) { .market-result { grid-template-columns:100px minmax(0,1fr) auto; gap:8px 12px; } .market-result>.market-tag { grid-column:2; grid-row:2; } .market-result>.instrument-meta { grid-column:2; grid-row:3; } .market-result>span:last-child { grid-column:3; grid-row:1 / span 3; } }
    @media(max-width:900px) { .market-search { grid-template-columns:repeat(2,minmax(0,1fr)); } .market-query { grid-column:1/-1; } .market-groups { grid-template-columns:1fr; } }
    @media(max-width:600px) { .market-heading { align-items:start; flex-direction:column; } .market-search { grid-template-columns:1fr; } .market-query { grid-column:auto; } .market-result { grid-template-columns:1fr auto; } .market-result>* { grid-column:1!important; grid-row:auto!important; } .market-result>span:last-child { grid-column:2!important; grid-row:1 / span 4!important; } }
  `],
})
export class MarketsPageComponent {
  private readonly api = inject(WorkspaceApi);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  readonly resource = new PageResource<Page<Instrument>>();
  readonly featuredResource = new PageResource<Instrument[]>();
  readonly groups = computed<MarketGroup[]>(() => {
    const all = this.featuredResource.data() ?? [];
    return FEATURED.map(group => ({ ...group, items: group.symbols
      .map(symbol => all.find(item => item.ticker.toUpperCase() === symbol))
      .filter((item): item is Instrument => !!item) }));
  });
  readonly searchMode = signal(false);
  query = '';
  market: Market = '';
  exchange = '';
  page = 0;

  constructor() {
    this.route.queryParamMap.pipe(takeUntilDestroyed()).subscribe((params) => {
      this.query = params.get('search') ?? '';
      this.market = (params.get('market') as Market) ?? '';
      this.exchange = params.get('exchange') ?? '';
      this.page = Math.max(0, Number(params.get('page')) || 0);
      this.searchMode.set(!!(this.query || this.market || this.exchange));
      if (this.searchMode()) this.load(); else this.loadFeatured();
    });
    inject(CatalogRefreshService).watch().pipe(takeUntilDestroyed()).subscribe(() => {
      if (!this.resource.loading() && !this.featuredResource.loading()) this.reload();
    });
  }

  private loadFeatured() {
    const symbols = FEATURED.flatMap(group => group.symbols);
    this.featuredResource.load(forkJoin([
      ...symbols.map((search) => this.api.instruments({ page: 0, pageSize: 10, search })),
    ]).pipe(map(pages => [...new Map(pages.flatMap(page => page.items).map(item => [item.instrumentId, item])).values()])), true);
  }
  reload() { this.searchMode() ? this.load() : this.loadFeatured(); }
  load() {
    this.resource.load(this.api.instruments({ page: this.page, pageSize: 20, search: this.query,
      ...(this.market ? { market: this.market } : {}), ...(this.exchange ? { exchange: this.exchange } : {}) }), true);
  }
  search() { this.go(0); }
  clear() { this.query = ''; this.market = ''; this.exchange = ''; this.go(0); }
  browse(market: Market) { this.market = market; this.query = ''; this.exchange = ''; this.go(0); }
  go(page: number) {
    void this.router.navigate([], { relativeTo: this.route, queryParams: {
      search: this.query || null, market: this.market || null, exchange: this.exchange || null, page,
    } });
  }
  pageCount() { return Math.max(1, Math.ceil((this.resource.data()?.total ?? 0) / 20)); }
  featuredSymbol(ticker: string): string {
    const forex: Record<string, string> = { 'FX:EURUSD': 'EUR', 'FX:USDJPY': 'JPY', 'FX:GBPUSD': 'GBP' };
    return forex[ticker] ?? ticker.replace(/^X:/, '').replace(/-USD$/, '');
  }
  marketLabel(item: Instrument): string {
    if (item.securityType === 'CRYPTO' || item.assetClass === 'Cryptocurrency') return 'Crypto';
    if (item.securityType === 'FX_PAIR' || item.assetClass === 'Foreign Exchange') return 'Forex';
    return 'Stocks & ETFs';
  }
}

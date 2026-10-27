import { TwoDecimalsPipe } from '../../shared/two-decimals.pipe';
import { OrderUpdateHealthComponent } from '../../shared/workspace/order-update-health.component';
import { Component, inject } from '@angular/core';
import { DisplayCurrencyComponent } from './display-currency.component';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  WorkspaceApi,
  Portfolio,
  PortfolioValuation,
  Page,
  Order,
} from '../../core/api/workspace-api.service';
import { OrderStatusService } from '../../core/api/order-status.service';
import { PageResource } from '../../shared/workspace/page-resource';
import { RequestStateComponent } from '../../shared/workspace/request-state.component';
import { OrdersTableComponent } from '../../shared/workspace/orders-table.component';
import { ClientAdvisoryApi } from './advisory/client-advisory-api.service';
import { ClientAdvisory } from './advisory/client-advisory-models';
import { ClientRecommendationsComponent } from './advisory/client-recommendations.component';

@Component({
  selector: 'app-account-page',
  imports: [TwoDecimalsPipe, OrderUpdateHealthComponent,
    DisplayCurrencyComponent,
    DatePipe,
    RouterLink,
    RequestStateComponent,
    OrdersTableComponent,
    ClientRecommendationsComponent,
  ],
  template: `
    <header class="workspace-heading">
      <p class="eyebrow">MY ACCOUNT</p>
      <h1 tabindex="-1">Your account at a glance</h1>
      <p>See what is available, explore instruments and follow your orders.</p>
    </header>
    <app-order-update-health />
    <app-request-state
      [loading]="resource.loading() && !resource.data()"
      [error]="resource.error()"
      [status]="resource.status()"
      (retry)="load()"
    />
    @if (resource.data(); as data) {
      <div class="section-heading">
        <p class="eyebrow">USD ACCOUNT · INTERNAL BOOKING</p>
        <div class="actions">
          <a routerLink="/app/banking">Deposit / withdraw</a>
          <a routerLink="/app/support/new" [queryParams]="{category: 'ACCOUNT_REVIEW'}">Ask about your account</a>
          <a routerLink="/app/markets">Find an instrument →</a>
        </div>
      </div>
      <div class="account-balances">
        <section
          class="panel"
          aria-labelledby="investing-heading"
          [attr.aria-busy]="valuation.loading()"
        >
          <h2 id="investing-heading" class="eyebrow">Investing</h2>
          @if (valuation.data(); as estimate) {
            <p class="mono-value metric-value">
              {{ valuation.error() ? 'Unavailable' : ((estimate.totalValue ?? 'Unavailable') | twoDecimals) }}
              @if (!valuation.error() && estimate.totalValue !== null) {
                <small>USD</small>
              }
            </p>
            @if (!valuation.error()) {
              <p>
                Holdings estimate: {{ (estimate.holdingsValue ?? 'Unavailable') | twoDecimals }}
                @if (estimate.holdingsValue !== null) {
                  USD
                }
              </p>
              <p>
                Cash balance: {{ (estimate.cashBalance ?? 'Unavailable') | twoDecimals }}
                @if (estimate.cashBalance !== null) {
                  USD
                }
              </p>
              @if (estimate.unpricedTickers.length) {
                <p class="muted">
                  Prices unavailable for {{ estimate.unpricedTickers.join(', ') }}. The full total
                  cannot be calculated.
                </p>
              }
              <p
                class="as-of"
                title="Modified Dietz estimate; deposits and withdrawals are weighted by time invested."
              >
                <span
                  [class.return-positive]="returnTone(estimate.allTimeReturnPercent) === 'positive'"
                  [class.return-negative]="returnTone(estimate.allTimeReturnPercent) === 'negative'"
                >
                  {{ formatAllTimeReturn(estimate.allTimeReturnPercent) }}
                </span>
              </p>
            }
          } @else {
            <p class="mono-value metric-value">
              {{ valuation.loading() ? 'Calculating…' : 'Unavailable' }}
            </p>
          }
          <p class="metric-status" role="status">
            @if (valuation.loading()) {
              Updating estimate…
            }
            @if (valuation.error()) {
              Prices could not be loaded. Your settled cash is unaffected.
            }
          </p>
          <button type="button" (click)="loadValuation()" [disabled]="valuation.loading()">
            Refresh estimate
          </button>
        </section>
        <section class="panel" aria-labelledby="settled-balance-heading">
          <h2 id="settled-balance-heading" class="eyebrow">Settled balance</h2>
          @for (balance of data.balances; track balance.currency) {
            <p class="mono-value metric-value">
              {{ balance.available | twoDecimals }}
              <small>{{ balance.currency }}</small>
            </p>
            <p>Reserved for pending buys: {{ balance.reserved | twoDecimals }} {{ balance.currency }}</p>
            <p>Total settled cash: {{ balance.posted | twoDecimals }} {{ balance.currency }}</p>
          } @empty {
            <p class="mono-value metric-value">Unavailable</p>
            <p class="muted">Your USD cash account is not available.</p>
          }
        </section>
        @for (balance of data.balances; track balance.currency) {
          @if (balance.currency === 'USD') {
            <app-display-currency [availableUsd]="balance.available" />
          }
        } @empty {
          <section class="panel">
            <h2>Available cash in another currency</h2>
            <p class="muted">A USD cash balance is needed for a display estimate.</p>
          </section>
        }
      </div>
      @if (!data.tradingEnabled) {
        <p class="notice">
          New order admission is currently unavailable. Your orders and balances remain saved.
          <a routerLink="/app/support/new" [queryParams]="{category: 'TRADING_AVAILABILITY'}">Ask about trading availability</a>
        </p>
      }
      <section class="panel">
        <div class="section-heading">
          <h2>Your positions</h2>
          <a routerLink="/app/markets">Browse markets</a>
        </div>
        @if (!data.currentPositions?.length) {
          <div class="empty">
            <p>No positions</p>
          </div>
        } @else {
          <div
            class="table-wrapper"
            tabindex="0"
            aria-label="Current positions, scroll horizontally"
          >
            <table>
              <caption>Committed positions · pending buys are not included</caption>
              <thead>
                <tr>
                  <th>Instrument</th>
                  <th>Owned</th>
                  <th>Reserved to sell</th>
                  <th>Available to sell</th>
                  <th scope="col">Advisor’s</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                @for (p of data.currentPositions; track p.instrumentId) {
                  <tr>
                    <td>
                      <a [routerLink]="['/app/instruments', p.instrumentId]">{{ p.ticker }}</a>
                      <small>{{ p.name }} · {{ p.currency }}</small>
                    </td>
                    <td>{{ p.owned | twoDecimals }}</td>
                    <td>{{ p.reserved | twoDecimals }}</td>
                    <td>{{ p.available | twoDecimals }}</td>
                    <td>
                      @if (advisory.error()) {
                        <span role="status">Unavailable</span>
                      } @else if (advisory.loading() && !advisory.data()) {
                        <span role="status">Loading…</span>
                      } @else {
                        {{ guidanceStatus(p.instrumentId) }}
                      }
                    </td>
                    <td>
                      <a
                        [routerLink]="['/app/trade', p.instrumentId]"
                        [queryParams]="{ side: 'BUY' }"
                      >
                        Buy
                      </a>
                      @if (hasUnits(p.available)) {
                        <a
                          class="position-sell"
                          [routerLink]="['/app/trade', p.instrumentId]"
                          [queryParams]="{ side: 'SELL' }"
                        >
                          Sell
                        </a>
                      }
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      </section>
      <app-client-recommendations
        [advisory]="advisory.data()"
        [positions]="data.currentPositions ?? []"
        [loading]="advisory.loading()"
        [error]="advisory.error()"
        [status]="advisory.status()"
        (retry)="loadAdvisory()"
      />
      @if (data.migrationExceptions?.length) {
        <section class="notice">
          <h2>Historical records need review</h2>
          <p>These records are preserved separately and cannot be spent or sold.</p>
          <a routerLink="/app/support/new" [queryParams]="{category: 'HISTORICAL_RECORD'}">Ask about historical records</a>
          @for (e of data.migrationExceptions; track e.id) {
            <p>Reference {{ e.id }} · {{ e.kind }}
              <a routerLink="/app/support/new" [queryParams]="{category:'HISTORICAL_RECORD',exceptionId:e.id,recordKind:e.kind,reason:'PROVENANCE'}">Ask about this retained record</a>
            </p>
          }
        </section>
      }
    }
    <section class="panel">
      <div class="section-heading">
        <h2>Recent orders</h2>
        <a routerLink="/app/orders">All orders →</a>
      </div>
      <app-request-state
        [loading]="recent.loading() && !recent.data()"
        [error]="recent.error()"
        [status]="recent.status()"
        (retry)="loadOrders()"
      />
      @if (recent.data(); as orders) {
        <app-orders-table [orders]="orders.items" />
      }
    </section>
  `,
})
export class AccountPageComponent {
  private readonly api = inject(WorkspaceApi);
  private readonly advisoryApi = inject(ClientAdvisoryApi);
  readonly resource = new PageResource<Portfolio>();
  readonly recent = new PageResource<Page<Order>>();
  readonly valuation = new PageResource<PortfolioValuation>();
  readonly advisory = new PageResource<ClientAdvisory>();

  constructor() {
    this.load();
    inject(OrderStatusService)
      .watch()
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.load());
  }

  load() {
    this.resource.load(this.api.portfolio(), true);
    this.loadOrders();
    this.loadValuation();
    this.loadAdvisory();
  }

  loadAdvisory() {
    this.advisory.load(this.advisoryApi.advisory(), true);
  }

  loadValuation() {
    this.valuation.load(this.api.portfolioValuation(), true);
  }

  loadOrders() {
    this.recent.load(this.api.orders({ pageSize: 5 }), true);
  }

  hasUnits(value: string) {
    return /[1-9]/.test(value);
  }

  formatAllTimeReturn(value: string | null | undefined): string {
    if (typeof value !== 'string') return 'All Time —';
    return `All Time ${value.startsWith('-') ? '' : '+'}${value}%`;
  }

  returnTone(value: string | null | undefined): 'positive' | 'negative' | 'neutral' {
    if (typeof value !== 'string' || value === '0.00') return 'neutral';
    return value.startsWith('-') ? 'negative' : 'positive';
  }

  guidanceStatus(instrumentId: number): string {
    if (this.advisory.error() || !this.advisory.data()) return 'Unavailable';
    const status = this.advisory.data()?.guidance.find((item) => item.instrumentId === instrumentId)?.status;
    return !status || status === 'NONE' ? 'N/A' : status;
  }
}

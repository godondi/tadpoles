import { Component, inject } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { WorkspaceApi, Portfolio, Page, Order } from '../../core/api/workspace-api.service';
import { PageResource } from '../../shared/workspace/page-resource';
import { RequestStateComponent } from '../../shared/workspace/request-state.component';
import { OrdersTableComponent } from '../../shared/workspace/orders-table.component';
@Component({
  selector: 'app-account-page',
  imports: [DatePipe, DecimalPipe, RouterLink, RequestStateComponent, OrdersTableComponent],
  template: ` <header class="workspace-heading">
      <p class="eyebrow">MY ACCOUNT</p>
      <h1 tabindex="-1">{{ portfolioOnly ? 'Portfolio' : 'Your account at a glance' }}</h1>
      <p>
        {{
          portfolioOnly
            ? 'Your recorded cash and latest holding snapshots.'
            : 'Review your account, explore markets and follow your orders.'
        }}
      </p>
    </header>
    <app-request-state
      [loading]="resource.loading()"
      [error]="resource.error()"
      [status]="resource.status()"
      (retry)="load()"
    />
    @if (resource.data(); as data) {
      <div class="summary-grid">
        <section class="panel">
          <p class="eyebrow">RECORDED CASH</p>
          <h2 class="mono-value">{{ data.portfolio.postedCash | number: '1.2-2' }}</h2>
          <p>Currency not recorded</p>
          <p class="muted">Available to spend: unavailable</p>
        </section>
        <section class="panel">
          <p class="eyebrow">HOLDING SNAPSHOTS</p>
          <h2>{{ data.portfolio.positions.length }} instruments</h2>
          <p>Latest recorded positions</p>
          <a routerLink="/app/markets">Explore the market catalog →</a>
        </section>
        <section class="panel">
          <p class="eyebrow">TRADING STATUS</p>
          <h2>Not enabled yet</h2>
          <p>
            You can browse instruments and review existing records. New orders are not available.
          </p>
          <a routerLink="/app/orders">View order history →</a>
        </section>
      </div>
      <p class="as-of">Retrieved {{ data.portfolio.retrievedAt | date: 'medium' : 'UTC' }} UTC</p>
      <aside class="notice">
        <h2>About these account values</h2>
        <p>{{ data.portfolio.limitation }}</p>
      </aside>
      <section class="panel">
        <h2>Recorded holdings</h2>
        @if (!data.portfolio.positions.length) {
          <div class="empty">
            <h3>No holdings recorded</h3>
            <p>There are no recorded positions in this account.</p>
            <a routerLink="/app/markets">Browse markets</a>
          </div>
        } @else {
          <div class="table-wrapper" tabindex="0" aria-label="Holdings, scroll horizontally">
            <table>
              <caption>
                Latest snapshot per instrument · not an available-to-sell balance
              </caption>
              <thead>
                <tr>
                  <th scope="col">Instrument</th>
                  <th scope="col">Recorded quantity</th>
                  <th scope="col">Currency</th>
                  <th scope="col">Snapshot date</th>
                </tr>
              </thead>
              <tbody>
                @for (position of data.portfolio.positions; track position.holdingId) {
                  <tr>
                    <td>
                      <a [routerLink]="['/app/instruments', position.instrumentId]">{{
                        position.ticker
                      }}</a
                      ><small>{{ position.name }}</small>
                    </td>
                    <td class="mono-value">{{ position.quantity | number: '1.0-6' }}</td>
                    <td>{{ position.currency }}</td>
                    <td>{{ position.asOfDate }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      </section>
      @if (!portfolioOnly) {
        <section class="panel">
          <div class="section-heading">
            <h2>Recent orders</h2>
            <a routerLink="/app/orders">All orders →</a>
          </div>
          <app-orders-table [orders]="data.orders.items" />
        </section>
      }
    }`,
})
export class AccountPageComponent {
  private readonly api = inject(WorkspaceApi);
  readonly portfolioOnly = inject(ActivatedRoute).snapshot.data['portfolioOnly'] === true;
  readonly resource = new PageResource<{ portfolio: Portfolio; orders: Page<Order> }>();
  constructor() {
    this.load();
  }
  load() {
    this.resource.load(
      forkJoin({ portfolio: this.api.portfolio(), orders: this.api.orders({ pageSize: 5 }) }),
    );
  }
}

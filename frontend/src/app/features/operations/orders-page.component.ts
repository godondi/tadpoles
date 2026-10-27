import { TwoDecimalsPipe } from '../../shared/two-decimals.pipe';
import { RequestStateComponent } from '../../shared/workspace/request-state.component';
import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { OperationsApi, OrderPage } from '../../core/api/operations-api.service';
import { CatalogRefreshService } from '../../core/api/catalog-refresh.service';
import { OperationsContextComponent } from './operations-context.component';
import { OperationsResource } from './operations-resource';
import { utcFilters } from '../../shared/workspace/utc-filter';

@Component({
  selector: 'app-operations-orders',
  imports: [TwoDecimalsPipe, RequestStateComponent, DatePipe, FormsModule, RouterLink, OperationsContextComponent],
  template: `
    <header class="workspace-heading">
      <p class="eyebrow">TRADING OPERATIONS</p>
      <h1 tabindex="-1">{{ exceptions ? 'Exception queue' : 'Orders & monitoring' }}</h1>
      <p>Read-only investigation of permitted accounts. Fills are booked internally in USD.</p>
    </header>
    <app-operations-context />
    @if (api.context()) {
      <section class="panel">
        @if (validation()) {
          <p class="notice" role="alert">{{ validation() }}</p>
        }
        <form class="filters" (ngSubmit)="search()">
          <div class="form-field">
            <label for="order-ref">Exact order reference</label
            ><input
              id="order-ref"
              name="orderId"
              [(ngModel)]="filters['orderId']"
              inputmode="numeric"
              maxlength="10"
            />
          </div>
          <div class="form-field">
            <label for="account-ref">Approved account reference</label
            ><input
              id="account-ref"
              name="account"
              [(ngModel)]="filters['accountReference']"
              maxlength="80"
              autocomplete="off"
            />
          </div>
          <div class="form-field">
            <label for="instrument-ref">Instrument ID</label
            ><input
              id="instrument-ref"
              name="instrument"
              [(ngModel)]="filters['instrumentId']"
              inputmode="numeric"
              maxlength="10"
            />
          </div>
          <div class="form-field">
            <label for="order-kind">Record kind</label
            ><select id="order-kind" name="kind" [(ngModel)]="filters['recordKind']">
              <option value="SELF_DIRECTED">Self-directed</option>
              <option value="LEGACY">Legacy (exact reference only)</option>
            </select>
          </div>
          <div class="form-field">
            <label for="order-status">Status</label
            ><select id="order-status" name="status" [(ngModel)]="filters['status']">
              <option value="">All</option>
              @for (state of statuses(); track state) {
                <option [value]="state">{{ state }}</option>
              }
            </select>
          </div>
          <div class="form-field">
            <label for="order-side">Side</label
            ><select id="order-side" name="side" [(ngModel)]="filters['side']">
              <option value="">All</option>
              <option>BUY</option>
              <option>SELL</option>
            </select>
          </div>
          <div class="form-field">
            <label for="rejection-stage">Rejection stage</label
            ><select id="rejection-stage" name="stage" [(ngModel)]="filters['rejectionStage']">
              <option value="">All</option>
              <option>ADMISSION</option>
              <option>EXECUTION</option>
            </select>
          </div>
          <div class="form-field">
            <label for="order-from">Submitted from (UTC)</label
            ><input id="order-from" name="from" type="datetime-local" [(ngModel)]="from" />
          </div>
          <div class="form-field">
            <label for="order-to">Submitted before (UTC)</label
            ><input id="order-to" name="to" type="datetime-local" [(ngModel)]="to" />
          </div>
          <div class="form-field">
            <label for="order-queue">Queue preset</label
            ><select id="order-queue" name="queue" [(ngModel)]="filters['queue']">
              <option value="">All permitted orders</option>
              <option value="REJECTED">Rejected</option>
              <option value="DELAYED">Past persisted deadline</option>
              <option value="RETRYING">Retrying / expired lease</option>
            </select>
          </div>
          <button [disabled]="resource.loading()">Search</button>
          <button type="button" (click)="clearFilters()">Clear filters</button>
          <button type="button" (click)="load()" [disabled]="resource.loading()">Refresh</button>
        </form>
        <p>
          Submission ranges are UTC, include the start and exclude the end, and span at most 31
          days. Default: the last seven days plus today. Acceptance is shown separately. A retry is
          not a new order or fill.
        </p>
        <app-request-state
          [loading]="resource.loading() && !resource.data()"
          [error]="resource.message()"
          [status]="resource.status()"
          (retry)="load()"
        />
        @if (resource.stale()) {
          <p class="notice" role="status">Results are stale; the latest refresh failed.</p>
        }
        @if (resource.data(); as page) {
          <p>
            Retrieved {{ page.retrievedAt | date: 'medium' : 'UTC' }} UTC. Paging reflects live
            state, not a fixed evidence snapshot.
          </p>
          <div
            class="table-wrapper"
            tabindex="0"
            aria-label="Permitted orders, scroll horizontally"
          >
            <table>
              <caption>
                Permitted orders
              </caption>
              <thead>
                <tr>
                  <th>Order / account</th>
                  <th>Instruction</th>
                  <th>State / kind</th>
                  <th>Submission / acceptance (UTC)</th>
                  <th>Investigation</th>
                </tr>
              </thead>
              <tbody>
                @for (order of page.items; track order.orderId) {
                  <tr>
                    <td>
                      {{ order.orderId }}<small>{{ order.accountReference }}</small>
                    </td>
                    <td>
                      {{ order.side }} {{ order.quantity | twoDecimals }} {{ order.ticker
                      }}<small>{{ order.currency }} · {{ order.executionMode }}</small>
                    </td>
                    <td>
                      {{ order.status
                      }}<small
                        >{{ order.recordKind }} ·
                        {{ order.queueCategory ?? 'No queue category' }}</small
                      ><small>{{ order.rejectionCode }}</small>
                    </td>
                    <td>
                      {{
                        order.submittedAt
                          ? (order.submittedAt | date: 'medium' : 'UTC')
                          : 'Precise submission time not recorded'
                      }}<small>{{
                        order.acceptedAt
                          ? (order.acceptedAt | date: 'medium' : 'UTC')
                          : 'No recorded acceptance'
                      }}</small>
                    </td>
                    <td>
                      <a
                        [routerLink]="['/operations/orders', order.orderId]"
                        [queryParams]="{ returnTo: returnPath() }"
                        >Investigate</a
                      >
                    </td>
                  </tr>
                } @empty {
                  <tr>
                    <td colspan="5">No permitted orders match these filters.</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
          <nav class="pagination" aria-label="Operations order pages">
            <button type="button" (click)="load(true)" [disabled]="!cursor">First page</button
            ><button
              type="button"
              (click)="next()"
              [disabled]="!page.hasMore || resource.loading()"
            >
              Next page
            </button>
          </nav>
        }
      </section>
    }
  `,
})
export class OperationsOrdersComponent {
  readonly api = inject(OperationsApi);
  readonly route = inject(ActivatedRoute);
  readonly router = inject(Router);
  readonly resource = new OperationsResource<OrderPage>();
  readonly exceptions = this.route.snapshot.data['exceptions'] === true;
  filters: Record<string, string> = {
    status: '',
    side: '',
    rejectionStage: '',
    recordKind: 'SELF_DIRECTED',
    queue: this.exceptions ? 'REJECTED' : '',
  };
  from = '';
  to = '';
  cursor = '';
  readonly validation = signal('');
  private applied: Record<string, string> = {};
  private hasApplied = false;
  constructor() {
    const params = this.route.snapshot.queryParamMap;
    for (const key of ['status', 'side', 'recordKind', 'rejectionStage', 'queue'])
      if (params.has(key)) this.filters[key] = params.get(key)!;
    this.from = params.get('from') ?? '';
    this.to = params.get('to') ?? '';
    this.apply();
    this.api.changed.pipe(takeUntilDestroyed()).subscribe(() => {
      this.cursor = '';
      if (this.api.context()) this.load(true);
    });
    inject(CatalogRefreshService)
      .watch()
      .pipe(takeUntilDestroyed())
      .subscribe(() => {
        if (this.api.context() && !this.resource.loading()) this.load();
      });
    if (this.api.context()) this.load(true);
  }
  statuses() {
    return this.filters['recordKind'] === 'LEGACY'
      ? ['PENDING', 'APPROVED', 'EXECUTED', 'REJECTED']
      : ['ACCEPTED', 'EXECUTING', 'FILLED', 'REJECTED'];
  }
  private apply() {
    try {
      const dates = utcFilters(this.from, this.to);
      this.applied = {
        ...Object.fromEntries(Object.entries(this.filters).filter(([, v]) => !!v)),
        ...dates,
      };
      this.validation.set('');
      this.hasApplied = true;
      return true;
    } catch (error) {
      this.validation.set((error as Error).message);
      return false;
    }
  }
  search() {
    if (!this.apply()) return;
    const queryParams: Record<string, string> = {};
    for (const key of ['status', 'side', 'recordKind', 'rejectionStage', 'queue'])
      if (this.filters[key]) queryParams[key] = this.filters[key];
    if (this.from) queryParams['from'] = this.from;
    if (this.to) queryParams['to'] = this.to;
    void this.router.navigate([], { relativeTo: this.route, queryParams, replaceUrl: true });
    this.load(true);
  }
  load(reset = false) {
    if (!this.api.context() || !this.hasApplied) return;
    if (reset) this.cursor = '';
    this.resource.load(
      this.api.orders({ ...this.applied, ...(this.cursor ? { cursor: this.cursor } : {}) }),
      !reset,
    );
  }
  next() {
    this.cursor = this.resource.data()?.nextCursor ?? '';
    this.resource.clear();
    this.load();
  }
  clearFilters() {
    this.filters = {
      recordKind: 'SELF_DIRECTED',
      status: '',
      side: '',
      rejectionStage: '',
      queue: '',
    };
    this.from = '';
    this.to = '';
    this.search();
  }
  returnPath() {
    return this.router.url;
  }
}

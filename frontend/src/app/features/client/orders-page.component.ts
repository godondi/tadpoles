import { Component, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { WorkspaceApi, Order, Page } from '../../core/api/workspace-api.service';
import { PageResource } from '../../shared/workspace/page-resource';
import { RequestStateComponent } from '../../shared/workspace/request-state.component';
import { OrdersTableComponent } from '../../shared/workspace/orders-table.component';
@Component({
  selector: 'app-orders-page',
  imports: [FormsModule, RequestStateComponent, OrdersTableComponent],
  template: ` <header class="workspace-heading">
      <p class="eyebrow">MY ACCOUNT</p>
      <h1 tabindex="-1">Orders</h1>
      <p>Your chronological order records. Open a reference to inspect its recorded outcome.</p>
    </header>
    <form class="filters" (ngSubmit)="go(0)">
      <label
        >Status<select name="status" [(ngModel)]="status">
          <option value="">All statuses</option>
          @for (state of states; track state) {
            <option [value]="state">{{ state }}</option>
          }
        </select></label
      ><label
        >Side<select name="side" [(ngModel)]="side">
          <option value="">Buy and sell</option>
          <option>BUY</option>
          <option>SELL</option>
        </select></label
      ><label>From date<input name="from" type="date" [(ngModel)]="from" /></label
      ><label>To date<input name="to" type="date" [(ngModel)]="to" /></label
      ><button type="submit">Apply filters</button>
    </form>
    @if (instrumentId) {
      <p>
        Showing orders for the instrument you selected.
        <button type="button" (click)="instrumentId = ''; go(0)">Clear instrument filter</button>
      </p>
    }
    <p class="muted">
      These are older order records. A pending or approved status does not confirm that an order was
      accepted. Open an order to see its recorded outcome and any missing details.
    </p>
    <app-request-state
      [loading]="resource.loading()"
      [error]="resource.error()"
      [status]="resource.status()"
      (retry)="load()"
    />
    @if (resource.data(); as data) {
      <section class="panel">
        <app-orders-table [orders]="data.items" />
        <nav class="pagination" aria-label="Order history pages">
          <button [disabled]="page === 0" (click)="go(page - 1)">Previous</button
          ><span>Page {{ page + 1 }} · {{ data.total }} records</span
          ><button [disabled]="(page + 1) * 20 >= data.total" (click)="go(page + 1)">Next</button>
        </nav>
      </section>
    }`,
})
export class OrdersPageComponent {
  private readonly api = inject(WorkspaceApi);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  readonly resource = new PageResource<Page<Order>>();
  readonly states = ['PENDING', 'APPROVED', 'REJECTED', 'EXECUTED'];
  status = '';
  side = '';
  from = '';
  to = '';
  instrumentId = '';
  page = 0;
  constructor() {
    this.route.queryParamMap.pipe(takeUntilDestroyed()).subscribe((params) => {
      this.status = params.get('status') ?? '';
      this.side = params.get('side') ?? '';
      this.from = params.get('from') ?? '';
      this.to = params.get('to') ?? '';
      this.instrumentId = params.get('instrumentId') ?? '';
      this.page = Math.max(0, Number(params.get('page')) || 0);
      this.load();
    });
  }
  params(): Record<string, string | number> {
    return Object.fromEntries(
      Object.entries({
        page: this.page,
        status: this.status,
        side: this.side,
        from: this.from,
        to: this.to,
        instrumentId: this.instrumentId,
      }).filter(([, value]) => value !== '' && value !== null),
    );
  }
  load() {
    this.resource.load(this.api.orders(this.params()));
  }
  go(page: number) {
    this.page = page;
    void this.router.navigate([], { relativeTo: this.route, queryParams: this.params() });
  }
}

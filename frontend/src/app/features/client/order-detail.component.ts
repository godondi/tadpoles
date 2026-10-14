import { Component, inject } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { WorkspaceApi, OrderDetail } from '../../core/api/workspace-api.service';
import { PageResource } from '../../shared/workspace/page-resource';
import { RequestStateComponent } from '../../shared/workspace/request-state.component';
@Component({
  selector: 'app-order-detail',
  imports: [DecimalPipe, RouterLink, RequestStateComponent],
  template: ` <a routerLink="/app/orders">← All orders</a>
    <header class="workspace-heading">
      <p class="eyebrow">ORDER RECORD</p>
      <h1 tabindex="-1">Order #{{ id }}</h1>
    </header>
    <app-request-state
      [loading]="resource.loading()"
      [error]="resource.error()"
      [status]="resource.status()"
      (retry)="load()"
    />
    @if (resource.data(); as data) {
      <section class="panel">
        <h2>{{ data.order.side }} · {{ data.order.ticker }}</h2>
        <dl class="details">
          <div>
            <dt>Instrument</dt>
            <dd>
              <a [routerLink]="['/app/instruments', data.order.instrumentId]">{{
                data.order.instrumentName
              }}</a>
            </dd>
          </div>
          <div>
            <dt>Recorded status</dt>
            <dd>{{ data.order.status }}</dd>
          </div>
          <div>
            <dt>Quantity</dt>
            <dd>{{ data.order.quantity | number: '1.0-6' }}</dd>
          </div>
          <div>
            <dt>Recorded price</dt>
            <dd>{{ data.order.recordedPrice | number: '1.2-2' }} {{ data.order.currency }}</dd>
          </div>
          <div>
            <dt>Trade date</dt>
            <dd>{{ data.order.tradeDate }}</dd>
          </div>
          <div>
            <dt>Execution timestamp (time zone unknown)</dt>
            <dd>{{ data.order.executedAt || 'Not recorded' }}</dd>
          </div>
          <div>
            <dt>Recorded reason</dt>
            <dd>{{ data.order.reason || 'Not recorded' }}</dd>
          </div>
        </dl>
      </section>
      <section class="notice">
        <h2>Incomplete historical evidence</h2>
        <p>{{ data.explanation }}</p>
        <p>Acceptance timeline, execution quote, fills and posting references: not recorded.</p>
      </section>
    }`,
})
export class OrderDetailComponent {
  private readonly api = inject(WorkspaceApi);
  readonly resource = new PageResource<OrderDetail>();
  id = '';
  constructor() {
    inject(ActivatedRoute)
      .paramMap.pipe(takeUntilDestroyed())
      .subscribe((params) => {
        this.id = params.get('id') ?? '';
        this.load();
      });
  }
  load() {
    this.resource.load(this.api.order(this.id));
  }
}

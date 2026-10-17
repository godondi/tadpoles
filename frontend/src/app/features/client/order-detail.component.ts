import { takeWhile } from 'rxjs';
import { Component, inject } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { WorkspaceApi, OrderDetail } from '../../core/api/workspace-api.service';
import { OrderStatusService } from '../../core/api/order-status.service';
import { PageResource } from '../../shared/workspace/page-resource';
import { RequestStateComponent } from '../../shared/workspace/request-state.component';

@Component({
  selector: 'app-order-detail',
  imports: [DatePipe, DecimalPipe, RouterLink, RequestStateComponent],
  template: `<a routerLink="/app/orders">← All orders</a>
    <header class="workspace-heading">
      <p class="eyebrow">ORDER RECEIPT</p>
      <h1 tabindex="-1">Order #{{ id }}</h1>
    </header>
    <app-request-state
      [loading]="resource.loading() && !resource.data()"
      [error]="resource.error()"
      [status]="resource.status()"
      (retry)="load()"
    />
    @if (resource.data(); as data) {
      <section class="panel" aria-live="polite">
        <p class="eyebrow">
          {{
            data.order.executionMode === 'INTERNAL'
              ? 'INTERNAL BOOKING · ZERO PROJECT FEES'
              : 'HISTORICAL RECORD'
          }}
        </p>
        <h2>
          {{ data.order.side }} {{ data.order.exactQuantity ?? data.order.quantity }}
          {{ data.order.ticker }}
        </h2>
        <p class="status-label">{{ data.order.status }}</p>
        <p>{{ data.order.reason }}</p>
        @if (pending()) {
          <p>
            Your order is saved and processing. It has not filled yet. Status updates automatically
            for up to 10 minutes.
          </p>
        }
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
            <dt>Submitted</dt>
            <dd>
              {{
                data.order.submittedAt
                  ? (data.order.submittedAt | date: 'medium' : 'UTC') + ' UTC'
                  : data.order.tradeDate
              }}
            </dd>
          </div>
          @if (data.order.recordKind === 'SELF_DIRECTED') {
            <div>
              <dt>Accepted</dt>
              <dd>
                {{
                  data.order.acceptedAt
                    ? (data.order.acceptedAt | date: 'medium' : 'UTC') + ' UTC'
                    : 'Not accepted'
                }}
              </dd>
            </div>
            <div>
              <dt>Reservation</dt>
              <dd>
                {{
                  data.reservation
                    ? data.reservation.amount +
                      ' ' +
                      data.reservation.resource +
                      ' · ' +
                      data.reservation.state
                    : 'None'
                }}
              </dd>
            </div>
          } @else {
            <div>
              <dt>Recorded price (not proof of fill)</dt>
              <dd>{{ data.order.recordedPrice | number: '1.2-2' }} {{ data.order.currency }}</dd>
            </div>
            <div>
              <dt>Execution time (zone unknown)</dt>
              <dd>{{ data.order.executedAt || 'Not recorded' }}</dd>
            </div>
          }
        </dl>
        <div class="actions">
          <a routerLink="/app/overview">View updated portfolio →</a
          ><button type="button" (click)="load()">Refresh status</button>
        </div>
      </section>
      @for (fill of data.fills; track fill.fillId) {
        <section class="panel">
          <h2>Internal fill booked</h2>
          <dl class="details">
            <div>
              <dt>Execution price</dt>
              <dd>{{ fill.price }} {{ fill.currency }}</dd>
            </div>
            <div>
              <dt>{{ data.order.side === 'BUY' ? 'Cash debited' : 'Cash credited' }}</dt>
              <dd>{{ fill.gross }} {{ fill.currency }}</dd>
            </div>
            <div>
              <dt>Position {{ data.order.side === 'BUY' ? 'added' : 'removed' }}</dt>
              <dd>{{ fill.quantity }} units</dd>
            </div>
            <div>
              <dt>Booked</dt>
              <dd>{{ fill.bookedAt | date: 'medium' : 'UTC' }} UTC</dd>
            </div>
          </dl>
          <p class="muted">
            Execution {{ fill.executionReference }} · posting {{ fill.postingGroup }}
          </p>
        </section>
      }
      @if (data.events?.length) {
        <section class="panel">
          <h2>What happened</h2>
          <ol class="order-timeline">
            @for (event of data.events; track event.eventId) {
              <li>
                <strong>{{ event.status }}</strong>
                <p>{{ event.reason }}</p>
                <small>{{ event.occurredAt | date: 'medium' : 'UTC' }} UTC</small>
              </li>
            }
          </ol>
        </section>
      }
      @if (data.decisions?.length) {
        <details class="panel">
          <summary>Price and rule checks</summary>
          @for (decision of data.decisions; track $index) {
            <p>
              {{ decision.stage }} · {{ decision.passed ? 'Passed' : 'Rejected' }} ·
              {{ decision.explanation }}
            </p>
          }
        </details>
      }
      @if (data.evidenceCompleteness !== 'RETAINED_INTERNAL_EVIDENCE') {
        <section class="notice">
          <h2>Incomplete historical evidence</h2>
          <p>{{ data.explanation }}</p>
        </section>
      }
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
    inject(OrderStatusService)
      .watch()
      .pipe(
        takeWhile(() => this.pending() || this.resource.loading()),
        takeUntilDestroyed(),
      )
      .subscribe(() => {
        if (this.pending()) this.load(true);
      });
  }

  pending() {
    return ['ACCEPTED', 'EXECUTING'].includes(this.resource.data()?.order.status ?? '');
  }

  load(background = false) {
    this.resource.load(this.api.order(this.id), background);
  }
}

import { OrderUpdateHealthComponent } from '../../shared/workspace/order-update-health.component';
import { takeWhile } from 'rxjs';
import { Component, inject } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { WorkspaceApi, OrderDetail, OrderEvent, Page } from '../../core/api/workspace-api.service';
import { OrderStatusService } from '../../core/api/order-status.service';
import { PageResource } from '../../shared/workspace/page-resource';
import { RequestStateComponent } from '../../shared/workspace/request-state.component';

@Component({
  selector: 'app-order-detail',
  imports: [OrderUpdateHealthComponent, DatePipe, DecimalPipe, RouterLink, RequestStateComponent],
  template: `<a routerLink="/app/orders">← All orders</a>
    <header class="workspace-heading">
      <p class="eyebrow">ORDER RECEIPT</p>
      <h1 tabindex="-1">Order #{{ id }}</h1>
    </header>
    <app-order-update-health />
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
        <a routerLink="/app/support/new" [queryParams]="{ orderId: data.order.orderId }"
          >Ask about this order</a
        >
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
      @if (data.events?.length || data.eventTotal) {
        <section class="panel">
          <h2>What happened</h2>
          <ol class="order-timeline">
            @for (
              event of eventPage === 0 ? data.events : timeline.data()?.items;
              track event.eventId
            ) {
              <li>
                <strong>{{ event.status }}</strong>
                <p>{{ event.reason }}</p>
                <small>{{ event.occurredAt | date: 'medium' : 'UTC' }} UTC</small>
              </li>
            }
          </ol>
          @if (timeline.error()) {
            <p role="alert">{{ timeline.error() }}</p>
            <button (click)="events(eventPage)">Retry event page</button>
          }
          @if ((data.eventTotal ?? 0) > 100) {
            <p>
              Page {{ eventPage + 1 }} · {{ data.eventTotal }} retained events. Only this page is
              displayed.
            </p>
            <button
              (click)="events(eventPage - 1)"
              [disabled]="eventPage === 0 || timeline.loading()"
            >
              Previous events</button
            ><button
              (click)="events(eventPage + 1)"
              [disabled]="(eventPage + 1) * 100 >= (data.eventTotal ?? 0) || timeline.loading()"
            >
              Next events
            </button>
          }
        </section>
      }
      @if (data.quotes?.length) {
        <section class="panel">
          <h2>Retained prices</h2>
          <p>
            Admission quotes supported acceptance checks. Execution quotes record later booking or
            rejection checks. A current chart does not reconstruct either price.
          </p>
          @for (quote of data.quotes; track quote.quoteId) {
            <article>
              <h3>{{ quote.stage }} · {{ quote.applicableSide }}</h3>
              <p>
                {{ quote.price ?? 'Price unavailable' }} {{ quote.currency }} ·
                {{ quote.source ?? 'Source not retained' }}
              </p>
              <p>
                Provider time {{ quote.providerTime | date: 'medium' : 'UTC' }} UTC · observed
                {{ quote.receivedAt | date: 'medium' : 'UTC' }} UTC ·
                {{
                  quote.stale === null
                    ? 'Freshness not retained'
                    : quote.stale
                      ? 'Reported stale'
                      : 'Reported fresh'
                }}
              </p>
              <p>
                {{
                  quote.usedForFill
                    ? 'Linked to the committed internal fill'
                    : 'Retained decision observation; not a committed fill'
                }}
              </p>
            </article>
          }
          <p>Coverage: {{ data.quoteCoverage }}</p>
        </section>
      }
      @if (data.decisions?.length) {
        <details class="panel">
          <summary>Price and rule checks</summary>
          @for (decision of data.decisions; track $index) {
            <p>
              {{ decision.stage }} · {{ decision.passed ? 'Passed' : 'Rejected' }} ·
              {{ decision.explanation }} · {{ decision.code }}
            </p>
            @if (decision.checks?.length) {
              <ul>
                @for (check of decision.checks; track check.code) {
                  <li>{{ check.code }} · {{ check.result }}</li>
                }
              </ul>
            } @else {
              <p>Granular check outcomes were not retained.</p>
            }
          }
        </details>
      }
      @if (data.evidenceChecks?.length) {
        <details class="panel">
          <summary>Retained evidence checks</summary>
          @for (check of data.evidenceChecks; track check.code) {
            <p>{{ check.explanation }} · {{ check.state }}</p>
          }
        </details>
      }
      @if (data.evidenceCompleteness !== 'RETAINED_INTERNAL_EVIDENCE') {
        <section class="notice">
          <h2>
            {{
              data.evidenceCompleteness === 'PENDING'
                ? 'Still processing'
                : data.evidenceCompleteness === 'INTEGRITY_FINDING'
                  ? 'Retained evidence needs review'
                  : data.evidenceCompleteness === 'PARTIAL'
                    ? 'Partial evidence coverage'
                    : 'Incomplete historical evidence'
            }}
          </h2>
          <p>{{ data.explanation }}</p>
        </section>
      }
    }`,
})
export class OrderDetailComponent {
  private readonly api = inject(WorkspaceApi);
  readonly resource = new PageResource<OrderDetail>();
  readonly timeline = new PageResource<Page<OrderEvent>>();
  eventPage = 0;
  id = '';

  constructor() {
    inject(ActivatedRoute)
      .paramMap.pipe(takeUntilDestroyed())
      .subscribe((params) => {
        this.id = params.get('id') ?? '';
        this.eventPage = 0;
        this.timeline.data.set(null);
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

  events(page: number) {
    this.eventPage = page;
    if (page > 0) this.timeline.load(this.api.orderEvents(this.id, page));
  }

  load(background = false) {
    this.resource.load(this.api.order(this.id), background);
    if (this.eventPage > 0) this.events(this.eventPage);
  }
}

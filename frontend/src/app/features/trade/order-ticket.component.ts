import { Component, DestroyRef, ElementRef, OnChanges, inject, input, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Subscription, takeUntil, timeout } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import {
  WorkspaceApi,
  OrderCommand,
  OrderPreview,
  OrderReceipt,
} from '../../core/api/workspace-api.service';
import { PendingOrder, PendingOrderService } from './pending-order.service';
import { createIdempotencyKey } from '../../core/idempotency-key';

@Component({
  selector: 'app-order-ticket',
  imports: [FormsModule, DatePipe, RouterLink],
  template: ` <section class="panel order-ticket" aria-labelledby="ticket-heading">
    <p class="eyebrow">INTERNAL BOOKING · ZERO PROJECT FEES</p>
    <h2 id="ticket-heading">{{ side === 'BUY' ? 'Buy' : 'Sell' }} {{ ticker() }}</h2>
    <p class="muted">
      Orders use provider quotes and are booked inside this application. No external order is
      routed.
    </p>
    @if (bankRequired()) {
      <p class="notice">
        Add a saved bank account before buying or selling. Your draft can be kept while you add it.
      </p>
      <a
        routerLink="/app/banking"
        [queryParams]="{ returnTo: '/app/trade/' + instrumentId() + '?side=' + side }"
        (click)="saveDraft()"
        >Add bank account and return</a
      >
    }
    @if (pending()) {
      <section class="notice" aria-live="polite">
        <h3>Checking whether your order was received</h3>
        <p>Your instruction is saved in this tab. Keep this request until its outcome is known.</p>
        <p>
          {{ pending()!.command.side }} {{ pending()!.command.quantity }} units · instrument #{{
            pending()!.command.instrumentId
          }}
        </p>
        <div class="actions">
          <button type="button" [disabled]="busy()" (click)="recover()">Check order status</button>
          <button type="button" [disabled]="busy()" (click)="send()">
            Retry the same instruction
          </button>
        </div>
        <p class="muted">
          A missing response does not mean the order failed. Retrying uses the same request
          reference.
        </p>
      </section>
    } @else if (!receipt()) {
      <form (ngSubmit)="review()">
        <fieldset [disabled]="busy()">
          <legend>1. Choose your instruction</legend>
          <div class="side-options">
            <label
              ><input
                type="radio"
                name="side"
                value="BUY"
                [(ngModel)]="side"
                (ngModelChange)="invalidate()"
              />
              Buy</label
            >
            <label
              ><input
                type="radio"
                name="side"
                value="SELL"
                [(ngModel)]="side"
                (ngModelChange)="invalidate()"
              />
              Sell</label
            >
          </div>
          <label for="order-quantity">Quantity <span class="muted">(units)</span></label>
          <input
            id="order-quantity"
            name="quantity"
            type="text"
            inputmode="decimal"
            autocomplete="off"
            [(ngModel)]="quantity"
            (ngModelChange)="invalidate()"
            aria-describedby="quantity-help ticket-message"
          />
          <p id="quantity-help" class="muted">
            Enter the number of shares or base units. The next step checks available cash and units.
          </p>
          <button type="submit" [disabled]="busy() || !validQuantity()">
            {{ busy() ? 'Checking…' : 'Review estimated impact' }}
          </button>
        </fieldset>
      </form>
      @if (preview(); as p) {
        <section class="order-review" aria-labelledby="review-heading">
          <h3 id="review-heading" tabindex="-1">2. Review your {{ side.toLowerCase() }}</h3>
          @if (side === 'SELL' && hasUnits(p.availableQuantity)) {
            <button type="button" data-sell-all (click)="sellAll(p.availableQuantity)">
              Sell all available ({{ p.availableQuantity }} units)
            </button>
          }
          @if (!p.eligible) {
            <p role="alert">{{ p.reason }}</p>
          } @else {
            <p>{{ quantity }} {{ p.quantityUnit }} · {{ p.venue }}</p>
            <dl class="details">
              <div>
                <dt>Estimated {{ side === 'BUY' ? 'cost' : 'proceeds' }}</dt>
                <dd class="mono-value">{{ p.gross }} {{ p.currency }}</dd>
              </div>
              <div>
                <dt>{{ side === 'BUY' ? 'Ask' : 'Bid' }} per unit</dt>
                <dd>{{ p.price }} {{ p.currency }}</dd>
              </div>
              <div>
                <dt>Available cash</dt>
                <dd>{{ p.availableCash }} {{ p.currency }}</dd>
              </div>
              <div>
                <dt>Available to sell</dt>
                <dd>{{ p.availableQuantity }} {{ p.quantityUnit }}</dd>
              </div>
              <div>
                <dt>Estimated cash after fill</dt>
                <dd>{{ p.projectedCash }} {{ p.currency }}</dd>
              </div>
              <div>
                <dt>Estimated units after fill</dt>
                <dd>{{ p.projectedQuantity }} {{ p.quantityUnit }}</dd>
              </div>
            </dl>
            <p>Project fee: {{ p.fee }} {{ p.currency }}</p>
            <p class="as-of">Provider quote {{ p.quoteTime | date: 'medium' : 'UTC' }} UTC</p>
            <p class="notice">
              Execution price may change. This estimate is not a maximum-spend limit. Your order may
              be rejected if available funds are insufficient at execution.
            </p>
            @if (expired()) {
              <p role="status">This estimate has expired. Review again for a fresh quote.</p>
            }
            <button type="button" [disabled]="busy() || expired()" (click)="confirm()">
              Confirm {{ side.toLowerCase() }}
            </button>
          }
        </section>
      }
    }
    @if (receipt(); as r) {
      <section class="notice" aria-live="polite">
        <h3 tabindex="-1">Order #{{ r.orderId }} · {{ r.status }}</h3>
        <p>{{ r.message }}</p>
        @if (r.status === 'ACCEPTED' || r.status === 'EXECUTING') {
          <p>Your order has been received. It has not filled yet.</p>
        }
        <a [routerLink]="['/app/orders', r.orderId]">View order, timeline and outcome →</a>
      </section>
    }
    <p id="ticket-message" role="status" aria-live="polite">{{ message() }}</p>
  </section>`,
})
export class OrderTicketComponent implements OnChanges {
  readonly instrumentId = input.required<number>();
  readonly ticker = input.required<string>();
  readonly bankRequired = input(false);
  readonly initialSide = input<string>('BUY');
  private readonly api = inject(WorkspaceApi);
  private readonly element = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly auth = inject(AuthService);
  private readonly storage = inject(PendingOrderService);
  private readonly router = inject(Router);
  side: 'BUY' | 'SELL' = 'BUY';
  quantity = '';
  readonly preview = signal<OrderPreview | null>(null);
  readonly pending = signal<PendingOrder | null>(null);
  readonly receipt = signal<OrderReceipt | null>(null);
  readonly busy = signal(false);
  readonly message = signal('');
  readonly expired = signal(false);
  private request?: Subscription;
  private expiry?: ReturnType<typeof setTimeout>;
  private previewCommand?: OrderCommand;

  constructor() {
    const session = this.auth.sessionChanged.subscribe(() => {
      this.request?.unsubscribe();
      clearTimeout(this.expiry);
      this.preview.set(null);
      this.pending.set(null);
      this.receipt.set(null);
      this.quantity = '';
      this.message.set('');
      this.busy.set(false);
    });
    inject(DestroyRef).onDestroy(() => {
      this.request?.unsubscribe();
      clearTimeout(this.expiry);
      session.unsubscribe();
    });
  }

  ngOnChanges() {
    this.request?.unsubscribe();
    this.invalidate();
    this.receipt.set(null);
    this.side = this.initialSide() === 'SELL' ? 'SELL' : 'BUY';
    const draft = this.storage.draft();
    if (draft?.instrumentId === this.instrumentId()) {
      this.side = draft.side;
      this.quantity = draft.quantity;
      this.storage.clearDraft();
    }
    this.pending.set(this.storage.read());
    if (this.pending()) this.recover();
  }

  saveDraft() {
    this.storage.draft(this.command());
  }

  hasUnits(value: string) {
    return typeof value === 'string' && /[1-9]/.test(value);
  }

  sellAll(value: string) {
    this.side = 'SELL';
    this.quantity = value;
    this.invalidate();
  }

  validQuantity() {
    return /^[0-9]{1,16}(\.[0-9]{1,12})?$/.test(this.quantity) && /[1-9]/.test(this.quantity);
  }

  invalidate() {
    this.request?.unsubscribe();
    this.busy.set(false);
    this.preview.set(null);
    this.previewCommand = undefined;
    this.expired.set(false);
    this.message.set('');
    clearTimeout(this.expiry);
  }

  private command(): OrderCommand {
    return {
      instrumentId: this.instrumentId(),
      side: this.side,
      quantity: this.quantity,
      orderType: 'MARKET',
    };
  }

  review() {
    if (this.busy() || this.pending() || !this.validQuantity()) return;
    this.busy.set(true);
    this.message.set('');
    const command = this.command();
    this.request = this.api
      .preview(command)
      .pipe(takeUntil(this.auth.sessionChanged))
      .subscribe({
        next: (p) => {
          this.busy.set(false);
          this.preview.set(p);
          if (typeof requestAnimationFrame !== 'undefined')
            requestAnimationFrame(() =>
              this.element.nativeElement.querySelector<HTMLElement>('#review-heading')?.focus(),
            );
          this.previewCommand = command;
          const remaining = Date.parse(p.expiresAt) - Date.now();
          this.expired.set(!(remaining > 0));
          clearTimeout(this.expiry);
          this.expiry = setTimeout(() => this.expired.set(true), Math.max(0, remaining));
        },
        error: (e) => {
          this.busy.set(false);
          this.message.set(e.error?.message ?? 'Unable to fetch an estimate. Please try again.');
        },
      });
  }

  confirm() {
    if (
      this.busy() ||
      this.pending() ||
      !this.preview()?.eligible ||
      this.expired() ||
      !this.previewCommand ||
      JSON.stringify(this.command()) !== JSON.stringify(this.previewCommand)
    )
      return;
    if (Date.parse(this.preview()!.expiresAt) <= Date.now()) {
      this.expired.set(true);
      return;
    }
    const key = createIdempotencyKey();
    if (!key) {
      this.message.set('Unable to create a secure order reference. Please reload and try again.');
      return;
    }
    const pending = { key, command: { ...this.previewCommand } };
    if (!this.storage.save(pending)) {
      this.message.set(
        'Enable session storage to preserve your order reference before submitting.',
      );
      return;
    }
    this.pending.set(pending);
    this.send();
  }

  send() {
    const pending = this.pending();
    if (!pending || this.busy()) return;
    this.busy.set(true);
    this.message.set('Submitting your saved instruction…');
    this.request = this.api
      .submit(pending.command, pending.key)
      .pipe(timeout(15000), takeUntil(this.auth.sessionChanged))
      .subscribe({
        next: (r) => this.resolved(r),
        error: (e) => {
          this.busy.set(false);
          if (e.status === 422 && e.error?.orderId) this.resolved(e.error);
          else {
            this.message.set(
              'Checking whether your order was received. Your saved request reference will be reused.',
            );
            this.recover();
          }
        },
      });
  }

  recover() {
    const pending = this.pending();
    if (!pending || this.busy()) return;
    this.busy.set(true);
    this.request = this.api
      .byRequest(pending.key)
      .pipe(timeout(15000), takeUntil(this.auth.sessionChanged))
      .subscribe({
        next: (r) => this.resolved(r),
        error: () => {
          this.busy.set(false);
          this.message.set(
            'Outcome is still unknown. Check again or retry the same instruction; a new order is blocked until this is resolved.',
          );
        },
      });
  }

  private resolved(receipt: OrderReceipt) {
    this.busy.set(false);
    this.receipt.set(receipt);
    this.pending.set(null);
    this.storage.clear();
    this.message.set('');
    if (receipt.acceptedAt) void this.router.navigate(['/app/orders', receipt.orderId]);
  }
}

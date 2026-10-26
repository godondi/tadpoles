import { TwoDecimalsPipe } from '../../shared/two-decimals.pipe';
import {
  Component,
  DestroyRef,
  Input,
  OnChanges,
  PLATFORM_ID,
  inject,
  signal,
} from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Subscription, takeUntil } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AuthService } from '../../core/auth/auth.service';
import { buildApiUrl } from '../../core/api/api-url';
interface DisplayEstimate {
  currency: string;
  usd: string;
  estimatedValue: string;
  targetPerUsd: string;
  rateDate: string;
  source: string;
}
@Component({
  selector: 'app-display-currency',
  imports: [TwoDecimalsPipe],
  template: `<section class="panel" aria-labelledby="display-currency-heading">
    <h2 id="display-currency-heading">Available cash in another currency</h2>
    <div class="filters">
      <label for="display-currency"
        >Display currency
        <select
          id="display-currency"
          [value]="currency"
          (change)="selectCurrency($any($event.target).value)"
        >
          <option value="USD">USD — US dollar</option>
          <option value="GBP">GBP — British pound estimate</option>
          <option value="INR">INR — Indian rupee estimate</option>
          <option value="EUR">EUR — Euro estimate</option>
        </select>
      </label>
    </div>
    <p>
      Available cash: <strong>{{ availableUsd | twoDecimals }} USD</strong>
    </p>
    <div aria-live="polite" [attr.aria-busy]="loading()">
      @if (loading()) {
        <p>Checking reference rates…</p>
      }
      @if (estimate(); as value) {
        <p>
          Estimated equivalent: <strong>{{ value.estimatedValue | twoDecimals }} {{ value.currency }}</strong>
        </p>
        <p>Based on {{ value.usd | twoDecimals }} USD at the time of this check.</p>
        <p class="as-of">
          {{ value.source }} · rate date {{ value.rateDate }} · 1 USD = {{ value.targetPerUsd | twoDecimals }}
          {{ value.currency }}
        </p>
      }
      @if (error()) {
        <p role="alert">
          Estimate unavailable. Reference rates could not be verified. Your USD balance is
          unchanged.
        </p>
        <button type="button" (click)="refresh()">Retry estimate</button>
      }
    </div>
    <p class="muted">
      Informational cash estimate only. This does not convert funds or value positions. Trading,
      deposits and withdrawals remain in USD.
    </p>
  </section>`,
})
export class DisplayCurrencyComponent implements OnChanges {
  @Input({ required: true }) availableUsd = '';
  private readonly http = inject(HttpClient);
  private readonly platform = inject(PLATFORM_ID);
  private readonly auth = inject(AuthService);
  private request?: Subscription;
  currency = 'USD';
  readonly estimate = signal<DisplayEstimate | null>(null);
  readonly loading = signal(false);
  readonly error = signal(false);

  constructor() {
    this.auth.sessionChanged.pipe(takeUntilDestroyed()).subscribe(() => {
      this.request?.unsubscribe();
      this.currency = 'USD';
      this.estimate.set(null);
      this.loading.set(false);
      this.error.set(false);
    });
    inject(DestroyRef).onDestroy(() => this.request?.unsubscribe());
  }

  ngOnChanges() {
    this.refresh();
  }

  selectCurrency(currency: string) {
    this.currency = currency;
    this.refresh();
  }

  refresh() {
    this.request?.unsubscribe();
    this.estimate.set(null);
    this.error.set(false);
    this.loading.set(false);
    if (this.currency === 'USD') return;
    this.loading.set(true);
    this.request = this.http
      .get<DisplayEstimate>(buildApiUrl('/api/workspace/client/display-currency', this.platform), {
        params: { currency: this.currency },
      })
      .pipe(takeUntil(this.auth.sessionChanged))
      .subscribe({
        next: (value) => {
          this.estimate.set(value);
          this.loading.set(false);
        },
        error: () => {
          this.error.set(true);
          this.loading.set(false);
        },
      });
  }
}

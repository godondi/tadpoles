import { PolicyHistoryComponent } from './policy-history.component';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription } from 'rxjs';
import {
  InstrumentPolicyApi,
  PolicyForm,
  PolicyRow,
  SymbolCheck,
} from '../../core/api/instrument-policy-api.service';

@Component({
  selector: 'app-instrument-policy-editor',
  imports: [FormsModule, DatePipe, RouterLink, PolicyHistoryComponent],
  template: `
    <header class="workspace-heading">
      <p class="eyebrow">TRADING OPERATIONS</p>
      <h1 tabindex="-1">{{ id ? 'Edit instrument' : 'Add instrument' }}</h1>
      <p>Validate the provider identity, set order limits and choose how clients can trade.</p>
      <a routerLink="/operations/instruments">Back to instruments</a>
    </header>
    @if (loading()) {
      <p role="status">Loading instrument…</p>
    }
    @if (loadError()) {
      <p role="alert">{{ loadError() }}</p>
      <button type="button" (click)="load()">Retry</button>
    }
    @if (ready()) {
      <section class="panel">
        <form #policyForm="ngForm" (ngSubmit)="save(policyForm)" class="workspace-form instrument-editor">
          <fieldset [disabled]="saving()">
            <legend>Identity and provider validation</legend>
            <div class="form-field">
              <label for="instrument-symbol">Enter ticker/instrument</label>
              <p class="field-hint" id="instrument-symbol-hint">
                Use the provider ticker for the instrument, for example SPY.
              </p>
              <div class="field-action">
                <input
                  id="instrument-symbol"
                  placeholder="SPY"
                  aria-describedby="instrument-symbol-hint"
                  name="providerSymbol"
                  required
                  [(ngModel)]="form.providerSymbol"
                  (ngModelChange)="invalidate()"
                  [readOnly]="!!id && identityLocked"
                  maxlength="30"
                />
                <button
                  type="button"
                  data-test-symbol
                  (click)="test()"
                  [disabled]="testing() || !form.providerSymbol"
                >
                  {{ testing() ? 'Testing…' : 'Test with Fauxnance' }}
                </button>
              </div>
              @if (testing()) {
                <p role="status">Checking the symbol quote and daily chart…</p>
              }
              @if (testError()) {
                <p class="notice" role="alert" data-symbol-error>{{ testError() }}</p>
              }
            </div>
            @if (check(); as c) {
              <section class="notice">
                <h3>Recognized: {{ c.symbol }}</h3>
                <p>{{ c.currency }} · {{ c.source }} · {{ c.asOf | date: 'medium' : 'UTC' }} UTC</p>
                <p>
                  Quote currently usable: {{ c.quoteUsable ? 'Yes' : 'No' }} · Platform eligible:
                  {{ c.eligible ? 'Yes' : 'No' }}
                </p>
                <p>{{ c.reason }}</p>
                <p>
                  Daily chart available: {{ c.chartAvailable ? 'Yes' : 'Not confirmed' }} ·
                  {{ c.chartReason }}
                </p>
                <p>{{ c.disclaimer }}</p>
              </section>
            }
            <div class="form-field">
              <label for="policy-ticker">Catalog ticker</label>
              <p class="field-hint" id="policy-ticker-hint">
                The symbol clients will see in the catalog, for example SPY.
              </p>
              <input
                id="policy-ticker"
                placeholder="SPY"
                aria-describedby="policy-ticker-hint"
                name="ticker"
                [(ngModel)]="form.ticker"
                [readOnly]="!!id"
                (ngModelChange)="invalidate()"
                required
                maxlength="30"
              />
            </div>
            <div class="form-field">
              <label for="policy-name">Name</label>
              <p class="field-hint" id="policy-name-hint">
                Use the instrument’s full display name.
              </p>
              <input
                id="policy-name"
                placeholder="SPDR S&P 500 ETF Trust"
                aria-describedby="policy-name-hint"
                name="name"
                [(ngModel)]="form.name"
                required
                maxlength="120"
              />
            </div>
            <div class="form-field">
              <label for="policy-venue">Venue label</label>
              <p class="field-hint" id="policy-venue-hint">
                The exchange or listing venue, for example NYSE Arca. This is a label, not automatic
                listing verification.
              </p>
              <select
                id="policy-venue"
                aria-describedby="policy-venue-hint"
                name="venue"
                [(ngModel)]="form.venue"
                required
              >
                <option value="" disabled>Choose an exchange</option>
                @if (form.venue && !['NYSE','Nasdaq','Nasdaq Global Market','Nasdaq Global Select Market','NYSE Arca','Cboe BZX','OTC'].includes(form.venue)) {
                  <option [value]="form.venue">{{ form.venue }} (current)</option>
                }
                <option value="NYSE">NYSE</option>
                <option value="Nasdaq">Nasdaq</option>
                <option value="Nasdaq Global Market">Nasdaq Global Market</option>
                <option value="Nasdaq Global Select Market">Nasdaq Global Select Market</option>
                <option value="NYSE Arca">NYSE Arca</option>
                <option value="Cboe BZX">Cboe BZX</option>
                <option value="OTC">OTC</option>
              </select>
            </div>
            <p>
              Venue is an Operations label; a quote response alone does not verify listing identity.
              Review the provider mapping before enabling.
            </p>
          </fieldset>
          <fieldset [disabled]="saving()">
            <legend>Trading limits</legend>
            <div class="form-field">
              <label for="policy-step">Quantity step (shares)</label>
              <p class="field-hint" id="policy-step-hint">
                Allowed share increment. Use 1 for whole shares or 0.5 for half-share increments;
                every order must be a multiple of this value.
              </p>
              <input
                id="policy-step"
                placeholder="1"
                aria-describedby="policy-step-hint"
                name="step"
                required
                [(ngModel)]="form.quantityStep"
                inputmode="decimal"
                type="text"
              />
            </div>
            <div class="form-field">
              <label for="policy-max-quantity">Maximum quantity</label>
              <p class="field-hint" id="policy-max-quantity-hint">
                Maximum shares allowed in one order. It must be at least one quantity step, up to
                1,000,000.
              </p>
              <input
                id="policy-max-quantity"
                placeholder="1000"
                aria-describedby="policy-max-quantity-hint"
                name="maxQuantity"
                required
                [(ngModel)]="form.maxQuantity"
                inputmode="decimal"
                type="text"
              />
            </div>
            <div class="form-field">
              <label for="policy-max-notional">Maximum consideration (USD)</label>
              <p class="field-hint" id="policy-max-notional-hint">
                Maximum order value in USD (shares × price), up to 1,000,000.00. Checked again at
                execution; this is not an account balance.
              </p>
              <input
                id="policy-max-notional"
                placeholder="100000"
                aria-describedby="policy-max-notional-hint"
                name="maxNotional"
                required
                [(ngModel)]="form.maxNotional"
                inputmode="decimal"
                type="text"
              />
            </div>
            <div class="form-field">
              <label for="policy-age">Maximum quote age (seconds, 1–1200)</label>
              <p class="field-hint" id="policy-age-hint">
                Oldest accepted provider quote in seconds. 60 means one minute; 1200 means twenty
                minutes. Explicitly stale quotes remain blocked.
              </p>
              <select
                id="policy-age"
                aria-describedby="policy-age-hint"
                name="age"
                required
                [(ngModel)]="form.quoteMaxAgeSeconds"
              >
                @if (![60, 300, 600, 1200].includes(form.quoteMaxAgeSeconds)) {
                  <option [ngValue]="form.quoteMaxAgeSeconds">{{ form.quoteMaxAgeSeconds }} seconds (current)</option>
                }
                <option [ngValue]="60">1 minute</option>
                <option [ngValue]="300">5 minutes</option>
                <option [ngValue]="600">10 minutes</option>
                <option [ngValue]="1200">20 minutes</option>
              </select>
            </div>
            <p>
              Session policy: provider open, or a verified U.S. core session when provider status is
              unknown. Settlement: immediate internal booking. Project fee: zero.
            </p>
          </fieldset>
          <fieldset [disabled]="saving()">
            <legend>Trading permissions</legend>
            <label class="checkbox-field">
              <input name="active" type="checkbox" [(ngModel)]="form.active" />
              Active
            </label>
            <label class="checkbox-field">
              <input name="buyAllowed" type="checkbox" [(ngModel)]="form.buyAllowed" />
              Allow buys
            </label>
            <label class="checkbox-field">
              <input name="sellAllowed" type="checkbox" [(ngModel)]="form.sellAllowed" />
              Allow sells
            </label>
            @if (!form.active || !form.sellAllowed) {
              <p class="notice">
                This blocks exits from held positions and may reject accepted orders. Use active +
                allow sells + disable buys for a sell-only exit policy.
              </p>
            }
            <div class="form-field">
              <label for="policy-reason">Reason for change (8–500 characters)</label>
              <p class="field-hint" id="policy-reason-hint">
                Explain why this policy is changing. Enter 8–500 characters for the audit record.
              </p>
              <textarea
                id="policy-reason"
                placeholder="Enable the verified USD listing for client trading."
                aria-describedby="policy-reason-hint"
                name="reason"
                [(ngModel)]="form.reason"
                minlength="8"
                maxlength="500"
                required
              ></textarea>
            </div>
            <p>
              Saving rechecks the current provider values. Full halts remain available during
              provider outages. Deactivation preserves instruments and historical records.
            </p>
            <div class="actions">
              <button type="submit">
                {{ saving() ? 'Saving…' : !form.active ? 'Save full halt' : 'Save policy' }}
              </button>
              <a routerLink="/operations/instruments">Back to instruments</a>
            </div>
            @if (message()) {
              <p [attr.role]="failed() ? 'alert' : 'status'">{{ message() }}</p>
            }
            @if (savedTicker()) {
              <a routerLink="/operations/instruments" [queryParams]="{ search: savedTicker() }">
                View saved instrument in catalog
              </a>
            }
          </fieldset>
        </form>
      </section>
    }
    @if (id && ready()) {
      <app-policy-history [instrumentId]="id" />
    }
  `,
})
export class InstrumentPolicyEditorComponent {
  private readonly api = inject(InstrumentPolicyApi);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroy = inject(DestroyRef);
  private validation?: Subscription;
  private request?: Subscription;
  private formVersion = 0;
  readonly check = signal<SymbolCheck | null>(null);
  readonly message = signal('');
  readonly failed = signal(false);
  readonly savedTicker = signal('');
  readonly testing = signal(false);
  readonly testError = signal('');
  readonly saving = signal(false);
  readonly loading = signal(false);
  readonly loadError = signal('');
  readonly ready = signal(false);
  id?: number;
  identityLocked = false;
  form: PolicyForm = this.empty();

  constructor() {
    this.route.paramMap.pipe(takeUntilDestroyed()).subscribe((params) => {
      const value = params.get('id');
      if (value !== 'new' && Number(value) === this.id && this.ready()) return;
      this.formVersion++;
      this.saving.set(false);
      if (value === 'new') {
        this.request?.unsubscribe();
        this.invalidate();
        this.id = undefined;
        this.identityLocked = false;
        this.form = this.empty();
        this.message.set('');
        this.savedTicker.set('');
        this.loadError.set('');
        this.loading.set(false);
        this.ready.set(true);
      } else if (Number(value) !== this.id || !this.ready()) {
        this.id = Number(value);
        this.load();
      }
    });
    this.destroy.onDestroy(() => {
      this.validation?.unsubscribe();
      this.request?.unsubscribe();
    });
  }

  private empty(): PolicyForm {
    return {
      ticker: '',
      name: '',
      providerSymbol: '',
      venue: '',
      quantityStep: '1',
      maxQuantity: '1000',
      maxNotional: '100000',
      quoteMaxAgeSeconds: 1200,
      active: true,
      buyAllowed: true,
      sellAllowed: true,
      reason: '',
      revision: 0,
    };
  }

  load() {
    if (!this.id) return;
    this.request?.unsubscribe();
    this.invalidate();
    this.ready.set(false);
    this.loading.set(true);
    this.loadError.set('');
    this.message.set('');
    this.savedTicker.set('');
    this.request = this.api.get(this.id).subscribe({
      next: (row) => {
        this.apply(row);
        this.ready.set(true);
        this.loading.set(false);
      },
      error: (e) => {
        this.loadError.set(e.error?.message ?? 'Unable to load this instrument.');
        this.loading.set(false);
      },
    });
  }

  invalidate() {
    this.validation?.unsubscribe();
    this.check.set(null);
    this.testing.set(false);
    this.testError.set('');
  }

  test() {
    this.invalidate();
    this.testing.set(true);
    this.message.set('');
    this.validation = this.api.test(this.form.providerSymbol.trim().toUpperCase()).subscribe({
      next: (check) => {
        this.check.set(check);
        this.testing.set(false);
      },
      error: (error) => {
        this.testing.set(false);
        this.testError.set(
          error.error?.message ??
            'Unable to test this symbol. Check the provider connection and try again.',
        );
      },
    });
  }

  private apply(row: PolicyRow) {
    this.invalidate();
    this.id = row.instrumentId;
    this.identityLocked = !!row.providerSymbol;
    this.form = {
      ...this.empty(),
      ticker: row.ticker,
      name: row.name,
      active: row.active,
      revision: row.revision,
      providerSymbol: row.providerSymbol ?? row.ticker,
      venue: row.venue ?? '',
      quantityStep: row.quantityStep ?? '1',
      maxQuantity: row.maxQuantity ?? '1000',
      maxNotional: row.maxNotional ?? '100000',
      quoteMaxAgeSeconds: row.quoteMaxAgeSeconds ?? 1200,
      buyAllowed: row.buyAllowed ?? true,
      sellAllowed: row.sellAllowed ?? true,
      reason: '',
    };
  }

  save(form: NgForm) {
    if (this.saving()) return;
    if (form.invalid) {
      form.form.markAllAsTouched();
      this.error({
        error: {
          message: 'Complete the required fields and enter a change reason of 8–500 characters.',
        },
      });
      return;
    }
    const version = this.formVersion;
    this.saving.set(true);
    this.message.set('');
    this.savedTicker.set('');
    this.api
      .save({ ...this.form }, this.id)
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe({
        next: (row) => {
          if (version !== this.formVersion) return;
          this.saving.set(false);
          this.apply(row);
          this.failed.set(false);
          this.savedTicker.set(row.ticker);
          this.message.set(
            `${row.ticker} saved. Revision ${row.revision}. ${row.active ? 'Available in the client catalog.' : 'Inactive; hidden from the client catalog.'}`,
          );
          // The same route/component stays mounted, preserving the operator's scroll position.
          void this.router.navigate(['/operations/instruments', row.instrumentId], {
            replaceUrl: true,
          });
        },
        error: (error) => {
          if (version !== this.formVersion) return;
          this.saving.set(false);
          this.error(error);
        },
      });
  }

  private error(error: { error?: { message?: string } }) {
    this.failed.set(true);
    this.message.set(
      error.error?.message ??
        'Unable to save. No success is assumed; review the fields and try again.',
    );
  }
}

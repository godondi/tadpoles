import { Component, DestroyRef, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription } from 'rxjs';
import { InstrumentPolicyApi, PolicyRow } from '../../core/api/instrument-policy-api.service';
import { CatalogRefreshService } from '../../core/api/catalog-refresh.service';

@Component({
  selector: 'app-operations-instruments',
  imports: [FormsModule, DatePipe, RouterLink],
  template: `
    <header class="workspace-heading">
      <p class="eyebrow">TRADING OPERATIONS</p>
      <h1 tabindex="-1">Instruments</h1>
      <p>
        Manage validated USD equities. Changes affect new decisions; accepted instructions retain
        their policy snapshots. Current halts apply before booking.
      </p>
    </header>
    <p role="status">Catalog changes appear automatically. Your filters stay selected.</p>
    @if (message()) {
      <p role="alert">{{ message() }}</p>
    }
    <section class="panel">
      <form (ngSubmit)="load(true)" class="filters">
        <div class="form-field">
          <label for="policy-search">Search instruments</label>
          <input id="policy-search" name="search" [(ngModel)]="search" maxlength="80" />
        </div>
        <button type="submit">Search</button>
        <button type="button" (click)="load()" [disabled]="loading()">Refresh catalog</button>
        <a class="button-link" routerLink="/operations/instruments/new">Add instrument</a>
      </form>
      <div class="table-wrapper" tabindex="0" aria-label="Instrument policies, scroll horizontally">
        <table>
          <caption>Catalog and validation state</caption>
          <thead>
            <tr>
              <th>Symbol</th>
              <th>Policy</th>
              <th>Last checked (UTC)</th>
              <th>Action</th>
            </tr>
          </thead>
          <tbody>
            @for (row of rows(); track row.instrumentId) {
              <tr>
                <td>
                  {{ row.ticker }}
                  <small>{{ row.name }} · {{ row.currency }}</small>
                </td>
                <td>
                  {{
                    !row.active
                      ? 'Full halt'
                      : row.buyAllowed && row.sellAllowed
                        ? 'Buy and sell'
                        : row.sellAllowed
                          ? 'Sell only'
                          : row.buyAllowed
                            ? 'Buy only'
                            : 'No trading'
                  }}
                  · revision {{ row.revision }}
                </td>
                <td>
                  {{
                    row.validatedAt ? (row.validatedAt | date: 'medium' : 'UTC') : 'Not validated'
                  }}
                </td>
                <td>
                  <a [routerLink]="['/operations/instruments', row.instrumentId]">
                    Edit / deactivate
                  </a>
                </td>
              </tr>
            } @empty {
              <tr>
                <td colspan="4">
                  {{
                    loading()
                      ? 'Loading instruments…'
                      : 'No matching instruments. Try clearing your search.'
                  }}
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
      <div class="actions">
        <button type="button" [disabled]="page === 0" (click)="page = page - 1; load()">
          Previous
        </button>
        <span>Page {{ page + 1 }}</span>
        <button type="button" [disabled]="rows().length < 100" (click)="page = page + 1; load()">
          Next
        </button>
      </div>
    </section>
  `,
})
export class OperationsInstrumentsComponent {
  private readonly api = inject(InstrumentPolicyApi);
  private readonly route = inject(ActivatedRoute);
  private readonly destroy = inject(DestroyRef);
  private listing?: Subscription;
  readonly rows = signal<PolicyRow[]>([]);
  readonly message = signal('');
  readonly loading = signal(false);
  search = '';
  page = 0;
  private appliedSearch = '';

  constructor() {
    this.route.queryParamMap.pipe(takeUntilDestroyed()).subscribe((params) => {
      this.search = params.get('search') ?? '';
      this.appliedSearch = this.search;
      this.page = Math.max(0, Number(params.get('page')) || 0);
      this.load();
    });
    inject(CatalogRefreshService)
      .watch()
      .pipe(takeUntilDestroyed())
      .subscribe(() => {
        if (!this.loading()) this.load();
      });
    this.destroy.onDestroy(() => this.listing?.unsubscribe());
  }

  load(reset = false) {
    if (reset) {
      this.page = 0;
      this.appliedSearch = this.search;
    }
    this.listing?.unsubscribe();
    this.loading.set(true);
    this.message.set('');
    this.listing = this.api.list(this.appliedSearch, this.page).subscribe({
      next: (rows) => {
        this.rows.set(rows);
        this.loading.set(false);
      },
      error: (error) => {
        this.message.set(
          error.error?.message ??
            'Unable to refresh the catalog. Previously loaded instruments remain visible.',
        );
        this.loading.set(false);
      },
    });
  }
}

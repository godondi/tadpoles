import { Component, computed, inject, signal } from '@angular/core';
import { forkJoin } from 'rxjs';
import { CurrencyPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { OperationsUser, OperationsUsersApi } from '../../core/api/operations-users-api.service';

@Component({
  selector: 'app-operations-user-access',
  imports: [FormsModule, CurrencyPipe],
  template: `
    <header class="workspace-heading">
      <p class="eyebrow">TRADING OPERATIONS</p>
      <h1 tabindex="-1">User access</h1>
      <p>Search client accounts and control access to trading.</p>
    </header>
    @if (message()) {
      <p class="notice" role="status">{{ message() }}</p>
    }
    @if (error()) {
      <p class="notice error" role="alert">
        {{ error() }}
        @if (retryableError()) {
          <button type="button" (click)="load()">Retry</button>
        }
      </p>
    }
    <form class="market-search panel" (ngSubmit)="load()" role="search">
      <label class="market-query"
        >Search clients
        <input name="search" [(ngModel)]="search" placeholder="Username, name, or email" />
      </label>
      <label
        >Trade access
        <select name="filter" [ngModel]="filter()" (ngModelChange)="filter.set($event)">
          <option value="ALL">All users</option>
          <option value="REVOKED">Revoked</option>
          <option value="ACTIVE">Allowed</option>
        </select>
      </label>
      <button type="submit" [disabled]="loading()">
        {{ loading() ? 'Searching…' : 'Search' }}
      </button>
      <button class="market-clear" type="button" (click)="search = ''; filter.set('ALL'); load()">
        Clear
      </button>
    </form>
    <section class="panel access-panel" aria-labelledby="access-table-title">
      <header class="panel-heading">
        <div>
          <p class="eyebrow">CLIENT ACCOUNTS</p>
          <h2 id="access-table-title">Trade access</h2>
        </div>
        <span>{{ filtered().length }} users</span>
      </header>
      <section class="bulk-controls" aria-label="Selected client actions">
        <div class="bulk-heading">
          <strong>{{ selected().size }} selected</strong>
          <span>Choose a duration, select clients, then revoke or restore access.</span>
        </div>
        <div class="bulk-fields">
          <label
            ><span>Revoke duration</span><select name="duration" [(ngModel)]="duration">
              <option value="INDEFINITE">Indefinite</option>
              <option value="TIMED">Timed</option>
            </select></label
          >
          @if (duration === 'TIMED') {
            <label
              ><span>Amount</span><input
                aria-label="Duration amount"
                type="number"
                min="1"
                [max]="maximum"
                [(ngModel)]="amount"
                name="amount"
            /></label>
            <label
              ><span>Unit</span><select aria-label="Duration unit" [(ngModel)]="unit" name="unit">
                <option>HOURS</option>
                <option>DAYS</option>
                <option>MONTHS</option>
              </select></label
            >
          }
          <label class="reason-field"
            ><span>Reason (optional)</span><input
              [(ngModel)]="reason"
              name="reason"
              placeholder="Add a short reason"
          /></label>
        </div>
        <div class="bulk-actions">
          <button type="button" (click)="revokeSelected()" [disabled]="saving() || !selected().size">
            {{ saving() ? 'Saving…' : 'Revoke selected' }}</button
          ><button
            class="secondary"
            type="button"
            (click)="restoreSelected()"
            [disabled]="saving() || !selected().size"
          >
            Restore selected
          </button>
        </div>
      </section>
      <div class="table-wrapper" tabindex="0" aria-label="Client access list, scroll horizontally">
        <table>
          <caption>
            Clients sorted by username
          </caption>
          <thead>
            <tr>
              <th>
                <input
                  aria-label="Select all visible users"
                  type="checkbox"
                  [checked]="allSelected()"
                  (change)="toggleAll($event)"
                />
              </th>
              <th>Username</th>
              <th>Name</th>
              <th>ID</th>
              <th>Balance</th>
              <th>Access</th>
            </tr>
          </thead>
          <tbody>
            @for (row of filtered(); track row.id) {
              <tr>
                <td>
                  <input
                    [attr.aria-label]="'Select ' + row.username"
                    type="checkbox"
                    [disabled]="row.client_id === null"
                    [checked]="row.client_id !== null && selected().has(row.client_id)"
                    (change)="row.client_id !== null && toggle(row.client_id, $event)"
                  />
                </td>
                <th scope="row">{{ row.username }}</th>
                <td>{{ row.name }}</td>
                <td>{{ row.id }}</td>
                <td>{{ row.balance | currency: 'USD' }}</td>
                <td>{{ accessLabel(row) }}</td>
              </tr>
            } @empty {
              <tr>
                <td colspan="6">
                  {{
                    loading()
                      ? 'Loading client accounts…'
                      : 'No client accounts match these filters.'
                  }}
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    </section>
  `,
  styles: [
    `
      .market-search {
        display: grid;
        grid-template-columns: minmax(220px, 2fr) minmax(170px, 1fr) auto auto;
        align-items: end;
        gap: 14px;
        margin: 0 0 20px;
      }
      .market-search label,
      .bulk-fields label {
        display: grid;
        grid-template-rows: minmax(1.25em, auto) 44px;
        align-content: start;
        gap: 7px;
        color: var(--text-secondary);
        font-size: 0.84rem;
        font-weight: 700;
      }
      .market-search input,
      .market-search select,
      .bulk-fields input,
      .bulk-fields select {
        min-width: 0;
        width: 100%;
        min-height: 44px;
        padding: 10px 12px;
        border: 1px solid var(--border-gray);
        background: var(--surface);
        color: var(--text-primary);
        font: inherit;
      }
      .market-search button,
      .bulk-actions button,
      .notice button {
        min-height: 44px;
        padding: 10px 16px;
        border: 1px solid var(--dark-green);
        background: var(--action-bg);
        color: var(--action-text);
        font: inherit;
        font-weight: 700;
        cursor: pointer;
      }
      .market-search .market-clear,
      .secondary {
        background: var(--surface) !important;
        color: var(--dark-green) !important;
      }
      .access-panel {
        padding: 20px;
      }
      .panel-heading {
        display: flex;
        justify-content: space-between;
        align-items: center;
        border-bottom: 1px solid var(--border-gray);
        padding-bottom: 12px;
        margin-bottom: 14px;
      }
      .panel-heading h2 {
        margin: 4px 0;
      }
      .panel-heading span {
        color: var(--text-secondary);
      }
      .eyebrow {
        margin: 0;
      }
      .table-wrapper {
        overflow: auto;
      }
      .table-wrapper table {
        width: 100%;
        border-collapse: collapse;
      }
      .table-wrapper th,
      .table-wrapper td {
        text-align: left;
        padding: 12px;
        border-bottom: 1px solid var(--border-gray);
      }
      .table-wrapper thead th {
        color: var(--text-secondary);
        font-size: 0.82rem;
      }
      .table-wrapper tbody th {
        font-weight: 700;
      }
      .bulk-controls {
        display: grid;
        gap: 14px;
        margin-bottom: 16px;
        padding: 16px;
        background: var(--surface-muted);
      }
      .bulk-heading {
        display: flex;
        align-items: baseline;
        gap: 12px;
      }
      .bulk-heading span {
        font-weight: 400;
        color: var(--text-secondary);
      }
      .bulk-fields {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(min(220px, 100%), 260px));
        gap: 12px;
      }
      .bulk-fields > label > span {
        min-height: 1.25em;
      }
      .bulk-actions {
        display: flex;
        gap: 10px;
        flex-wrap: wrap;
      }
      .notice {
        padding: 12px 16px;
        background: var(--surface-muted);
        color: var(--text-primary);
      }
      .notice.error {
        background: var(--error-bg);
        color: var(--error-text);
      }
      @media (max-width: 800px) {
        .market-search {
          grid-template-columns: 1fr 1fr;
        }
        .market-query {
          grid-column: 1/-1;
        }
        .bulk-fields {
          grid-template-columns: 1fr 1fr;
        }
        .reason-field {
          grid-column: 1/-1;
        }
      }
      @media (max-width: 560px) {
        .market-search,
        .bulk-fields {
          grid-template-columns: 1fr;
        }
        .market-query,
        .reason-field {
          grid-column: auto;
        }
        .access-panel {
          padding: 14px;
        }
        .bulk-heading {
          align-items: flex-start;
          flex-direction: column;
          gap: 4px;
        }
      }
    `,
  ],
})
export class OperationsUserAccessComponent {
  private readonly api = inject(OperationsUsersApi);
  readonly rows = signal<OperationsUser[]>([]);
  readonly selected = signal(new Set<number>());
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly message = signal('');
  readonly error = signal('');
  readonly retryableError = signal(false);
  readonly filter = signal<'ALL' | 'REVOKED' | 'ACTIVE'>('ALL');
  readonly filtered = computed(() =>
    this.rows().filter(
      (row) => this.filter() === 'ALL' || (this.filter() === 'REVOKED') === row.revoked,
    ),
  );
  readonly allSelected = computed(
    () =>
      !!this.filtered().filter((row) => row.client_id !== null).length &&
      this.filtered()
        .filter((row) => row.client_id !== null)
        .every((row) => this.selected().has(row.client_id!)),
  );
  get maximum(): number {
    return this.unit === 'HOURS' ? 8760 : this.unit === 'DAYS' ? 365 : 12;
  }
  search = '';
  duration = 'INDEFINITE';
  amount = 1;
  unit: 'HOURS' | 'DAYS' | 'MONTHS' = 'HOURS';
  reason = '';
  constructor() {
    this.load();
  }
  load() {
    this.loading.set(true);
    this.error.set('');
    this.retryableError.set(false);
    this.api.clients(this.search.trim()).subscribe({
      next: (rows) => {
        this.rows.set(
          [...rows].sort((a, b) =>
            a.username.localeCompare(b.username, undefined, { sensitivity: 'base' }),
          ),
        );
        this.selected.set(new Set());
        this.loading.set(false);
      },
      error: (err) => {
        const detail = err?.error?.message;
        const onboardingError = this.isMissingOnboardingId(detail);
        this.error.set(
          onboardingError
            ? 'This client has not completed onboarding, so trade access cannot be managed yet.'
            : detail
              ? `Unable to load client access: ${detail}`
              : 'Unable to load client access. Check your connection and retry.',
        );
        if (!onboardingError) {
          this.retryableError.set(!err?.status || err.status >= 500);
        }
        this.loading.set(false);
      },
    });
  }
  accessLabel(row: OperationsUser) {
    if (row.client_id === null) return 'Onboarding';
    if (!row.revoked) return 'Allowed';
    return row.revoked_until
      ? `Revoked until ${new Date(row.revoked_until).toLocaleString()}`
      : 'Revoked indefinitely';
  }
  toggle(id: number, event: Event) {
    const next = new Set(this.selected());
    (event.target as HTMLInputElement).checked ? next.add(id) : next.delete(id);
    this.selected.set(next);
  }
  toggleAll(event: Event) {
    const next = new Set(this.selected());
    for (const row of this.filtered())
      if (row.client_id !== null)
        (event.target as HTMLInputElement).checked
          ? next.add(row.client_id)
          : next.delete(row.client_id);
    this.selected.set(next);
  }
  revokeSelected() {
    this.change([...this.selected()], true);
  }
  restoreSelected() {
    this.change([...this.selected()], false);
  }
  private change(ids: number[], revoke: boolean) {
    if (!ids.length) return;
    this.saving.set(true);
    this.error.set('');
    const body = {
      revoke,
      ...(revoke && this.duration === 'TIMED' ? { amount: this.amount, unit: this.unit } : {}),
      ...(revoke && this.reason.trim() ? { reason: this.reason.trim() } : {}),
    };
    forkJoin(ids.map((id) => this.api.changeAccess(id, body))).subscribe({
      next: () => {
        this.message.set(revoke ? 'Trade access revoked.' : 'Trade access restored.');
        this.saving.set(false);
        this.load();
      },
      error: (err) => {
        const detail = err?.error?.message;
        this.error.set(
          this.isMissingOnboardingId(detail)
            ? 'This client has not completed onboarding, so trade access cannot be managed yet.'
            : detail ?? 'Unable to update trade access.',
        );
        this.retryableError.set(false);
        this.saving.set(false);
      },
    });
  }
  private isMissingOnboardingId(detail: unknown): boolean {
    return typeof detail === 'string' && /For input string:\s*["']undefined["']/.test(detail);
  }
}

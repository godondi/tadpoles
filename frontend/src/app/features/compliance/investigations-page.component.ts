import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ComplianceApi } from './compliance-api.service';
import { ComplianceContextComponent } from './compliance-context.component';
import { ComplianceResource } from './compliance-resource';
import { ComplianceSearchFilters, ComplianceSearchPage } from './compliance.models';
import { RequestStateComponent } from '../../shared/workspace/request-state.component';
import {
  ComplianceSearchState,
  complianceStatuses,
  emptySearchDraft,
  restoreSearch,
  safeSearchQuery,
  searchDraft,
  validateSearch,
} from './search-state';

@Component({
  selector: 'app-compliance-investigations',
  imports: [DatePipe, FormsModule, RouterLink, RequestStateComponent, ComplianceContextComponent],
  template: `
    <header class="workspace-heading">
      <p class="eyebrow">RISK &amp; COMPLIANCE</p>
      <h1 tabindex="-1">Investigations</h1>
      <p>Find retained order evidence within your approved account scope.</p>
    </header>
    <app-compliance-context />
    @if (api.context()) {
      <section class="panel">
        <form class="filters" (ngSubmit)="search()">
          <div class="form-field">
            <label for="compliance-order-id">Order ID</label>
            <input
              id="compliance-order-id"
              name="orderId"
              [(ngModel)]="filters.orderId"
              inputmode="numeric"
              maxlength="10"
            />
          </div>
          <div class="form-field">
            <label for="compliance-from">Submitted from (UTC)</label>
            <input
              id="compliance-from"
              name="from"
              type="datetime-local"
              [(ngModel)]="filters.from"
            />
          </div>
          <div class="form-field">
            <label for="compliance-to">Submitted before (UTC)</label>
            <input id="compliance-to" name="to" type="datetime-local" [(ngModel)]="filters.to" />
          </div>
          <div class="form-field">
            <label for="compliance-client-reference">Permitted client reference</label>
            <input
              id="compliance-client-reference"
              name="clientReference"
              [(ngModel)]="filters.clientReference"
              maxlength="80"
              autocomplete="off"
            />
          </div>
          <div class="form-field">
            <label for="compliance-instrument">Instrument</label>
            <input
              id="compliance-instrument"
              name="instrument"
              [(ngModel)]="filters.instrument"
              maxlength="80"
              autocomplete="off"
            />
          </div>
          <div class="form-field">
            <label for="compliance-status">Lifecycle status</label>
            <select id="compliance-status" name="status" [(ngModel)]="filters.status">
              <option value="">All statuses</option>
              @for (status of statuses; track status) {
                <option [value]="status">
                  {{ legacyStatus(status) ? status + ' (legacy)' : status }}
                </option>
              }
            </select>
          </div>
          <button type="submit" [disabled]="resource.loading()">Apply filters</button>
          <button type="button" (click)="reset()" [disabled]="resource.loading()">Reset</button>
        </form>
        <p>
          Results are scoped before counts and paging. Date filters are UTC and may span at most 31
          days. Only status, instrument, UTC bounds and paging are restored from the URL; order and
          account filters remain in memory.
        </p>
        @if (validation()) {
          <p class="notice" role="alert">{{ validation() }}</p>
        }
        <app-request-state
          [loading]="resource.loading()"
          [error]="resource.error()"
          [status]="resource.status()"
          (retry)="loadPage(page)"
        />
        @if (resource.data(); as result) {
          <p aria-live="polite">
            {{ result.total }} permitted result{{ result.total === 1 ? '' : 's' }} · as of
            {{ result.asOf | date: 'medium' : 'UTC' }} UTC · {{ result.scope }}
          </p>
          <div
            class="table-wrapper"
            tabindex="0"
            aria-label="Compliance investigations, scroll horizontally"
          >
            <table>
              <caption>
                Permitted investigation results
              </caption>
              <thead>
                <tr>
                  <th scope="col">Order / client reference</th>
                  <th scope="col">Instruction</th>
                  <th scope="col">State / completeness</th>
                  <th scope="col">Recorded times (UTC)</th>
                  <th scope="col">Review</th>
                </tr>
              </thead>
              <tbody>
                @for (item of result.items; track item.orderId) {
                  <tr>
                    <td>
                      {{ item.orderId }}<small>{{ item.clientReference }}</small>
                    </td>
                    <td>
                      {{ item.side }} {{ item.quantity }} {{ item.unit }} · {{ item.instrument }}
                      <small>{{ item.executionMode }} · {{ item.recordKind }}</small>
                    </td>
                    <td>
                      {{ item.status }}<small>{{ item.evidenceCompleteness }}</small>
                    </td>
                    <td>
                      <small>Submitted: {{ time(item.submittedAt) }}</small>
                      <small>Accepted: {{ time(item.acceptedAt) }}</small>
                      <small>Outcome: {{ time(item.outcomeAt) }}</small>
                    </td>
                    <td>
                      <a
                        [routerLink]="['/compliance/investigations', item.orderId]"
                        [queryParams]="filterQuery()"
                      >
                        Review evidence
                      </a>
                    </td>
                  </tr>
                } @empty {
                  <tr>
                    <td colspan="5">No permitted investigations match these filters.</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
          <nav aria-label="Compliance investigation pages" class="pagination">
            <button
              type="button"
              (click)="loadPage(page - 1)"
              [disabled]="page === 0 || resource.loading()"
            >
              Previous
            </button>
            <span>Page {{ page + 1 }} of {{ pages(result) }}</span>
            <button
              type="button"
              (click)="loadPage(page + 1)"
              [disabled]="(page + 1) * result.pageSize >= result.total || resource.loading()"
            >
              Next
            </button>
          </nav>
        }
      </section>
    }
  `,
  styles: [
    `
      small {
        display: block;
        color: var(--text-secondary);
        margin-top: 0.25rem;
      }
      .filters {
        display: grid;
        gap: 1rem;
        grid-template-columns: repeat(auto-fit, minmax(12rem, 1fr));
        align-items: end;
      }
      .form-field {
        display: grid;
        gap: 0.35rem;
      }
      .pagination {
        display: flex;
        gap: 1rem;
        align-items: center;
        margin-top: 1rem;
      }
    `,
  ],
})
export class ComplianceInvestigationsPageComponent {
  readonly api = inject(ComplianceApi);
  readonly resource = new ComplianceResource<ComplianceSearchPage>();
  readonly validation = signal('');
  readonly statuses = complianceStatuses;
  readonly filters = emptySearchDraft();
  private readonly state = inject(ComplianceSearchState);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private applied: ComplianceSearchFilters = { page: 0, pageSize: 25 };
  private initialContext = !this.api.context();
  page = 0;

  constructor() {
    try {
      this.applied =
        (this.api.context() ? this.state.applied : null) ??
        restoreSearch(this.route.snapshot.queryParamMap);
      Object.assign(this.filters, searchDraft(this.applied));
      this.page = this.applied.page;
    } catch {
      this.validation.set('Saved search filters were invalid and have been reset.');
    }
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: this.filterQuery(),
      replaceUrl: true,
    });
    this.api.changed.pipe(takeUntilDestroyed()).subscribe(() => {
      if (this.initialContext) {
        if (this.api.context()) {
          this.initialContext = false;
          this.loadPage(this.applied.page);
        }
        return;
      }
      this.applied = { page: 0, pageSize: 25 };
      Object.assign(this.filters, emptySearchDraft());
      if (this.api.context()) this.loadPage(0);
    });
    if (this.api.context()) this.loadPage(this.page);
  }

  search() {
    this.validation.set('');
    const filters = this.buildFilters();
    if (!filters) return;
    this.applied = filters;
    this.loadPage(0);
  }

  reset() {
    Object.assign(this.filters, {
      orderId: '',
      from: '',
      to: '',
      clientReference: '',
      instrument: '',
      status: '',
    });
    this.validation.set('');
    this.page = 0;
    this.search();
  }

  loadPage(page: number) {
    if (!Number.isInteger(page) || page < 0 || page > 1000 || !this.api.context()) return;
    this.page = page;
    this.applied = { ...this.applied, page };
    this.state.applied = { ...this.applied };
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: this.filterQuery(),
      replaceUrl: true,
    });
    this.resource.load(this.api.search({ ...this.applied }));
  }

  filterQuery(): Record<string, string> {
    return safeSearchQuery(this.applied);
  }

  pages(result: ComplianceSearchPage) {
    return Math.max(1, Math.ceil(result.total / result.pageSize));
  }
  legacyStatus(status: string) {
    return ['PENDING', 'APPROVED', 'EXECUTED'].includes(status);
  }

  time(value: string | null) {
    return value ? `${value} UTC` : 'Not recorded';
  }

  private buildFilters(): ComplianceSearchFilters | null {
    try {
      return validateSearch(this.filters);
    } catch (error) {
      this.validation.set((error as Error).message);
      return null;
    }
  }
}

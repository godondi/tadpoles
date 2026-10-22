import { Component, DestroyRef, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ComplianceApi } from './compliance-api.service';
import { ComplianceContextComponent } from './compliance-context.component';
import { ComplianceResource } from './compliance-resource';
import { ComplianceSearchFilters, ComplianceSearchPage } from './compliance.models';
import { RequestStateComponent } from '../../shared/workspace/request-state.component';

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
            <input id="compliance-from" name="from" type="datetime-local" [(ngModel)]="filters.from" />
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
                <option [value]="status">{{ status }}</option>
              }
            </select>
          </div>
          <button type="submit" [disabled]="resource.loading()">Apply filters</button>
          <button type="button" (click)="reset()" [disabled]="resource.loading()">Reset</button>
        </form>
        <p>
          Results are scoped before counts and paging. Date filters are UTC and may span at most 31
          days.
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
          <div class="table-wrapper" tabindex="0" aria-label="Compliance investigations, scroll horizontally">
            <table>
              <caption>Permitted investigation results</caption>
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
                    <td>{{ item.orderId }}<small>{{ item.clientReference }}</small></td>
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
                  <tr><td colspan="5">No permitted investigations match these filters.</td></tr>
                }
              </tbody>
            </table>
          </div>
          <nav aria-label="Compliance investigation pages" class="pagination">
            <button type="button" (click)="loadPage(page - 1)" [disabled]="page === 0 || resource.loading()">
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
  readonly statuses = ['SUBMITTED', 'ACCEPTED', 'REJECTED', 'FILLED', 'CANCELLED'];
  readonly filters = {
    orderId: '',
    from: '',
    to: '',
    clientReference: '',
    instrument: '',
    status: '',
  };
  page = 0;

  constructor() {
    this.api.changed.pipe(takeUntilDestroyed()).subscribe(() => {
      if (this.api.context()) this.loadPage(0);
    });
    if (this.api.context()) this.loadPage(0);
  }

  search() {
    this.validation.set('');
    const filters = this.buildFilters();
    if (!filters) return;
    this.page = 0;
    this.resource.load(this.api.search(filters));
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
    this.loadPage(0);
  }

  loadPage(page: number) {
    const filters = this.buildFilters();
    if (!filters) return;
    this.page = Math.max(0, page);
    this.resource.load(this.api.search({ ...filters, page: this.page }));
  }

  filterQuery(): Record<string, string> {
    return Object.fromEntries(
      Object.entries(this.filters).filter(([, value]) => Boolean(value)),
    ) as Record<string, string>;
  }

  pages(result: ComplianceSearchPage) {
    return Math.max(1, Math.ceil(result.total / result.pageSize));
  }

  time(value: string | null) {
    return value ? `${value} UTC` : 'Not recorded';
  }

  private buildFilters(): ComplianceSearchFilters | null {
    const orderId = this.filters.orderId.trim();
    if (orderId && (!/^[1-9]\d*$/.test(orderId) || Number(orderId) > Number.MAX_SAFE_INTEGER)) {
      this.validation.set('Order ID must be a positive integer.');
      return null;
    }
    if (this.filters.from && this.filters.to) {
      const from = Date.parse(this.filters.from);
      const to = Date.parse(this.filters.to);
      if (!Number.isFinite(from) || !Number.isFinite(to) || to <= from) {
        this.validation.set('The UTC end time must be after the start time.');
        return null;
      }
      if (to - from > 31 * 24 * 60 * 60 * 1000) {
        this.validation.set('The date range cannot exceed 31 days.');
        return null;
      }
    }
    return {
      ...(orderId ? { orderId: Number(orderId) } : {}),
      ...(this.filters.from ? { from: this.filters.from } : {}),
      ...(this.filters.to ? { to: this.filters.to } : {}),
      ...(this.filters.clientReference.trim()
        ? { clientReference: this.filters.clientReference.trim() }
        : {}),
      ...(this.filters.instrument.trim() ? { instrument: this.filters.instrument.trim() } : {}),
      ...(this.filters.status ? { status: this.filters.status } : {}),
      page: this.page,
      pageSize: 25,
    };
  }
}

import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AdvisorApi } from './advisor-api.service';
import { AdvisorClient, AdvisorPage } from './advisor-models';
import { PageResource } from '../../shared/workspace/page-resource';
import { RequestStateComponent } from '../../shared/workspace/request-state.component';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-advisor-clients-page',
  imports: [DatePipe, FormsModule, RouterLink, RequestStateComponent],
  template: `
    <header class="workspace-heading">
      <p class="eyebrow">ADVISOR WORKSPACE</p>
      <h1 tabindex="-1">Assigned clients</h1>
      <p>Review current holdings and record advisory guidance for clients assigned to you.</p>
    </header>

    <section class="panel" aria-labelledby="client-search-heading">
      <h2 id="client-search-heading">Find an assigned client</h2>
      <form (ngSubmit)="searchClients()">
        <label for="advisor-client-search">Search by name or safe client reference</label>
        <div class="advisor-search-row">
          <input
            id="advisor-client-search"
            name="search"
            type="search"
            [(ngModel)]="search"
            maxlength="100"
          />
          <button type="submit" [disabled]="resource.loading()">Search</button>
        </div>
      </form>
    </section>

    <app-request-state
      [loading]="resource.loading() && !resource.data()"
      [error]="resource.error()"
      [status]="resource.status()"
      (retry)="load()"
    />

    @if (resource.data(); as page) {
      @if (!page.items.length) {
        <section class="panel empty" aria-live="polite">
          <h2>No clients are assigned</h2>
          <p>A successful response contained no clients for this Advisor account.</p>
        </section>
      } @else {
        <section class="panel" aria-labelledby="assigned-clients-heading">
          <div class="section-heading">
            <h2 id="assigned-clients-heading">Your clients</h2>
            <p class="muted">{{ page.total }} assigned client{{ page.total === 1 ? '' : 's' }}</p>
          </div>
          <div class="table-wrapper" tabindex="0" aria-label="Assigned clients, scroll horizontally">
            <table>
              <caption>Assigned clients and the latest available client record time</caption>
              <thead>
                <tr>
                  <th scope="col">Client</th>
                  <th scope="col">Reference</th>
                  <th scope="col">Assignment</th>
                  <th scope="col">Last updated (UTC)</th>
                  <th scope="col">Action</th>
                </tr>
              </thead>
              <tbody>
                @for (client of page.items; track client.clientId) {
                  <tr>
                    <th scope="row">{{ client.displayName }}</th>
                    <td>{{ client.clientReference }}</td>
                    <td>{{ client.assignmentStatus || 'Assigned' }}</td>
                    <td>{{ client.updatedAt | date: 'medium' : 'UTC' }} UTC</td>
                    <td><a [routerLink]="['/advisor/clients', client.clientId]">View holdings</a></td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
          <nav class="pagination" aria-label="Assigned client pages">
            <button type="button" (click)="previousPage()" [disabled]="page.page === 0 || resource.loading()">
              Previous
            </button>
            <span>Page {{ page.page + 1 }} of {{ pageCount(page) }}</span>
            <button type="button" (click)="nextPage(page)" [disabled]="!hasNextPage(page) || resource.loading()">
              Next
            </button>
          </nav>
        </section>
      }
    }
  `,
})
export class AdvisorClientsPageComponent {
  private readonly api = inject(AdvisorApi);
  readonly resource = new PageResource<AdvisorPage<AdvisorClient>>();
  search = '';
  private readonly currentPage = signal(0);
  private readonly pageSize = 20;

  constructor() {
    inject(AuthService).sessionChanged.pipe(takeUntilDestroyed()).subscribe(() => {
      this.search = '';
      this.currentPage.set(0);
    });
    this.load();
  }

  load(): void {
    this.resource.load(
      this.api.clients({ page: this.currentPage(), pageSize: this.pageSize, search: this.search.trim() }),
    );
  }

  searchClients(): void {
    this.currentPage.set(0);
    this.load();
  }

  previousPage(): void {
    if (this.currentPage() === 0) return;
    this.currentPage.update((page) => page - 1);
    this.load();
  }

  nextPage(page: AdvisorPage<AdvisorClient>): void {
    if (!this.hasNextPage(page)) return;
    this.currentPage.update((current) => current + 1);
    this.load();
  }

  hasNextPage(page: AdvisorPage<AdvisorClient>): boolean {
    return (page.page + 1) * page.pageSize < page.total;
  }

  pageCount(page: AdvisorPage<AdvisorClient>): number {
    return Math.max(1, Math.ceil(page.total / page.pageSize));
  }
}

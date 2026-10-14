import { Component, DestroyRef, inject } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { WorkspaceApi, Instrument, Page } from '../../core/api/workspace-api.service';
import { PageResource } from '../../shared/workspace/page-resource';
import { RequestStateComponent } from '../../shared/workspace/request-state.component';
@Component({
  selector: 'app-markets-page',
  imports: [RouterLink, FormsModule, RequestStateComponent],
  template: ` <header class="workspace-heading">
      <p class="eyebrow">EXPLORE</p>
      <h1 tabindex="-1">Markets</h1>
      <p>Find an instrument and view its indicative price history.</p>
    </header>
    <aside class="notice">
      <strong>Browsing only</strong>
      <p>
        Catalog inclusion and active status do not mean an instrument is approved for trading.
        Trading is not enabled.
      </p>
    </aside>
    <form class="filters" (ngSubmit)="search()">
      <label
        >Symbol or name<input
          name="search"
          [(ngModel)]="query"
          maxlength="100"
          placeholder="Search the catalog" /></label
      ><label
        >Asset class<input
          name="assetClass"
          [(ngModel)]="assetClass"
          maxlength="60"
          placeholder="Any asset class" /></label
      ><button type="submit">Search</button>
    </form>
    <app-request-state
      [loading]="resource.loading()"
      [error]="resource.error()"
      [status]="resource.status()"
      (retry)="load()"
    />
    @if (resource.data(); as data) {
      <section class="panel">
        @if (!data.items.length) {
          <div class="empty">
            <h2>No matching instruments</h2>
            <p>Try another symbol or clear your filters.</p>
          </div>
        } @else {
          <div
            class="table-wrapper"
            tabindex="0"
            aria-label="Instrument catalog, scroll horizontally"
          >
            <table>
              <caption>
                Instrument catalog ·
                {{
                  data.total
                }}
                results
              </caption>
              <thead>
                <tr>
                  <th scope="col">Instrument</th>
                  <th scope="col">Asset class</th>
                  <th scope="col">Currency</th>
                  <th scope="col">Catalog status</th>
                </tr>
              </thead>
              <tbody>
                @for (item of data.items; track item.instrumentId) {
                  <tr>
                    <td>
                      <a [routerLink]="['/app/instruments', item.instrumentId]">{{ item.ticker }}</a
                      ><small>{{ item.instrumentName }}</small>
                    </td>
                    <td>{{ item.assetClass || 'Not recorded' }}</td>
                    <td>{{ item.currency }}</td>
                    <td>{{ item.isActive ? 'Active · browsing only' : 'Inactive' }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
        <nav class="pagination" aria-label="Catalog pages">
          <button [disabled]="page === 0" (click)="go(page - 1)">Previous</button
          ><span>Page {{ page + 1 }}</span
          ><button [disabled]="(page + 1) * 20 >= data.total" (click)="go(page + 1)">Next</button>
        </nav>
      </section>
    }`,
})
export class MarketsPageComponent {
  private readonly api = inject(WorkspaceApi);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  readonly resource = new PageResource<Page<Instrument>>();
  query = '';
  assetClass = '';
  page = 0;
  constructor() {
    this.route.queryParamMap.pipe(takeUntilDestroyed()).subscribe((params) => {
      this.query = params.get('search') ?? '';
      this.assetClass = params.get('assetClass') ?? '';
      this.page = Math.max(0, Number(params.get('page')) || 0);
      this.load();
    });
  }
  load() {
    this.resource.load(
      this.api.instruments({
        page: this.page,
        search: this.query,
        ...(this.assetClass ? { assetClass: this.assetClass } : {}),
      }),
    );
  }
  search() {
    this.go(0);
  }
  go(page: number) {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { search: this.query || null, assetClass: this.assetClass || null, page },
    });
  }
}

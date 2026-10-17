import { Component, inject } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { WorkspaceApi, Instrument, Page } from '../../core/api/workspace-api.service';
import { CatalogRefreshService } from '../../core/api/catalog-refresh.service';
import { PageResource } from '../../shared/workspace/page-resource';
import { RequestStateComponent } from '../../shared/workspace/request-state.component';

@Component({
  selector: 'app-markets-page',
  imports: [RouterLink, FormsModule, RequestStateComponent],
  template: `
    <header class="workspace-heading">
      <p class="eyebrow">EXPLORE</p>
      <h1 tabindex="-1">Markets</h1>
      <p>Find an instrument and view its indicative price history.</p>
    </header>
    <p class="muted">Catalog changes appear automatically. Your filters stay selected.</p>
    <aside class="notice">
      <strong>Explore before you trade</strong>
      <p>
        Review instrument rules before trading. Orders use a separate, validated Fauxnance quote;
        chart values do not guarantee an execution price.
      </p>
    </aside>
    <form class="filters" (ngSubmit)="search()">
      <label>
        Symbol or name
        <input name="search" [(ngModel)]="query" maxlength="100" placeholder="Search the catalog" />
      </label>
      <label>
        Asset class
        <input
          name="assetClass"
          [(ngModel)]="assetClass"
          maxlength="60"
          placeholder="Any asset class"
        />
      </label>
      <button type="submit">Search</button>
    </form>
    <app-request-state
      [loading]="resource.loading() && !resource.data()"
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
                {{ data.total }}
                results
              </caption>
              <thead>
                <tr>
                  <th scope="col">Instrument</th>
                  <th scope="col">Asset class</th>
                  <th scope="col">Currency</th>
                  <th scope="col">Trading availability</th>
                </tr>
              </thead>
              <tbody>
                @for (item of data.items; track item.instrumentId) {
                  <tr>
                    <td>
                      <a [routerLink]="['/app/instruments', item.instrumentId]">
                        {{ item.ticker }}
                      </a>
                      <small>{{ item.instrumentName }}</small>
                    </td>
                    <td>{{ item.assetClass || 'Not recorded' }}</td>
                    <td>{{ item.currency }}</td>
                    <td>
                      {{ item.trading?.enabled ? 'Review trading rules' : 'Browsing only' }}
                      <small>
                        {{
                          item.trading?.enabled
                            ? item.trading?.venue + ' · ' + item.trading?.quantityUnit
                            : item.trading?.reason || 'Open instrument for eligibility details'
                        }}
                      </small>
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
        <nav class="pagination" aria-label="Catalog pages">
          <button [disabled]="page === 0" (click)="go(page - 1)">Previous</button>
          <span>Page {{ page + 1 }}</span>
          <button [disabled]="(page + 1) * 20 >= data.total" (click)="go(page + 1)">Next</button>
        </nav>
      </section>
    }
  `,
})
export class MarketsPageComponent {
  private readonly api = inject(WorkspaceApi);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  readonly resource = new PageResource<Page<Instrument>>();
  query = '';
  assetClass = '';
  page = 0;
  private appliedQuery = '';
  private appliedAssetClass = '';

  constructor() {
    this.route.queryParamMap.pipe(takeUntilDestroyed()).subscribe((params) => {
      this.query = params.get('search') ?? '';
      this.assetClass = params.get('assetClass') ?? '';
      this.appliedQuery = this.query;
      this.appliedAssetClass = this.assetClass;
      this.page = Math.max(0, Number(params.get('page')) || 0);
      this.load();
    });
    inject(CatalogRefreshService)
      .watch()
      .pipe(takeUntilDestroyed())
      .subscribe(() => {
        if (!this.resource.loading()) this.load();
      });
  }

  load() {
    this.resource.load(
      this.api.instruments({
        page: this.page,
        search: this.appliedQuery,
        ...(this.appliedAssetClass ? { assetClass: this.appliedAssetClass } : {}),
      }),
      true,
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

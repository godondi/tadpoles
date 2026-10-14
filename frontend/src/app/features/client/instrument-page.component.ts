import { Component, inject } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { forkJoin } from 'rxjs';
import {
  WorkspaceApi,
  Instrument,
  TradingAvailability,
} from '../../core/api/workspace-api.service';
import { PageResource } from '../../shared/workspace/page-resource';
import { RequestStateComponent } from '../../shared/workspace/request-state.component';
import { TradeComponent } from '../trade/trade.component';
@Component({
  selector: 'app-instrument-page',
  imports: [RouterLink, RequestStateComponent, TradeComponent],
  template: ` <a routerLink="/app/markets">← Markets</a>
    <header class="workspace-heading">
      <p class="eyebrow">{{ ticket ? 'ORDER TICKET' : 'INSTRUMENT DETAILS' }}</p>
      <h1 tabindex="-1">{{ ticket ? 'Trading availability' : 'Instrument details' }}</h1>
    </header>
    <app-request-state
      [loading]="resource.loading()"
      [error]="resource.error()"
      [status]="resource.status()"
      (retry)="load()"
    />
    @if (resource.data(); as data) {
      <header class="workspace-heading">
        <p class="eyebrow">{{ ticket ? 'ORDER TICKET' : 'INSTRUMENT DETAILS' }}</p>
        <h2>{{ data.instrument.instrumentName }}</h2>
        <p>
          {{ data.instrument.ticker }} · {{ data.instrument.currency }} ·
          {{ data.instrument.assetClass || 'Asset class not recorded' }} ·
          {{ data.instrument.isActive ? 'Active catalog entry' : 'Inactive' }}
        </p>
        <a routerLink="/app/orders" [queryParams]="{ instrumentId: data.instrument.instrumentId }"
          >Your orders for {{ data.instrument.ticker }}</a
        >
      </header>
      <section class="notice">
        <h2>Trading is not available yet</h2>
        <p>{{ data.availability.reason }}</p>
        <p>No order will be created and no money will move.</p>
        @if (ticket) {
          <div class="actions">
            <button disabled>Buy unavailable</button><button disabled>Sell unavailable</button
            ><a [routerLink]="['/app/instruments', data.instrument.instrumentId]"
              >Return to instrument</a
            >
          </div>
        } @else {
          <a [routerLink]="['/app/trade', data.instrument.instrumentId]"
            >View trading availability →</a
          >
        }
      </section>
      @if (!ticket) {
        <p class="muted">
          Prices are indicative. Market hours and execution eligibility are not confirmed for this
          catalog entry.
        </p>
        <app-trade [catalogSymbol]="data.instrument.ticker" [embedded]="true" />
      }
    }`,
})
export class InstrumentPageComponent {
  private readonly api = inject(WorkspaceApi);
  private readonly route = inject(ActivatedRoute);
  readonly resource = new PageResource<{
    instrument: Instrument;
    availability: TradingAvailability;
  }>();
  readonly ticket = this.route.snapshot.data['ticket'] === true;
  id = '';
  constructor() {
    this.route.paramMap.pipe(takeUntilDestroyed()).subscribe((params) => {
      this.id = params.get('id') ?? params.get('instrumentId') ?? '';
      this.load();
    });
  }
  load() {
    this.resource.load(
      forkJoin({ instrument: this.api.instrument(this.id), availability: this.api.availability() }),
    );
  }
}
@Component({
  selector: 'app-legacy-instrument',
  imports: [RouterLink, RequestStateComponent],
  template: ` <header class="workspace-heading">
      <h1 tabindex="-1">Find instrument</h1>
      <p>Checking {{ symbol }} against the instrument catalog.</p>
    </header>
    <app-request-state
      [loading]="resource.loading()"
      [error]="resource.error()"
      [status]="resource.status()"
      (retry)="load()"
    />
    @if (resource.data() && !resource.loading()) {
      <section class="notice">
        <h2>Symbol not supported</h2>
        <p>No exact catalog entry matches this symbol.</p>
        <a routerLink="/app/markets">Browse the catalog</a>
      </section>
    }`,
})
export class LegacyInstrumentComponent {
  private readonly api = inject(WorkspaceApi);
  private readonly router = inject(Router);
  readonly resource = new PageResource<
    import('../../core/api/workspace-api.service').Page<Instrument>
  >();
  symbol = '';
  constructor() {
    inject(ActivatedRoute)
      .paramMap.pipe(takeUntilDestroyed())
      .subscribe((params) => {
        this.symbol = params.get('symbol') ?? '';
        this.load();
      });
  }
  load() {
    this.resource.load(
      this.api.instruments({ search: this.symbol, pageSize: 100 }).pipe(
        importTap((data) => {
          const instrument = data.items.find(
            (item) => item.ticker.toUpperCase() === this.symbol.toUpperCase(),
          );
          if (instrument)
            void this.router.navigate(['/app/instruments', instrument.instrumentId], {
              replaceUrl: true,
            });
        }),
      ),
    );
  }
}
import { tap as importTap } from 'rxjs';

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
import { OrderTicketComponent } from '../trade/order-ticket.component';
import { TradeComponent } from '../trade/trade.component';

@Component({
  selector: 'app-instrument-page',
  imports: [RouterLink, RequestStateComponent, TradeComponent, OrderTicketComponent],
  template: ` <a routerLink="/app/markets">← Markets</a>
    <header class="workspace-heading">
      <p class="eyebrow">{{ ticket ? 'ORDER TICKET' : 'INSTRUMENT DETAILS' }}</p>
      <h1 tabindex="-1">{{ ticket ? 'Place an order' : 'Instrument details' }}</h1>
    </header>
    <app-request-state
      [loading]="resource.loading()"
      [error]="resource.error()"
      [status]="resource.status()"
      (retry)="load()"
    />
    @if (resource.data(); as data) {
      <header class="workspace-heading">
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
      @if (data.availability.enabled || data.availability.code === 'BANK_REQUIRED') {
        @if (ticket) {
          <app-order-ticket
            [instrumentId]="data.instrument.instrumentId"
            [ticker]="data.instrument.ticker"
            [initialSide]="sideHint"
            [bankRequired]="data.availability.code === 'BANK_REQUIRED'"
          />
        } @else {
          <section class="panel">
            <p class="eyebrow">INTERNAL BOOKING · ZERO PROJECT FEES</p>
            <h2>Review a buy or sell</h2>
            <p>
              {{ data.availability.venue }} · {{ data.availability.quantityUnit }} · Fauxnance quote
              checks
            </p>
            <div class="actions">
              <a
                class="button-link"
                [routerLink]="['/app/trade', data.instrument.instrumentId]"
                [queryParams]="{ side: 'BUY' }"
                >Buy {{ data.instrument.ticker }}</a
              >
              <a
                [routerLink]="['/app/trade', data.instrument.instrumentId]"
                [queryParams]="{ side: 'SELL' }"
                >Sell</a
              >
            </div>
          </section>
        }
      } @else {
        <section class="notice">
          <h2>Trading unavailable for this instrument</h2>
          <p>{{ data.availability.reason }}</p>
          <p>You can still browse its market information.</p>
        </section>
      }
      @if (!ticket) {
        <p class="muted">
          {{
            data.availability.enabled
              ? 'Chart values are informational. Every order uses a separate fresh provider quote at execution.'
              : 'Provider prices are indicative. Trading eligibility is not confirmed for this instrument.'
          }}
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
  readonly sideHint = this.route.snapshot.queryParamMap.get('side') === 'SELL' ? 'SELL' : 'BUY';
  id = '';

  constructor() {
    this.route.paramMap.pipe(takeUntilDestroyed()).subscribe((params) => {
      this.id = params.get('id') ?? params.get('instrumentId') ?? '';
      this.load();
    });
  }

  load() {
    this.resource.load(
      forkJoin({
        instrument: this.api.instrument(this.id),
        availability: this.api.availability(this.id),
      }),
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

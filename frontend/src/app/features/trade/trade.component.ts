import { Component, Input, OnDestroy, inject, signal, computed } from '@angular/core';
import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import {
  Candle,
  CandlesResponse,
  MarketDataService,
  QuoteResponse,
} from '../../core/api/market-data.service';
import { LoadingComponent } from '../../shared/loading/loading.component';
import { dailyCandles } from './daily-candles';
import { CandleChartComponent } from './candle-chart.component';

@Component({
  selector: 'app-trade',
  imports: [CurrencyPipe, DatePipe, DecimalPipe, LoadingComponent, CandleChartComponent],
  templateUrl: './trade.component.html',
  styleUrl: './trade.component.css',
})
export class TradeComponent implements OnDestroy {
  @Input() embedded = false;
  private readonly marketData = inject(MarketDataService);
  private requests = new Subscription();
  readonly symbol = signal('');
  readonly quote = signal<QuoteResponse | null>(null);
  readonly candleResponse = signal<CandlesResponse | null>(null);
  private readonly allCandles = signal<Candle[]>([]);
  readonly rangeDays = signal(30);
  readonly candles = computed(() => {
    const rows = this.allCandles();
    if (!rows.length) return [];
    const cutoff = new Date(rows[rows.length - 1].date + 'T00:00:00Z');
    cutoff.setUTCDate(cutoff.getUTCDate() - this.rangeDays());
    return rows.filter((c) => c.date > cutoff.toISOString().slice(0, 10));
  });
  readonly loadingCandles = signal(false);
  readonly loadingQuote = signal(false);
  readonly candleError = signal('');
  readonly quoteError = signal('');
  readonly notFound = signal('');
  private readonly routeSubscription = inject(ActivatedRoute).paramMap.subscribe((params) => {
    const symbol = params.get('symbol');
    if (symbol) this.changeSymbol(symbol);
  });
  @Input() set catalogSymbol(symbol: string) {
    if (symbol) this.changeSymbol(symbol);
  }

  private changeSymbol(symbol: string) {
    this.requests.unsubscribe();
    this.requests = new Subscription();
    this.symbol.set(symbol.trim().toUpperCase());
    this.quote.set(null);
    this.candleResponse.set(null);
    this.allCandles.set([]);
    this.quoteError.set('');
    this.candleError.set('');
    this.notFound.set('');
    this.loadingQuote.set(false);
    this.loadCandles();
    this.refreshQuote();
  }

  loadCandles() {
    this.loadingCandles.set(true);
    this.candleError.set('');
    this.requests.add(
      this.marketData.getCandles(this.symbol()).subscribe({
        next: (response) => {
          try {
            this.allCandles.set(dailyCandles(response, this.symbol()));
            this.candleResponse.set(response);
          } catch (error) {
            this.allCandles.set([]);
            this.candleError.set(error instanceof Error ? error.message : 'Invalid daily data');
          }
          this.loadingCandles.set(false);
        },
        error: (error) => {
          this.candleError.set(this.errorMessage(error));
          this.loadingCandles.set(false);
        },
      }),
    );
  }

  refreshQuote() {
    if (this.loadingQuote()) return;
    this.loadingQuote.set(true);
    this.quoteError.set('');
    this.requests.add(
      this.marketData.getQuote(this.symbol()).subscribe({
        next: (response) => {
          this.quote.set(response);
          this.loadingQuote.set(false);
        },
        error: (error) => {
          this.quoteError.set(this.errorMessage(error));
          this.loadingQuote.set(false);
        },
      }),
    );
  }

  private errorMessage(error: HttpErrorResponse) {
    const message =
      typeof error.error?.message === 'string' && error.error.message.trim()
        ? error.error.message
        : error.status === 404
          ? `Ticker ${this.symbol()} was not found.`
          : error.status === 401
            ? 'Your session has expired. Please sign in again.'
            : 'Unable to load market data. Please try again.';
    if (error.status === 404) this.notFound.set(message);
    return message;
  }

  ngOnDestroy() {
    this.routeSubscription.unsubscribe();
    this.requests.unsubscribe();
  }
}

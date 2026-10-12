import { Component, OnDestroy, inject, signal } from '@angular/core';
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
import { CandleChartComponent } from './candle-chart.component';

@Component({
  selector: 'app-trade',
  imports: [CurrencyPipe, DatePipe, DecimalPipe, LoadingComponent, CandleChartComponent],
  templateUrl: './trade.component.html',
  styleUrl: './trade.component.css',
})
export class TradeComponent implements OnDestroy {
  private readonly marketData = inject(MarketDataService);
  private requests = new Subscription();
  readonly symbol = signal('');
  readonly quote = signal<QuoteResponse | null>(null);
  readonly candleResponse = signal<CandlesResponse | null>(null);
  readonly candles = signal<Candle[]>([]);
  readonly loadingCandles = signal(false);
  readonly loadingQuote = signal(false);
  readonly candleError = signal('');
  readonly quoteError = signal('');
  readonly notFound = signal('');
  private readonly routeSubscription = inject(ActivatedRoute).paramMap.subscribe((params) => {
    this.requests.unsubscribe();
    this.requests = new Subscription();
    this.symbol.set((params.get('symbol') ?? '').trim().toUpperCase());
    this.quote.set(null);
    this.candleResponse.set(null);
    this.candles.set([]);
    this.quoteError.set('');
    this.candleError.set('');
    this.notFound.set('');
    this.loadingQuote.set(false);
    this.loadCandles();
    this.refreshQuote();
  });

  loadCandles() {
    this.loadingCandles.set(true);
    this.candleError.set('');
    this.requests.add(
      this.marketData.getCandles(this.symbol()).subscribe({
        next: (response) => {
          this.candleResponse.set(response);
          this.candles.set(
            [...response.data.candles].sort((a, b) => a.date.localeCompare(b.date)).slice(-30),
          );
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

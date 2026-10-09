import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { TradeComponent } from './trade.component';

export const quote = {
  data: {
    symbol: 'AAPL',
    price: 210,
    bid: 209,
    ask: 211,
    spreadBps: 95.2,
    currency: 'USD',
    change: -2,
    changePercent: -0.94,
    previousClose: 212,
    asOf: '2026-10-08T15:00:00Z',
    marketState: 'open',
  },
  meta: {
    asOf: '2026-10-08T15:00:00Z',
    disclaimer: 'Educational data.',
    source: 'cache',
    symbol: 'AAPL',
    stale: true,
    spreadSource: 'modelled',
  },
};
export const candles = {
  data: {
    symbol: 'AAPL',
    interval: '1d',
    currency: 'USD',
    candles: Array.from({ length: 40 }, (_, i) => ({
      date: new Date(Date.UTC(2026, 7, i + 1)).toISOString().slice(0, 10),
      open: 200,
      high: 215,
      low: 195,
      close: i % 2 ? 210 : 198,
      adjclose: 210,
      volume: 1000,
      synthetic: false,
    })).reverse(),
  },
  meta: { ...quote.meta, partial: false },
};

describe('Trade page', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'trade/:symbol', component: TradeComponent }]),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
  });
  afterEach(() => http.verify());
  const request = (kind: string, symbol = 'AAPL') =>
    http.expectOne((r) => r.url.endsWith(`/${kind}/${symbol}`));
  const render = () => {
    harness.detectChanges();
    return harness.routeNativeElement!;
  };
  async function loaded() {
    await harness.navigateByUrl('/trade/aapl', TradeComponent);
    const req = request('candles');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('interval')).toBe('1d');
    expect(req.request.params.has('from')).toBe(true);
    expect(req.request.params.has('to')).toBe(true);
    req.flush(candles);
    request('quotes').flush(quote);
    return render();
  }
  it('shows reusable loading indicators while both requests are pending', async () => {
    await harness.navigateByUrl('/trade/AAPL');
    expect(render().querySelectorAll('app-loading').length).toBe(2);
    request('candles').flush(candles);
    expect(render().querySelectorAll('app-loading').length).toBe(1);
    request('quotes').flush(quote);
    expect(render().querySelector('app-loading')).toBeNull();
  });
  it('renders 30 chronological OHLC candles and the quote fields', async () => {
    const el = await loaded();
    const bars = el.querySelectorAll('[data-candle]');
    expect(bars.length).toBe(30);
    expect(bars[0].getAttribute('data-candle')).toBe('2026-08-11');
    expect(el.textContent).toContain('$210.00');
    for (const label of [
      'Bid',
      'Ask',
      'Previous close',
      'Spread',
      'Market',
      'As of',
      'Stale',
      'Educational data.',
    ]) {
      expect(el.textContent).toContain(label);
    }
  });
  it('refreshes only the quote, prevents duplicate clicks, and replaces the quote', async () => {
    const el = await loaded();
    const button = el.querySelector<HTMLButtonElement>('[data-refresh]')!;
    button.click();
    render();
    expect(button.disabled).toBe(true);
    button.click();
    request('quotes').flush({ ...quote, data: { ...quote.data, price: 220 } });
    expect(render().textContent).toContain('$220.00');
    expect(button.disabled).toBe(false);
  });
  it('shows the backend error below a warning triangle for an unknown ticker', async () => {
    await harness.navigateByUrl('/trade/INVALID');
    request('candles', 'INVALID').flush(candles);
    request('quotes', 'INVALID').flush(
      { message: 'Symbol INVALID was not found.' },
      { status: 404, statusText: 'Not Found' },
    );
    const el = render();
    expect(el.querySelector('[role="alert"] svg')).toBeTruthy();
    expect(el.textContent).toContain('Symbol INVALID was not found.');
  });
  it('keeps the chart and old quote on refresh failure and allows retry', async () => {
    const el = await loaded();
    el.querySelector<HTMLButtonElement>('[data-refresh]')!.click();
    request('quotes').flush(
      { message: 'Market data unavailable' },
      { status: 503, statusText: 'Unavailable' },
    );
    expect(render().querySelectorAll('[data-candle]').length).toBe(30);
    expect(el.textContent).toContain('$210.00');
    expect(el.textContent).toContain('Market data unavailable');
    el.querySelector<HTMLButtonElement>('[data-refresh]')!.click();
    request('quotes').flush(quote);
    expect(render().textContent).not.toContain('Market data unavailable');
  });
  it('cancels pending requests and clears data when the ticker changes', async () => {
    await harness.navigateByUrl('/trade/AAPL');
    const oldCandles = request('candles');
    const oldQuote = request('quotes');
    await harness.navigateByUrl('/trade/MSFT');
    expect(oldCandles.cancelled).toBe(true);
    expect(oldQuote.cancelled).toBe(true);
    request('candles', 'MSFT').flush({ ...candles, data: { ...candles.data, candles: [] } });
    request('quotes', 'MSFT').flush({ ...quote, data: { ...quote.data, symbol: 'MSFT' } });
    expect(render().textContent).toContain('No daily candles available');
    expect(render().textContent).toContain('MSFT');
  });
  it('handles a candle failure independently and retries the chart', async () => {
    await harness.navigateByUrl('/trade/AAPL');
    request('candles').flush({}, { status: 503, statusText: 'Unavailable' });
    request('quotes').flush(quote);
    const el = render();
    expect(el.textContent).toContain('$210.00');
    el.querySelector<HTMLButtonElement>('[data-retry-candles]')!.click();
    request('candles').flush(candles);
    expect(render().querySelectorAll('[data-candle]').length).toBe(30);
  });
  it('shows limited history and nullable quote values without inventing a currency', async () => {
    await harness.navigateByUrl('/trade/AAPL');
    request('candles').flush({
      ...candles,
      data: { ...candles.data, candles: candles.data.candles.slice(0, 3) },
      meta: { ...candles.meta, partial: true },
    });
    request('quotes').flush({
      ...quote,
      data: {
        ...quote.data,
        currency: null,
        change: null,
        changePercent: null,
        previousClose: null,
      },
    });
    const el = render();
    expect(el.querySelectorAll('[data-candle]').length).toBe(3);
    expect(el.textContent).toContain('Only 3 daily candles available');
    expect(el.textContent).toContain('Partial price history');
    expect(el.textContent).toContain('Not specified');
    expect(el.querySelector('.price')!.textContent).not.toContain('$');
  });
  it('uses the warning view when the candle endpoint rejects the ticker', async () => {
    await harness.navigateByUrl('/trade/INVALID');
    request('candles', 'INVALID').flush({}, { status: 404, statusText: 'Not Found' });
    request('quotes', 'INVALID').flush(quote);
    const el = render();
    expect(el.querySelector('[role="alert"] svg')).toBeTruthy();
    expect(el.textContent).toContain('Ticker INVALID was not found.');
    expect(el.querySelector('app-candle-chart')).toBeNull();
  });
});

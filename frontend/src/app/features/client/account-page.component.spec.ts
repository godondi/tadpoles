import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { Subject, EMPTY } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { OrderStatusService } from '../../core/api/order-status.service';
import { AccountPageComponent } from './account-page.component';
describe('independent account resources', () => {
  it('displays explicit NONE guidance as N/A rather than the wire enum', () => {
    TestBed.configureTestingModule({
      imports: [AccountPageComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
        { provide: AuthService, useValue: { sessionChanged: new Subject<void>() } },
        { provide: OrderStatusService, useValue: { watch: () => EMPTY } }],
    });
    const fixture = TestBed.createComponent(AccountPageComponent);
    const http = TestBed.inject(HttpTestingController);
    http.match(() => true).forEach((request) => request.flush({}));
    fixture.componentInstance.advisory.data.set({ guidance: [{ instrumentId: 42, status: 'NONE', reasoning: null, authorName: null, updatedAt: '2026-10-22T18:00:00Z' }], recommendations: [], asOf: null, source: 'test' });
    expect(fixture.componentInstance.guidanceStatus(42)).toBe('N/A');
    http.verify();
  });

  it('keeps confirmed balances useful when recent orders fail', () => {
    TestBed.configureTestingModule({
      imports: [AccountPageComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: { sessionChanged: new Subject<void>() } },
        { provide: OrderStatusService, useValue: { health: () => 'IDLE', lastSuccess: () => null, watch: () => EMPTY } },
      ],
    });
    const http = TestBed.inject(HttpTestingController);
    const f = TestBed.createComponent(AccountPageComponent);
    http
      .expectOne((r) => r.url.endsWith('/api/workspace/client/advisory'))
      .flush({ guidance: [], recommendations: [], asOf: null, source: 'test' });
    http
      .expectOne((r) => r.url.endsWith('/portfolio/valuation'))
      .flush({
        status: 'COMPLETE',
        totalValue: '1000',
        holdingsValue: '0',
        cashBalance: '1000',
        unpricedTickers: [],
      });
    http
      .expectOne((r) => r.url.endsWith('/portfolio'))
      .flush({
        tradingEnabled: true,
        balances: [{ currency: 'USD', posted: '1000', reserved: '500', available: '500' }],
        currentPositions: [],
        positions: [],
        retrievedAt: new Date().toISOString(),
      });
    http
      .expectOne((r) => r.url.endsWith('/orders'))
      .flush({}, { status: 503, statusText: 'Unavailable' });
    f.detectChanges();
    expect(f.nativeElement.textContent).toContain('Display currency');
    expect(f.nativeElement.querySelector('a[href="/app/support/new?category=ACCOUNT_REVIEW"]')).not.toBeNull();
    expect(f.nativeElement.textContent).toContain('Settled balance');
    expect(f.nativeElement.textContent).toContain('500');
    expect(f.nativeElement.textContent).toContain('Unable to connect');
    http.verify();
  });

  it('keeps financial information visible when client advisory data is unavailable', () => {
    TestBed.configureTestingModule({
      imports: [AccountPageComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: { sessionChanged: new Subject<void>() } },
        { provide: OrderStatusService, useValue: { health: () => 'IDLE', lastSuccess: () => null, watch: () => EMPTY } },
      ],
    });
    const http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(AccountPageComponent);
    http.expectOne((request) => request.url.endsWith('/api/workspace/client/advisory')).flush(
      { detail: 'Advisory information is unavailable.' },
      { status: 503, statusText: 'Unavailable' },
    );
    http.expectOne((request) => request.url.endsWith('/portfolio')).flush({
      balances: [{ currency: 'USD', posted: '1000', reserved: '0', available: '1000', updatedAt: '2026-10-22T18:00:00Z' }],
      currentPositions: [],
      retrievedAt: '2026-10-22T18:00:00Z',
    });
    http.expectOne((request) => request.url.endsWith('/portfolio/valuation')).flush({
      status: 'COMPLETE',
      totalValue: '1000',
      holdingsValue: '0',
      cashBalance: '1000',
      unpricedTickers: [],
      snapshotAt: '2026-10-22T18:00:00Z',
      quoteAsOf: null,
    });
    http.expectOne((request) => request.url.endsWith('/orders')).flush({ items: [], total: 0 });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Settled balance');
    expect(fixture.nativeElement.textContent).toContain('Advisory information is unavailable');
  });
});

describe('account background updates', () => {
  it('retains panels and currency selection while updates load, fail, and retry', () => {
    const changes = new Subject<boolean>();
    const sessionChanged = new Subject<void>();
    TestBed.configureTestingModule({
      imports: [AccountPageComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: { sessionChanged } },
        { provide: OrderStatusService, useValue: { health: () => 'IDLE', lastSuccess: () => null, watch: () => changes } },
      ],
    });
    const http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(AccountPageComponent);
    http
      .expectOne((r) => r.url.endsWith('/portfolio/valuation'))
      .flush({
        status: 'COMPLETE',
        totalValue: '1200',
        holdingsValue: '200',
        cashBalance: '1000',
        unpricedTickers: [],
      });
    const portfolio = {
      tradingEnabled: true,
      balances: [{ currency: 'USD', posted: '1000', reserved: '500', available: '500' }],
      currentPositions: [
        {
          instrumentId: 1,
          ticker: 'AAPL',
          name: 'Apple',
          currency: 'USD',
          owned: '2',
          reserved: '0',
          available: '2',
        },
      ],
      retrievedAt: '2026-10-16T14:00:00Z',
    };
    http.expectOne((r) => r.url.endsWith('/portfolio')).flush(portfolio);
    http.expectOne((r) => r.url.endsWith('/orders')).flush({ items: [], total: 0 });
    http
      .expectOne((r) => r.url.endsWith('/api/workspace/client/advisory'))
      .flush({ guidance: [], recommendations: [], asOf: null, source: 'test' });
    fixture.detectChanges();

    const positions = fixture.nativeElement.querySelector('.table-wrapper');
    const select = fixture.nativeElement.querySelector('select') as HTMLSelectElement;
    select.value = 'GBP';
    select.dispatchEvent(new Event('change'));
    http
      .expectOne((r) => r.params.get('currency') === 'GBP')
      .flush({
        currency: 'GBP',
        usd: '500',
        estimatedValue: '400',
        targetPerUsd: '0.8',
        rateDate: '2026-10-16',
        source: 'Test',
      });
    fixture.detectChanges();

    changes.next(true);
    http
      .expectOne((r) => r.url.endsWith('/portfolio/valuation'))
      .flush({
        status: 'COMPLETE',
        totalValue: '1000',
        holdingsValue: '0',
        cashBalance: '1000',
        unpricedTickers: [],
      });
    http
      .expectOne((r) => r.url.endsWith('/api/workspace/client/advisory'))
      .flush({ guidance: [], recommendations: [], asOf: null, source: 'test' });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.table-wrapper')).toBe(positions);
    expect(fixture.nativeElement.querySelector('select')).toBe(select);
    expect(select.value).toBe('GBP');
    expect(fixture.nativeElement.querySelector('app-loading')).toBeNull();
    http
      .expectOne((r) => r.url.endsWith('/portfolio'))
      .flush({}, { status: 503, statusText: 'Unavailable' });
    http.expectOne((r) => r.url.endsWith('/orders')).flush({ items: [], total: 0 });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.table-wrapper')).toBe(positions);
    expect(fixture.nativeElement.textContent).toContain('400 GBP');
    expect(fixture.nativeElement.textContent).toContain('Unable to connect');

    changes.next(true);
    http
      .expectOne((r) => r.url.endsWith('/portfolio/valuation'))
      .flush({
        status: 'COMPLETE',
        totalValue: '1000',
        holdingsValue: '0',
        cashBalance: '1000',
        unpricedTickers: [],
      });
    http
      .expectOne((r) => r.url.endsWith('/portfolio'))
      .flush({ ...portfolio, currentPositions: [] });
    http.expectOne((r) => r.url.endsWith('/orders')).flush({ items: [], total: 0 });
    http
      .expectOne((r) => r.url.endsWith('/api/workspace/client/advisory'))
      .flush({ guidance: [], recommendations: [], asOf: null, source: 'test' });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).not.toContain('AAPL');
    expect(fixture.nativeElement.textContent).toContain('Your first investment starts here');
    expect(fixture.nativeElement.querySelector('select')).toBe(select);
    expect(select.value).toBe('GBP');

    sessionChanged.next();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('select')).toBeNull();
    http.verify();
  });
});

describe('account summary metrics', () => {
  it('loads Investing independently and keeps settled cash useful if valuation fails', () => {
    TestBed.configureTestingModule({
      imports: [AccountPageComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: { sessionChanged: new Subject<void>() } },
        { provide: OrderStatusService, useValue: { health: () => 'IDLE', lastSuccess: () => null, watch: () => EMPTY } },
      ],
    });
    const http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(AccountPageComponent);
    http
      .expectOne((r) => r.url.endsWith('/portfolio'))
      .flush({
        balances: [{ currency: 'USD', posted: '1000.01', reserved: '200', available: '800.01' }],
        currentPositions: [],
      });
    http.expectOne((r) => r.url.endsWith('/orders')).flush({ items: [], total: 0 });
    http
      .expectOne((r) => r.url.endsWith('/api/workspace/client/advisory'))
      .flush({ guidance: [], recommendations: [], asOf: null, source: 'test' });
    fixture.detectChanges();
    const panels = fixture.nativeElement.querySelectorAll('.account-balances > *');
    expect(panels.length).toBe(3);
    expect(panels[0].textContent).toContain('Investing');
    expect(panels[1].textContent).toContain('Settled balance');
    expect(panels[1].textContent).toContain('800.01');
    expect(panels[1].textContent).toContain('Reserved for pending buys: 200');
    expect(panels[1].textContent).not.toContain('Unsettled');
    http
      .expectOne((r) => r.url.endsWith('/portfolio/valuation'))
      .flush({}, { status: 503, statusText: 'Unavailable' });
    fixture.detectChanges();
    expect(panels[0].textContent).toContain('Unavailable');
    expect(panels[1].textContent).toContain('800.01');
    http.verify();
  });
});

describe('Investing estimates', () => {
  it('preserves exact server amounts and replaces incomplete valuations without hiding settled cash', () => {
    TestBed.configureTestingModule({
      imports: [AccountPageComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: { sessionChanged: new Subject<void>() } },
        { provide: OrderStatusService, useValue: { health: () => 'IDLE', lastSuccess: () => null, watch: () => EMPTY } },
      ],
    });
    const http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(AccountPageComponent);
    http
      .expectOne((r) => r.url.endsWith('/portfolio'))
      .flush({
        balances: [{ currency: 'USD', posted: '1000.01', reserved: '200', available: '800.01' }],
        currentPositions: [],
      });
    http.expectOne((r) => r.url.endsWith('/orders')).flush({ items: [], total: 0 });
    http
      .expectOne((r) => r.url.endsWith('/portfolio/valuation'))
      .flush({
        status: 'COMPLETE',
        totalValue: '9007199254740993.02',
        holdingsValue: '9007199254739993.01',
        cashBalance: '1000.01',
        snapshotAt: '2026-10-16T20:00:00Z',
        quoteAsOf: '2026-10-16T19:59:50Z',
        unpricedTickers: [],
      });
    http
      .expectOne((r) => r.url.endsWith('/api/workspace/client/advisory'))
      .flush({ guidance: [], recommendations: [], asOf: null, source: 'test' });
    fixture.detectChanges();
    const panel = fixture.nativeElement.querySelector('[aria-labelledby="investing-heading"]');
    expect(panel.textContent).toContain('9007199254740993.02');
    expect(panel.textContent).toContain('Oldest quote');
    const currency = fixture.nativeElement.querySelector('select');
    panel.querySelector('button').click();
    fixture.detectChanges();
    expect(panel.textContent).toContain('Updating estimate');
    http
      .expectOne((r) => r.url.endsWith('/portfolio/valuation'))
      .flush({
        status: 'UNAVAILABLE',
        totalValue: null,
        holdingsValue: null,
        cashBalance: '1000.01',
        snapshotAt: '2026-10-16T20:01:00Z',
        quoteAsOf: null,
        unpricedTickers: ['AAPL'],
      });
    fixture.detectChanges();
    expect(panel.textContent).toContain('Prices unavailable for AAPL');
    expect(panel.textContent).not.toContain('9007199254740993.02');
    expect(
      fixture.nativeElement.querySelector('[aria-labelledby="settled-balance-heading"]')
        .textContent,
    ).toContain('800.01');
    expect(fixture.nativeElement.querySelector('select')).toBe(currency);
    http.verify();
  });
});

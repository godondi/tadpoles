import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { Subject, of } from 'rxjs';
import { MarketsPageComponent } from './markets-page.component';
import { AuthService } from '../../core/auth/auth.service';
describe('market catalog page', () => {
  let http: HttpTestingController;
  let changed: Subject<void>;
  beforeEach(() => {
    changed = new Subject<void>();
    TestBed.configureTestingModule({
      imports: [MarketsPageComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: { sessionChanged: changed } },
        {
          provide: ActivatedRoute,
          useValue: { queryParamMap: of(convertToParamMap({ search: 'AAPL', page: '2' })) },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  it('sends filters and pagination to the server and displays actual empty results', () => {
    const f = TestBed.createComponent(MarketsPageComponent);
    f.detectChanges();
    const req = http.expectOne((r) => r.url.endsWith('/instruments'));
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('search')).toBe('AAPL');
    req.flush({ items: [], page: 2, pageSize: 20, total: 0 });
    f.detectChanges();
    expect(f.nativeElement.textContent).toContain('No matching instruments');
    expect(f.nativeElement.textContent).not.toMatch(/simulation|synthetic test prices/i);
  });
  it('does not disguise forbidden reads as an empty catalog', () => {
    const f = TestBed.createComponent(MarketsPageComponent);
    http
      .expectOne((r) => r.url.endsWith('/instruments'))
      .flush({ message: 'Denied' }, { status: 403, statusText: 'Forbidden' });
    f.detectChanges();
    expect(f.nativeElement.textContent).toContain('You do not have access');
    expect(f.nativeElement.textContent).not.toContain('No matching instruments');
  });
  it('cancels the page request on account change', () => {
    const f = TestBed.createComponent(MarketsPageComponent);
    const req = http.expectOne((r) => r.url.endsWith('/instruments'));
    changed.next();
    expect(req.cancelled).toBe(true);
    expect(f.componentInstance.resource.data()).toBeNull();
  });
  it('retries a failed request without stale results', () => {
    const f = TestBed.createComponent(MarketsPageComponent);
    http
      .expectOne((r) => r.url.endsWith('/instruments'))
      .flush({}, { status: 503, statusText: 'Unavailable' });
    f.detectChanges();
    f.nativeElement.querySelector('app-request-state button').click();
    http
      .expectOne((r) => r.url.endsWith('/instruments'))
      .flush({ items: [], page: 2, pageSize: 20, total: 0 });
    f.detectChanges();
    expect(f.componentInstance.resource.error()).toBe('');
  });
});

describe('market catalog background refresh', () => {
  it('keeps existing rows mounted while refreshed enabled instruments arrive', () => {
    TestBed.configureTestingModule({
      imports: [MarketsPageComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: { sessionChanged: new Subject<void>() } },
        {
          provide: ActivatedRoute,
          useValue: { queryParamMap: of(convertToParamMap({ search: 'SPY' })) },
        },
      ],
    });
    const http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(MarketsPageComponent);
    const item = {
      instrumentId: 42,
      ticker: 'SPY',
      instrumentName: 'SPDR S&P 500 ETF Trust',
      currency: 'USD',
      exchange: 'NYSE Arca',
    };
    http
      .expectOne((r) => r.url.endsWith('/instruments'))
      .flush({ items: [item], total: 1, page: 0, pageSize: 20 });
    fixture.detectChanges();
    const table = fixture.nativeElement.querySelector('table');
    fixture.componentInstance.load();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('table')).toBe(table);
    expect(fixture.nativeElement.querySelector('app-loading')).toBeNull();
    const refresh = http.expectOne((r) => r.url.endsWith('/instruments'));
    expect(refresh.request.params.get('search')).toBe('SPY');
    refresh.flush({
      items: [{ ...item, instrumentName: 'Updated fund name' }],
      total: 1,
      page: 0,
      pageSize: 20,
    });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('table')).toBe(table);
    expect(fixture.nativeElement.textContent).toContain('Updated fund name');
    expect(fixture.nativeElement.textContent).toContain('NYSE Arca');
    fixture.destroy();
    http.verify();
  });
});

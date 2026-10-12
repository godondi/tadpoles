import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { vi } from 'vitest';
import { appConfig } from '../../app.config';
import { AuthService } from '../auth/auth.service';
import { MarketDataService } from './market-data.service';

describe('MarketDataService with application HTTP configuration', () => {
  let http: HttpTestingController;
  let service: MarketDataService;
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        ...appConfig.providers,
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { getAccessToken: () => 'test-session-token' } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    service = TestBed.inject(MarketDataService);
  });
  afterEach(() => {
    http.verify();
    vi.useRealTimers();
  });
  it('requests an inclusive UTC date range and authenticates the candle request', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-10-08T00:30:00Z'));
    service.getCandles(' aapl ').subscribe();
    const req = http.expectOne((r) => r.url.endsWith('/candles/AAPL'));
    expect(req.request.params.get('from')).toBe('2026-07-10');
    expect(req.request.params.get('to')).toBe('2026-10-08');
    expect(req.request.params.get('interval')).toBe('1d');
    expect(req.request.headers.get('Authorization')).toBe('Bearer test-session-token');
    req.flush({});
  });
  it('encodes special symbols into a single path segment and authenticates quotes', () => {
    service.getQuote('^gspc').subscribe();
    const req = http.expectOne((r) => r.url.endsWith('/quotes/%5EGSPC'));
    expect(req.request.method).toBe('GET');
    expect(req.request.headers.get('Authorization')).toBe('Bearer test-session-token');
    expect(req.request.params.keys()).toEqual([]);
    req.flush({});
  });
});

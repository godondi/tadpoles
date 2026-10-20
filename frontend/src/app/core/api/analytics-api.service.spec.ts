import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { AnalyticsApiService } from './analytics-api.service';
import { AnalyticsFilters } from './analytics-api.service';
import { AuthService } from '../auth/auth.service';
import { AuthInterceptor } from '../auth/auth.interceptor';
import { HTTP_INTERCEPTORS } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { Subject } from 'rxjs';

describe('AnalyticsApiService', () => {
  const filters: AnalyticsFilters = { from: '2026-10-01', to: '2026-10-02', timezone: 'UTC', granularity: 'day' };
  const auth = { getAccessToken: () => 'token', sessionChanged: new Subject<void>(), logout: vi.fn() };
  beforeEach(() => TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(withInterceptorsFromDi()), provideHttpClientTesting(), { provide: AuthService, useValue: auth }, { provide: HTTP_INTERCEPTORS, useClass: AuthInterceptor, multi: true }] }));
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('builds bounded query parameters for the analytics origin', () => {
    TestBed.inject(AnalyticsApiService).summary(filters).subscribe();
    const request = TestBed.inject(HttpTestingController).expectOne((candidate) => candidate.url.includes('/api/analytics/summary'));
    expect(request.request.url).toContain(':8090');
    expect(request.request.params.get('from')).toBe('2026-10-01');
    expect(request.request.params.get('to')).toBe('2026-10-02');
    expect(request.request.headers.get('Authorization')).toBe('Bearer token');
    request.flush({});
  });

  it('requests the portfolio visualization contracts with explicit filters', () => {
    const service = TestBed.inject(AnalyticsApiService);
    service.portfolioPerformance(filters, '12m').subscribe();
    service.portfolioHoldings(filters).subscribe();

    const performance = TestBed.inject(HttpTestingController).expectOne((candidate) => candidate.url.includes('/api/analytics/portfolio-performance'));
    expect(performance.request.params.get('period')).toBe('12m');
    expect(performance.request.params.get('timezone')).toBe('UTC');
    performance.flush({});

    const holdings = TestBed.inject(HttpTestingController).expectOne((candidate) => candidate.url.includes('/api/analytics/holdings'));
    expect(holdings.request.params.get('from')).toBe('2026-10-01');
    holdings.flush({});
  });
});

import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AdvisorApi } from './advisor-api.service';

describe('AdvisorApi', () => {
  let api: AdvisorApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), AdvisorApi],
    });
    api = TestBed.inject(AdvisorApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('rejects unsafe or non-positive numeric IDs before issuing requests', () => {
    for (const id of [0, -1, 1.5, Number.MAX_SAFE_INTEGER + 1]) {
      expect(() => api.portfolio(id)).toThrow('positive integers');
    }
    http.expectNone(() => true);
  });

  it.each([
    [400, 'invalid'], [401, 'expired'], [403, 'access'], [404, 'unavailable'],
    [409, 'changed'], [429, 'Too many'], [503, 'unavailable'],
  ])('preserves HTTP %s and supplies an actionable default', (status, message) => {
    let received: any;
    api.guidance(7).subscribe({ error: (error) => received = error });
    http.expectOne('/api/workspace/advisor/clients/7/guidance').flush({}, { status: Number(status), statusText: 'Failure' });
    expect(received.status).toBe(status);
    expect(received.error.message).toContain(message);
  });

  it('requests assigned clients with bounded pagination and search', () => {
    api.clients({ page: 1, pageSize: 25, search: 'Ada & Co' }).subscribe();

    const request = http.expectOne((candidate) => candidate.url.endsWith('/api/workspace/advisor/clients'));
    expect(request.request.params.get('page')).toBe('1');
    expect(request.request.params.get('pageSize')).toBe('25');
    expect(request.request.params.get('search')).toBe('Ada & Co');
    request.flush({ items: [], page: 1, pageSize: 25, total: 0 });
  });

  it('sends an expected guidance version without touching an order endpoint', () => {
    api.saveGuidance(7, 42, { status: 'SELL', reasoning: 'Review concentration.', expectedVersion: 3 }).subscribe();

    const request = http.expectOne(
      '/api/workspace/advisor/clients/7/guidance/42',
    );
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({
      status: 'SELL',
      reasoning: 'Review concentration.',
      expectedVersion: 3,
    });
    expect(request.request.url).not.toContain('/orders');
    request.flush({
      instrumentId: 42,
      status: 'SELL',
      reasoning: 'Review concentration.',
      authorName: 'Demo Advisor',
      version: 4,
      updatedAt: '2026-10-22T18:00:00Z',
    });
  });

  it('maps FastAPI-style detail errors to the shared message shape', () => {
    let received: { error?: { message?: string } } | undefined;
    api.guidance(7).subscribe({ error: (error) => (received = error) });

    const request = http.expectOne('/api/workspace/advisor/clients/7/guidance');
    request.flush({ detail: 'Guidance is unavailable.' }, { status: 503, statusText: 'Unavailable' });

    expect(received?.error?.message).toBe('Guidance is unavailable.');
  });
});

import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Subject } from 'rxjs';
import { OperationsApi } from './operations-api.service';
import { AuthService } from '../auth/auth.service';

describe('Operations purpose and evidence access', () => {
  let api: OperationsApi;
  let http: HttpTestingController;
  const sessionChanged = new Subject<void>();
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { sessionChanged } },
      ],
    });
    api = TestBed.inject(OperationsApi);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => {
    api.clear();
    http.verify();
  });
  function context() {
    api.createContext('ORDER_REVIEW', 'CASE-TEST-001').subscribe();
    const request = http.expectOne((r) => r.url.endsWith('/access-contexts'));
    expect(request.request.body).toEqual({
      purposeCode: 'ORDER_REVIEW',
      caseReference: 'CASE-TEST-001',
      referenceType: 'EXTERNAL',
    });
    request.flush({
      contextId: 'context-123',
      purposeCode: 'ORDER_REVIEW',
      expiresAt: new Date(Date.now() + 600000).toISOString(),
    });
  }
  it('sends only the opaque context in protected request headers and clears it on session change', () => {
    context();
    api.orders({ status: 'FILLED' }).subscribe();
    const request = http.expectOne((r) => r.url.endsWith('/orders'));
    expect(request.request.headers.get('X-Operations-Context')).toBe('context-123');
    expect(request.request.urlWithParams).not.toContain('CASE-TEST');
    request.flush({
      items: [],
      nextCursor: null,
      hasMore: false,
      retrievedAt: new Date().toISOString(),
    });
    sessionChanged.next();
    expect(api.context()).toBeNull();
  });
  it('clears purpose on revoked access and retains it on transport failure', () => {
    context();
    api.detail(1).subscribe({ error: () => {} });
    http
      .expectOne((r) => r.url.endsWith('/orders/1'))
      .flush({}, { status: 503, statusText: 'Unavailable' });
    expect(api.context()).not.toBeNull();
    api.detail(1).subscribe({ error: () => {} });
    http
      .expectOne((r) => r.url.endsWith('/orders/1'))
      .flush({}, { status: 403, statusText: 'Forbidden' });
    expect(api.context()).toBeNull();
    expect(api.contextMessage()).toContain('cleared');
  });
  it('does not restore a context when its response arrives after a session change', () => {
    let received = false;
    api.createContext('ORDER_REVIEW', 'CASE-TEST-002').subscribe(() => (received = true));
    const request = http.expectOne((r) => r.url.endsWith('/access-contexts'));
    sessionChanged.next();
    request.flush({
      contextId: 'late-context',
      purposeCode: 'ORDER_REVIEW',
      expiresAt: new Date(Date.now() + 600000).toISOString(),
    });
    expect(received).toBe(false);
    expect(api.context()).toBeNull();
  });
  it('expires in-memory contexts without waiting for polling', () => {
    vi.useFakeTimers();
    context();
    vi.advanceTimersByTime(600001);
    expect(api.context()).toBeNull();
    vi.useRealTimers();
  });
  it('downloads with authenticated headers and no token in the URL', () => {
    context();
    api.download('artifact-1').subscribe();
    const request = http.expectOne((r) => r.url.endsWith('/evidence-exports/artifact-1/download'));
    expect(request.request.headers.get('X-Operations-Context')).toBe('context-123');
    expect(request.request.responseType).toBe('blob');
    request.flush(new Blob(['zip']));
  });
});

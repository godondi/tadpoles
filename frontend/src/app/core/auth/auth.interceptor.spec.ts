import { PLATFORM_ID } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import {
  HTTP_INTERCEPTORS,
  provideHttpClient,
  withInterceptorsFromDi,
  HttpClient,
} from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { Subject } from 'rxjs';
import { AuthInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';
import { buildApiUrl } from '../api/api-url';
describe('credential boundary', () => {
  const auth = {
    getAccessToken: () => 'token',
    sessionChanged: new Subject<void>(),
    logout: vi.fn(),
  };
  beforeEach(() => {
    auth.logout.mockClear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: auth },
        { provide: HTTP_INTERCEPTORS, useClass: AuthInterceptor, multi: true },
      ],
    });
  });
  afterEach(() => TestBed.inject(HttpTestingController).verify());
  it('never attaches credentials to another origin', () => {
    TestBed.inject(HttpClient).get('https://external.test/api').subscribe();
    const req = TestBed.inject(HttpTestingController).expectOne('https://external.test/api');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
  });
  it('attaches only intended backend credentials and keeps a valid 403 session', () => {
    const url = buildApiUrl('/api/orders', TestBed.inject(PLATFORM_ID));
    TestBed.inject(HttpClient)
      .get(url)
      .subscribe({ error: () => {} });
    const req = TestBed.inject(HttpTestingController).expectOne(url);
    expect(req.request.headers.get('Authorization')).toBe('Bearer token');
    req.flush({ message: 'Denied' }, { status: 403, statusText: 'Forbidden' });
    expect(auth.logout).not.toHaveBeenCalled();
  });
});

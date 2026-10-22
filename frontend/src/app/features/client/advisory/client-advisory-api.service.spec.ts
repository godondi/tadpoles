import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ClientAdvisoryApi } from './client-advisory-api.service';

describe('ClientAdvisoryApi', () => {
  let api: ClientAdvisoryApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), ClientAdvisoryApi],
    });
    api = TestBed.inject(ClientAdvisoryApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('uses session-owned assignment and advisory paths without a client ID', () => {
    api.assignment().subscribe();
    api.advisory().subscribe();

    expect(http.expectOne('/api/workspace/client/advisor').request.method).toBe('GET');
    expect(http.expectOne('/api/workspace/client/advisory').request.method).toBe('GET');
  });
});

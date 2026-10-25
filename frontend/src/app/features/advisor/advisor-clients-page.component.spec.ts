import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { Subject } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { AdvisorClientsPageComponent } from './advisor-clients-page.component';

describe('AdvisorClientsPageComponent', () => {
  function setup() {
    const sessionChanged = new Subject<void>();
    TestBed.configureTestingModule({
      imports: [AdvisorClientsPageComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: { sessionChanged } },
      ],
    });
    const fixture = TestBed.createComponent(AdvisorClientsPageComponent);
    const http = TestBed.inject(HttpTestingController);
    const request = http.expectOne((candidate) => candidate.url.endsWith('/api/workspace/advisor/clients'));
    return { fixture, http, request, sessionChanged };
  }

  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('clears a sensitive search draft when the verified session changes', () => {
    const { fixture, request, sessionChanged } = setup();
    request.flush({ items: [], page: 0, pageSize: 20, total: 0 });
    fixture.componentInstance.search = 'Private client search';
    sessionChanged.next();
    expect(fixture.componentInstance.search).toBe('');
    expect(fixture.componentInstance.resource.data()).toBeNull();
  });

  it.each([401, 403, 404, 429, 503, 0])('never treats HTTP %s as no assigned clients', (status) => {
    const { fixture, request } = setup();
    if (status === 0) request.error(new ProgressEvent('error'));
    else request.flush({ message: 'Assigned clients unavailable' }, { status, statusText: 'Failure' });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).not.toContain('No clients are assigned');
    expect(fixture.nativeElement.querySelector('[role="alert"]')).not.toBeNull();
  });

  it('searches with bounded pagination and cancels an obsolete search', () => {
    const { fixture, http, request } = setup();
    request.flush({ items: [], page: 0, pageSize: 20, total: 0 });
    fixture.componentInstance.search = ' Demo ';
    fixture.componentInstance.searchClients();
    const old = http.expectOne((r) => r.params.get('search') === 'Demo');
    fixture.componentInstance.search = 'Next';
    fixture.componentInstance.searchClients();
    expect(old.cancelled).toBe(true);
    const next = http.expectOne((r) => r.params.get('search') === 'Next');
    expect(next.request.params.get('page')).toBe('0');
    expect(next.request.params.get('pageSize')).toBe('20');
    next.flush({ items: [], page: 0, pageSize: 20, total: 0 });
  });

  it('renders assigned clients with stable detail links and updated time', () => {
    const { fixture, request } = setup();
    request.flush({
      items: [
        {
          clientId: 7,
          displayName: 'Demo Client',
          clientReference: 'CLIENT-7',
          assignmentStatus: 'ASSIGNED',
          updatedAt: '2026-10-22T18:00:00Z',
        },
      ],
      page: 0,
      pageSize: 20,
      total: 1,
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Demo Client');
    expect(fixture.nativeElement.textContent).toContain('CLIENT-7');
    expect(fixture.nativeElement.textContent).toContain('ASSIGNED');
    expect(fixture.nativeElement.querySelector('a[href="/advisor/clients/7"]')).not.toBeNull();
  });

  it('distinguishes a successful empty list from an unavailable request', () => {
    const { fixture, request } = setup();
    request.flush({ items: [], page: 0, pageSize: 20, total: 0 });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('No clients are assigned');
    expect(fixture.nativeElement.textContent).not.toContain('Unable to connect');
  });
});

import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { Subject, of } from 'rxjs';
import { routes } from '../../app.routes';
import { authGuard, workspaceGuard } from '../../core/auth/auth.guard';
import { AuthService } from '../../core/auth/auth.service';
import { AdvisorApi } from './advisor-api.service';
import { AdvisorClientsPageComponent } from './advisor-clients-page.component';
import { AdvisorClientDetailPageComponent } from './advisor-client-detail-page.component';

@Component({ template: '<h1>Outside Advisor workspace</h1>' })
class OutsideWorkspace {}

describe('Advisor route integration with existing guards', () => {
  function setup(roles = ['ADVISOR'], authenticated = true) {
    const sessionChanged = new Subject<void>();
    const auth = { roles: signal(roles), status: signal(authenticated ? 'authenticated' : 'unauthenticated'), ensureInitialized: vi.fn(async () => authenticated), sessionChanged };
    // Use the real lazy Advisor entries; isolate unrelated pages and shell rendering.
    const advisorRoutes = routes.find((r) => !!r.component)?.children?.find((r) => !!r.canActivateChild)?.children?.filter((r) => r.path?.startsWith('advisor/')) ?? [];
    TestBed.configureTestingModule({ providers: [
      provideRouter([
        { path: '', canActivate: [authGuard], canActivateChild: [workspaceGuard], children: advisorRoutes },
        { path: 'login', component: OutsideWorkspace },
        { path: 'unauthorized', component: OutsideWorkspace },
      ]),
      { provide: AuthService, useValue: auth },
      { provide: AdvisorApi, useValue: {
        clients: () => of({ items: [], page: 0, pageSize: 20, total: 0 }),
        portfolio: (clientId: number) => of({ clientId, clientName: 'Fixture Client', clientReference: 'TEST', currentPositions: [], retrievedAt: '2026-10-22T18:00:00Z' }),
        guidance: () => of({ items: [], asOf: null }),
        recommendations: () => of({ items: [], asOf: null }),
        instruments: () => of({ items: [], page: 0, pageSize: 100, total: 0 }),
      } },
    ] });
    return auth;
  }

  it('opens the exact lazy home and detail deep links', async () => {
    setup();
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/advisor/clients/7', AdvisorClientDetailPageComponent);
    expect(harness.routeNativeElement?.textContent).toContain('Fixture Client');
    await harness.navigateByUrl('/advisor/clients', AdvisorClientsPageComponent);
    expect(harness.routeNativeElement?.textContent).toContain('No clients are assigned');
  });

  it.each(['0', '-1', '1.5', 'abc'])('denies invalid detail ID %s', async (id) => {
    setup();
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(`/advisor/clients/${id}`, OutsideWorkspace);
    expect(TestBed.inject(Router).url).toBe('/unauthorized');
  });

  it('denies a client-only session and an unauthenticated deep link', async () => {
    const auth = setup(['CLIENT']);
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/advisor/clients', OutsideWorkspace);
    expect(TestBed.inject(Router).url).toBe('/unauthorized');
    auth.ensureInitialized.mockResolvedValue(false);
    await harness.navigateByUrl('/advisor/clients/7', OutsideWorkspace);
    expect(TestBed.inject(Router).url).toContain('/login');
  });

  it('clears protected detail on logout then loads the new route after verified relogin', async () => {
    const auth = setup();
    const harness = await RouterTestingHarness.create();
    const page = await harness.navigateByUrl('/advisor/clients/7', AdvisorClientDetailPageComponent);
    auth.sessionChanged.next();
    expect(page.portfolio.data()).toBeNull();
    auth.ensureInitialized.mockResolvedValue(false);
    await harness.navigateByUrl('/advisor/clients', OutsideWorkspace);
    auth.ensureInitialized.mockResolvedValue(true);
    await harness.navigateByUrl('/advisor/clients/8', AdvisorClientDetailPageComponent);
    expect(harness.routeNativeElement?.textContent).toContain('Fixture Client');
  });
});
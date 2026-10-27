import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { vi } from 'vitest';
import { AuthService } from './core/auth/auth.service';
import { App } from './app';
import { routes } from './app.routes';

describe('App routing and shell', () => {
  let getContextSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(async () => {
    localStorage.clear();
    vi.stubGlobal(
      'ResizeObserver',
      class {
        observe() {}
        disconnect() {}
      },
    );
    getContextSpy = vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(null);

    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
  });

  afterEach(() => {
    localStorage.clear();
    getContextSpy.mockRestore();
    vi.unstubAllGlobals();
  });

  async function renderAt(
    url: string,
    session?: { onboardingComplete: boolean; roles?: string[] },
  ) {
    const router = TestBed.inject(Router);
    const roles = session?.roles ?? ['CLIENT'];

    if (session) {
      localStorage.setItem(
        'tadpoles.auth.session',
        JSON.stringify({
          token: 'stored-token',
          tokenType: 'Bearer',
          expiresIn: 3600,
          expiresAt: Date.now() + 3600000,
          userId: 14,
          email: 'casey@example.com',
          displayName: 'Casey Example',
          clientId: roles.includes('CLIENT') && session.onboardingComplete ? 7 : null,
          onboardingComplete: session.onboardingComplete,
        }),
      );
      localStorage.setItem('tadpoles.auth.token', 'stored-token');
      localStorage.setItem(
        'tadpoles.user.profile',
        JSON.stringify({
          userId: 14,
          clientId: roles.includes('CLIENT') && session.onboardingComplete ? 7 : null,
          email: 'casey@example.com',
          displayName: 'Casey Example',
          clientName: '',
          phone: '',
          dateOfBirth: '',
          addressLine1: '',
          addressLine2: '',
          city: '',
          state: '',
          postalCode: '',
          country: '',
          employmentStatus: '',
          netWorth: null,
          riskTolerance: '',
          investmentObjective: '',
          preferredContactMethod: '',
          paperlessStatements: true,
          marketingOptIn: false,
          onboardingComplete: session.onboardingComplete,
        }),
      );
    }

    if (session) {
      const auth = TestBed.inject(AuthService);
      const http = TestBed.inject(HttpTestingController);
      const init = auth.ensureInitialized();
      http
        .expectOne((r) => r.url.endsWith('/api/users/me'))
        .flush({
          userId: 14,
          enabled: true,
          roles,
          clientId: roles.includes('CLIENT') && session.onboardingComplete ? 7 : null,
          email: 'casey@example.com',
          displayName: 'Casey',
        });
      await init;
      if (roles.includes('CLIENT')) {
        const profile = auth.ensureClientProfile();
        http
          .expectOne((r) => r.url.endsWith('/api/users/me/profile'))
          .flush({
            userId: 14,
            clientId: session.onboardingComplete ? 7 : null,
            email: 'casey@example.com',
            displayName: 'Casey',
            onboardingComplete: session.onboardingComplete,
          });
        await profile;
      }
    }
    const fixture = TestBed.createComponent(App);
    await router.navigateByUrl(url);
    fixture.detectChanges();
    const access = TestBed.inject(HttpTestingController).match((r) =>
      r.url.endsWith('/api/workspace/client/trade-access'),
    );
    access.forEach((request) => request.flush({ revoked: false, revokedUntil: '' }));
    await fixture.whenStable();
    fixture.detectChanges();

    return { fixture, router };
  }

  it('redirects unauthenticated visitors from the dashboard to login', async () => {
    const { fixture, router } = await renderAt('/');

    expect(router.url).toBe('/login');
    expect(fixture.nativeElement.textContent).toContain('Sign In');
  });

  it('renders the authenticated shell for signed-in visitors', async () => {
    const { fixture, router } = await renderAt('/', { onboardingComplete: true });
    const compiled = fixture.nativeElement as HTMLElement;
    const shellText = compiled.querySelector('app-top-nav')?.textContent ?? '';

    expect(router.url).toBe('/app/overview');
    expect(compiled.querySelector('app-top-nav')).toBeTruthy();
    expect(compiled.querySelector('app-top-nav .brand')?.getAttribute('aria-label')).toBe(
      'Tadpoles dashboard',
    );
    expect(shellText).toContain('Search');
    expect(shellText).toContain('Account');
    expect(shellText).not.toContain('Trades');
  });

  it('redirects authenticated visitors away from login', async () => {
    const { router } = await renderAt('/login', { onboardingComplete: true });

    expect(router.url).toBe('/app/overview');
  });

  it('redirects authenticated users with incomplete onboarding to the onboarding route', async () => {
    const { router } = await renderAt('/', { onboardingComplete: false });

    expect(router.url).toBe('/onboarding');
  });

  it('keeps the shared header on the instrument route for signed-in users', async () => {
    const { fixture, router } = await renderAt('/app/instruments/11', { onboardingComplete: true });
    expect(router.url).toBe('/app/instruments/11');
    expect(fixture.nativeElement.querySelector('app-top-nav')).toBeTruthy();
    const http = TestBed.inject(HttpTestingController);
    http
      .expectOne((r) => r.url.endsWith('/api/instruments/11'))
      .flush({
        instrumentId: 11,
        ticker: 'AAPL',
        instrumentName: 'Apple',
        currency: 'USD',
        isActive: true,
      });
    http
      .expectOne((r) => r.url.endsWith('/trading-availability'))
      .flush({ enabled: false, reason: 'Not enabled' });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('app-trade')).toBeTruthy();
    http
      .expectOne((r) => r.url.endsWith('/candles/AAPL'))
      .flush({ data: { candles: [] }, meta: {} });
    http
      .expectOne((r) => r.url.endsWith('/quotes/AAPL'))
      .flush({}, { status: 404, statusText: 'Not Found' });
    http.verify();
  });

  it('preserves the instrument URL when redirecting a guest to login', async () => {
    const { router } = await renderAt('/trade/AAPL');
    expect(router.url).toContain('/login');
    expect(router.parseUrl(router.url).queryParams['redirectTo']).toBe('/trade/AAPL');
  });

  it('keeps latest reconciliation available when Finance summary is unavailable', async () => {
    const { fixture, router } = await renderAt('/finance/overview', {
      onboardingComplete: true,
      roles: ['FINANCE'],
    });
    const http = TestBed.inject(HttpTestingController);
    const summary = http.expectOne(
      (request) => request.method === 'GET' && request.url.endsWith('/api/finance/summary'),
    );
    summary.flush(
      { message: 'Period totals are not available yet.' },
      { status: 503, statusText: 'Service Unavailable' },
    );

    const reconciliation = http.expectOne(
      (request) => request.method === 'GET' && request.url.endsWith('/api/finance/reconciliations'),
    );
    reconciliation.flush({ runId: null, message: 'No stored run yet.' });
    fixture.detectChanges();

    expect(router.url).toBe('/finance/overview');
    expect(fixture.nativeElement.textContent).toContain('Finance overview');
    expect(fixture.nativeElement.textContent).toContain('No stored run yet.');
    expect(fixture.nativeElement.textContent).toContain('unavailable');
    http.verify();
  });

  it('renders the dedicated Finance reconciliation route without replacing shared consumers', async () => {
    const { fixture, router } = await renderAt('/finance/reconciliation', {
      onboardingComplete: true,
      roles: ['FINANCE'],
    });
    const http = TestBed.inject(HttpTestingController);
    http
      .expectOne((request) => request.method === 'GET' && request.url.endsWith('/reconciliations'))
      .flush({
        runId: null,
        message: 'Run a reconciliation to inspect a consistent snapshot.',
      });
    fixture.detectChanges();
    expect(router.url).toBe('/finance/reconciliation');
    expect(fixture.nativeElement.querySelector('app-finance-reconciliation')).toBeTruthy();
    expect(fixture.nativeElement.textContent).toContain('No reconciliation run is stored');
  });

  it.each(['CLIENT', 'ANALYST', 'ADVISOR', 'COMPLIANCE'])(
    'denies direct Finance navigation for %s-only identities',
    async (role) => {
      const { router, fixture } = await renderAt('/finance/overview', {
        onboardingComplete: true,
        roles: [role],
      });
      expect(router.url).toBe('/unauthorized');
      expect(fixture.nativeElement.textContent).toContain('Access denied');
      TestBed.inject(HttpTestingController).expectNone((request) =>
        request.url.includes('/api/finance/'),
      );
    },
  );

  it('keeps mixed-role Finance denial fail-closed at the backend API', async () => {
    const { fixture, router } = await renderAt('/finance/overview', {
      onboardingComplete: true,
      roles: ['CLIENT', 'FINANCE'],
    });
    const http = TestBed.inject(HttpTestingController);
    const reconciliation = http.expectOne((request) =>
      request.url.endsWith('/api/finance/reconciliations'),
    );
    http
      .expectOne((request) => request.url.endsWith('/api/finance/summary'))
      .flush({ message: 'Access denied' }, { status: 403, statusText: 'Forbidden' });
    expect(reconciliation.cancelled).toBe(true);
    fixture.detectChanges();
    expect(router.url).toBe('/finance/overview');
    expect(fixture.nativeElement.textContent).toContain('Access denied');
    expect(fixture.nativeElement.textContent).not.toContain('No stored run');
    http.verify({ ignoreCancelled: true });
  });
});

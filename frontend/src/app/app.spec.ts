import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { App } from './app';
import { routes } from './app.routes';
import { AuthService } from './core/auth/auth.service';
import { WorkspaceRole } from './core/auth/workspace-roles';

class MockAuthService {
  private readonly authenticated = signal(false);
  private readonly onboarding = signal(false);
  private readonly roleState = signal<WorkspaceRole[]>([]);
  readonly isAuthenticated = this.authenticated.asReadonly();
  readonly hasCompletedOnboarding = this.onboarding.asReadonly();
  readonly roles = this.roleState.asReadonly();
  readonly clientId = signal<number | null>(null).asReadonly();

  async ensureInitialized(): Promise<void> {
    return Promise.resolve();
  }

  setState(authenticated: boolean, onboardingComplete: boolean, roles: WorkspaceRole[]): void {
    this.authenticated.set(authenticated);
    this.onboarding.set(onboardingComplete);
    this.roleState.set(roles);
  }

  hasAnyRole(requiredRoles: WorkspaceRole[]): boolean {
    return requiredRoles.some((role) => this.roles().includes(role));
  }

  resolveLandingRoute(): string {
    if (this.roles().includes('CLIENT')) return '/app/overview';
    if (this.roles().includes('TRADING_OPS')) return '/operations/orders';
    if (this.roles().includes('COMPLIANCE')) return '/compliance/investigations';
    if (this.roles().includes('ANALYST')) return '/analytics/overview';
    if (this.roles().includes('FINANCE')) return '/finance/overview';
    return '/unauthorized';
  }

  logout(): void {
    this.setState(false, false, []);
  }
}

describe('App routing and shell', () => {
  let getContextSpy: ReturnType<typeof vi.spyOn>;
  let mockAuthService: MockAuthService;

  beforeEach(async () => {
    localStorage.clear();
    getContextSpy = vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(null);
    mockAuthService = new MockAuthService();

    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter(routes),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: mockAuthService },
      ],
    }).compileComponents();
  });

  afterEach(() => {
    localStorage.clear();
    getContextSpy.mockRestore();
  });

  async function renderAt(url: string, state?: { authenticated: boolean; onboardingComplete: boolean; roles: WorkspaceRole[] }) {
    const router = TestBed.inject(Router);
    if (state) {
      mockAuthService.setState(state.authenticated, state.onboardingComplete, state.roles);
    } else {
      mockAuthService.setState(false, false, []);
    }

    const fixture = TestBed.createComponent(App);
    await router.navigateByUrl(url);
    fixture.detectChanges();
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
    const { fixture, router } = await renderAt('/', {
      authenticated: true,
      onboardingComplete: true,
      roles: ['CLIENT'],
    });
    const compiled = fixture.nativeElement as HTMLElement;
    const shellText = compiled.querySelector('app-top-nav')?.textContent ?? '';

    expect(router.url).toBe('/app/overview');
    expect(compiled.querySelector('app-top-nav')).toBeTruthy();
    expect(shellText).toContain('Tadpoles');
    expect(shellText).toContain('Search');
    expect(shellText).toContain('Account');
  });

  it('redirects authenticated visitors away from login', async () => {
    const { router } = await renderAt('/login', {
      authenticated: true,
      onboardingComplete: true,
      roles: ['CLIENT'],
    });
    expect(router.url).toBe('/app/overview');
  });

  it('redirects authenticated users with incomplete onboarding to the onboarding route', async () => {
    const { router } = await renderAt('/app/overview', {
      authenticated: true,
      onboardingComplete: false,
      roles: ['CLIENT'],
    });
    expect(router.url).toBe('/onboarding');
  });

  it('keeps the shared header on the instrument route for signed-in users', async () => {
    const { fixture, router } = await renderAt('/trade/AAPL', {
      authenticated: true,
      onboardingComplete: true,
      roles: ['CLIENT'],
    });
    expect(router.url).toBe('/trade/AAPL');
    expect(fixture.nativeElement.querySelector('app-top-nav')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('app-trade')).toBeTruthy();
    const http = TestBed.inject(HttpTestingController);
    http.expectOne((r) => r.url.endsWith('/candles/AAPL')).flush({ data: { candles: [] }, meta: {} });
    http.expectOne((r) => r.url.endsWith('/quotes/AAPL')).flush({}, { status: 404, statusText: 'Not Found' });
    http.verify();
  });

  it('preserves the instrument URL when redirecting a guest to login', async () => {
    const { router } = await renderAt('/trade/AAPL');
    expect(router.url).toContain('/login');
    expect(router.parseUrl(router.url).queryParams['redirectTo']).toBe('/trade/AAPL');
  });
});

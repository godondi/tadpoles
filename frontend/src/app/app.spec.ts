import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { vi } from 'vitest';
import { App } from './app';
import { routes } from './app.routes';

describe('App routing and shell', () => {
  let getContextSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(async () => {
    localStorage.clear();
    getContextSpy = vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(null);

    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
  });

  afterEach(() => {
    localStorage.clear();
    getContextSpy.mockRestore();
  });

  async function renderAt(url: string, session?: { onboardingComplete: boolean }) {
    const router = TestBed.inject(Router);

    if (session) {
      localStorage.setItem('tadpoles.auth.session', JSON.stringify({
        token: 'stored-token',
        tokenType: 'Bearer',
        expiresIn: 3600,
        userId: 14,
        email: 'casey@example.com',
        displayName: 'Casey Example',
        clientId: session.onboardingComplete ? 7 : null,
        onboardingComplete: session.onboardingComplete,
      }));
      localStorage.setItem('tadpoles.auth.token', 'stored-token');
      localStorage.setItem('tadpoles.user.profile', JSON.stringify({
        userId: 14,
        clientId: session.onboardingComplete ? 7 : null,
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
      }));
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
    const { fixture, router } = await renderAt('/', { onboardingComplete: true });
    const compiled = fixture.nativeElement as HTMLElement;
    const shellText = compiled.querySelector('app-top-nav')?.textContent ?? '';

    expect(router.url).toBe('/');
    expect(compiled.querySelector('app-top-nav')).toBeTruthy();
    expect(shellText).toContain('Tadpoles');
    expect(shellText).toContain('Search');
    expect(shellText).toContain('Account');
    expect(shellText).not.toContain('Trades');
  });

  it('redirects authenticated visitors away from login', async () => {
    const { router } = await renderAt('/login', { onboardingComplete: true });

    expect(router.url).toBe('/');
  });

  it('redirects authenticated users with incomplete onboarding to the onboarding route', async () => {
    const { router } = await renderAt('/', { onboardingComplete: false });

    expect(router.url).toBe('/onboarding');
  });
});

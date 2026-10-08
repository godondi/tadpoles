import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { vi } from 'vitest';
import { App } from './app';
import { routes } from './app.routes';
import { AuthService } from './core/auth/auth.service';

describe('App routing and shell', () => {
  let getContextSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(async () => {
    localStorage.clear();
    getContextSpy = vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(null);

    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter(routes)],
    }).compileComponents();
  });

  afterEach(() => {
    localStorage.clear();
    getContextSpy.mockRestore();
  });

  async function renderAt(url: string, authenticated = false) {
    const authService = TestBed.inject(AuthService);
    const router = TestBed.inject(Router);

    if (authenticated) {
      authService.login('casey@example.com');
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
    const { fixture, router } = await renderAt('/', true);
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
    const { router } = await renderAt('/login', true);

    expect(router.url).toBe('/');
  });
});

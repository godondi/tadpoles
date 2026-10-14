import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { AuthService } from './auth.service';
import { authGuard, workspaceGuard, clientGuard } from './auth.guard';
@Component({ template: '<h1>Workspace</h1>' })
class Workspace {}
@Component({ template: '<h1>Denied</h1>' })
class Denied {}
describe('workspace router guards', () => {
  let auth: any;
  beforeEach(() => {
    auth = {
      roles: signal(['FINANCE']),
      isAuthenticated: () => true,
      status: () => 'authenticated',
      ensureInitialized: vi.fn().mockResolvedValue(true),
      ensureClientProfile: vi.fn().mockResolvedValue(true),
      hasCompletedOnboarding: () => false,
    };
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: auth },
        provideRouter([
          {
            path: '',
            canActivate: [authGuard],
            canActivateChild: [workspaceGuard],
            children: [
              { path: 'finance/overview', component: Workspace },
              { path: 'app/overview', component: Workspace, canActivate: [clientGuard] },
            ],
          },
          { path: 'unauthorized', component: Denied },
          { path: 'login', component: Denied },
          { path: 'onboarding', component: Denied },
        ]),
      ],
    });
  });
  it('allows staff deep link without client profile', async () => {
    const h = await RouterTestingHarness.create('/finance/overview');
    expect(h.routeNativeElement?.textContent).toContain('Workspace');
    expect(auth.ensureClientProfile).not.toHaveBeenCalled();
  });
  it('checks a forbidden child after shell already mounted', async () => {
    const h = await RouterTestingHarness.create('/finance/overview');
    await h.navigateByUrl('/app/overview');
    expect(TestBed.inject(Router).url).toBe('/unauthorized');
  });
  it('mixed role enters staff without onboarding but client must complete onboarding', async () => {
    auth.roles.set(['CLIENT', 'FINANCE']);
    const h = await RouterTestingHarness.create('/finance/overview');
    expect(auth.ensureClientProfile).not.toHaveBeenCalled();
    await h.navigateByUrl('/app/overview');
    expect(TestBed.inject(Router).url).toBe('/onboarding');
  });
  it('awaits restored identity before protected route renders', async () => {
    let resolve!: (value: boolean) => void;
    auth.ensureInitialized.mockImplementation(() => new Promise<boolean>((r) => (resolve = r)));
    const h = await RouterTestingHarness.create();
    const navigation = h.navigateByUrl('/finance/overview');
    await new Promise((r) => setTimeout(r, 0));
    expect(h.routeNativeElement?.textContent ?? '').not.toContain('Workspace');
    auth.ensureInitialized.mockResolvedValue(true);
    resolve(true);
    await navigation;
    expect(h.routeNativeElement?.textContent).toContain('Workspace');
  });
});

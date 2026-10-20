import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { AuthenticatedShellComponent } from './authenticated-shell.component';
import { AuthService } from '../core/auth/auth.service';
import { UserService } from '../user.service';

// Browser checks exercise actual sticky positioning; this verifies the shared navigation
// and that the offset follows the rendered header instead of a fixed role-specific height.
describe('shared workspace navigation', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('exposes the Add route to Operations, hides management for mixed client roles, and observes header height', async () => {
    let resized = () => {};
    const disconnect = vi.fn();
    vi.stubGlobal(
      'ResizeObserver',
      class {
        constructor(callback: () => void) {
          resized = callback;
        }
        observe() {}
        disconnect = disconnect;
      },
    );
    const roles = signal(['TRADING_OPS']);
    TestBed.configureTestingModule({
      imports: [AuthenticatedShellComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { roles, isAuthenticated: signal(true) } },
        {
          provide: UserService,
          useValue: {
            getProfile: () => signal({ displayName: 'Test operator', email: 'test@example.test' }),
          },
        },
      ],
    });
    const fixture = TestBed.createComponent(AuthenticatedShellComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    expect(
      fixture.nativeElement.querySelector('a[href="/operations/instruments/new"]'),
    ).not.toBeNull();
    const header = fixture.nativeElement.querySelector('app-top-nav');
    vi.spyOn(header, 'getBoundingClientRect').mockReturnValue({ height: 192 } as DOMRect);
    resized();
    fixture.detectChanges();
    expect(fixture.nativeElement.style.getPropertyValue('--workspace-header-height')).toBe('192px');
    roles.set(['TRADING_OPS', 'CLIENT']);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('a[href="/operations/instruments/new"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('a[href="/operations/instruments"]')).toBeNull();
    fixture.destroy();
    expect(disconnect).toHaveBeenCalled();
  });
});

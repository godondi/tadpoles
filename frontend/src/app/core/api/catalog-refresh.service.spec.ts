import { TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';
import { CatalogRefreshService } from './catalog-refresh.service';
import { AuthService } from '../auth/auth.service';

describe('visible catalog refresh', () => {
  afterEach(() => vi.useRealTimers());

  it('refreshes on a timer and focus, pauses when hidden, and stops on session change', () => {
    vi.useFakeTimers();
    const sessionChanged = new Subject<void>();
    TestBed.configureTestingModule({
      providers: [{ provide: AuthService, useValue: { sessionChanged } }],
    });
    const visibility = vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('visible');
    const refresh = vi.fn();
    const subscription = TestBed.inject(CatalogRefreshService).watch().subscribe(refresh);
    vi.advanceTimersByTime(15000);
    expect(refresh).toHaveBeenCalledTimes(1);
    visibility.mockReturnValue('hidden');
    vi.advanceTimersByTime(15000);
    window.dispatchEvent(new Event('focus'));
    expect(refresh).toHaveBeenCalledTimes(1);
    visibility.mockReturnValue('visible');
    document.dispatchEvent(new Event('visibilitychange'));
    window.dispatchEvent(new Event('focus'));
    expect(refresh).toHaveBeenCalledTimes(3);
    sessionChanged.next();
    vi.advanceTimersByTime(30000);
    window.dispatchEvent(new Event('focus'));
    expect(refresh).toHaveBeenCalledTimes(3);
    expect(subscription.closed).toBe(true);
    visibility.mockRestore();
  });
});

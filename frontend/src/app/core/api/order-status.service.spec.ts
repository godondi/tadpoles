import { TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import { OrderStatusService } from './order-status.service';
import { WorkspaceApi } from './workspace-api.service';
import { AuthService } from '../auth/auth.service';
describe('resumable order status polling', () => {
  let changed: Subject<void>;
  const changes = vi.fn();
  beforeEach(() => {
    vi.useFakeTimers();
    changes.mockReset();
    changed = new Subject<void>();
    TestBed.configureTestingModule({
      providers: [
        { provide: WorkspaceApi, useValue: { changes } },
        { provide: AuthService, useValue: { sessionChanged: changed } },
      ],
    });
  });
  afterEach(() => vi.useRealTimers());
  it('converges after disconnect and does not notify again for duplicate versions', () => {
    changes
      .mockReturnValueOnce(throwError(() => new Error('offline')))
      .mockReturnValueOnce(
        of({ items: [{ orderId: 1, version: 3, cursor: 1 }], cursor: 1, reset: false }),
      )
      .mockReturnValue(of({ items: [], cursor: 1, reset: false }));
    const notify = vi.fn();
    const sub = TestBed.inject(OrderStatusService).watch().subscribe(notify);
    vi.advanceTimersByTime(9000);
    expect(notify).toHaveBeenCalledTimes(1);
    expect(changes).toHaveBeenLastCalledWith(1);
    sub.unsubscribe();
  });
  it('requests a full reload on a reset and stops on identity change', () => {
    changes.mockReturnValue(of({ items: [], cursor: 0, reset: true }));
    const notify = vi.fn();
    TestBed.inject(OrderStatusService).watch().subscribe(notify);
    vi.advanceTimersByTime(3000);
    expect(notify).toHaveBeenCalledTimes(1);
    changed.next();
    vi.advanceTimersByTime(9000);
    expect(changes).toHaveBeenCalledTimes(1);
  });
});

it('shows failed and exhausted polling, resumes explicitly, and clears health on identity changes', () => {
  vi.useFakeTimers();
  try {
    const changed = new Subject<void>();
    const changes = vi.fn().mockReturnValue(throwError(() => new Error('offline')));
    TestBed.configureTestingModule({
      providers: [
        { provide: WorkspaceApi, useValue: { changes } },
        { provide: AuthService, useValue: { sessionChanged: changed } },
      ],
    });
    const service = TestBed.inject(OrderStatusService);
    // Read the prospective public health contract without making an absent property a compile failure.
    const health = () => (service as unknown as { health?: () => string }).health?.();
    const success = () =>
      (service as unknown as { lastSuccess?: () => string | null }).lastSuccess?.();
    service.watch().subscribe();
    vi.advanceTimersByTime(3000);
    expect(health()).toBe('STALE');
    changes.mockReturnValue(of({ items: [], cursor: 0, reset: false }));
    vi.advanceTimersByTime(3000);
    expect(health()).toBe('LIVE');
    expect(success()).toBeTruthy();
    vi.advanceTimersByTime(600000);
    expect(health()).toBe('EXHAUSTED');
    const count = changes.mock.calls.length;
    (service as unknown as { resume?: () => void }).resume?.();
    vi.advanceTimersByTime(3000);
    expect(changes.mock.calls.length).toBe(count + 1);
    expect(health()).toBe('LIVE');
    changed.next();
    expect(success()).toBeNull();
    vi.advanceTimersByTime(3000);
    expect(changes.mock.calls.length).toBe(count + 1);
  } finally {
    vi.useRealTimers();
  }
});

import { Injectable, inject, signal } from '@angular/core';
import {
  EMPTY,
  Subject,
  defer,
  exhaustMap,
  catchError,
  filter,
  map,
  take,
  takeUntil,
  timer,
  startWith,
  switchMap,
  tap,
} from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { WorkspaceApi } from './workspace-api.service';
/** Bounded authenticated polling; reconnect is independent of a manual receipt refresh. */
@Injectable({ providedIn: 'root' })
export class OrderStatusService {
  private readonly api = inject(WorkspaceApi);
  private readonly auth = inject(AuthService);
  private readonly reconnect = new Subject<void>();
  readonly health = signal<'IDLE' | 'CONNECTING' | 'LIVE' | 'STALE' | 'EXHAUSTED' | 'STOPPED'>(
    'IDLE',
  );
  readonly lastSuccess = signal<string | null>(null);
  constructor() {
    this.auth.sessionChanged.subscribe(() => {
      this.health.set('IDLE');
      this.lastSuccess.set(null);
    });
  }
  resume() {
    this.reconnect.next();
  }
  watch() {
    return defer(() => {
      let cursor = 0;
      const versions = new Map<number, number>();
      return this.reconnect.pipe(
        startWith(undefined),
        switchMap(() => {
          this.health.set('CONNECTING');
          const stopped = new Subject<void>();
          return timer(3000, 3000).pipe(
            take(200),
            exhaustMap(() =>
              this.api.changes(cursor).pipe(
                catchError((error) => {
                  this.health.set([401, 403].includes(error.status) ? 'STOPPED' : 'STALE');
                  if ([401, 403].includes(error.status)) {
                    this.lastSuccess.set(null);
                    stopped.next();
                  }
                  return EMPTY;
                }),
              ),
            ),
            map((batch) => {
              this.health.set('LIVE');
              this.lastSuccess.set(new Date().toISOString());
              const gap = batch.items.length > 0 && batch.items[0].cursor !== cursor + 1;
              const changed =
                batch.reset ||
                gap ||
                batch.items.some((event) => event.version > (versions.get(event.orderId) ?? 0));
              if (batch.reset) versions.clear();
              for (const event of batch.items)
                versions.set(
                  event.orderId,
                  Math.max(event.version, versions.get(event.orderId) ?? 0),
                );
              cursor = Math.max(batch.reset ? 0 : cursor, batch.cursor);
              return changed;
            }),
            tap({ complete: () => this.health.set('EXHAUSTED') }),
            filter(Boolean),
            takeUntil(stopped),
          );
        }),
        takeUntil(this.auth.sessionChanged),
      );
    });
  }
}

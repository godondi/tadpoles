import { Injectable, inject } from '@angular/core';
import { EMPTY, defer, exhaustMap, catchError, filter, map, take, takeUntil, timer } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { WorkspaceApi } from './workspace-api.service';
/** Visible-page polling is bounded, authenticated on every request, and cancelled on session change. */
@Injectable({ providedIn: 'root' })
export class OrderStatusService {
  private readonly api = inject(WorkspaceApi);
  private readonly auth = inject(AuthService);

  watch() {
    return defer(() => {
      let cursor = 0;
      const versions = new Map<number, number>();
      return timer(3000, 3000).pipe(
        take(200),
        exhaustMap(() => this.api.changes(cursor).pipe(catchError(() => EMPTY))),
        map((batch) => {
          const gap = batch.items.length > 0 && batch.items[0].cursor !== cursor + 1;
          const changed =
            batch.reset ||
            gap ||
            batch.items.some((event) => event.version > (versions.get(event.orderId) ?? 0));
          if (batch.reset) versions.clear();
          for (const event of batch.items)
            versions.set(event.orderId, Math.max(event.version, versions.get(event.orderId) ?? 0));
          cursor = batch.cursor;
          return changed;
        }),
        filter(Boolean),
        takeUntil(this.auth.sessionChanged),
      );
    });
  }
}

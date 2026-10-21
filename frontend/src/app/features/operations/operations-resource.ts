import { DestroyRef, inject, signal } from '@angular/core';
import { Observable, Subscription } from 'rxjs';
import { OperationsApi } from '../../core/api/operations-api.service';

/** Cancels pending reads when purpose/session changes; only transport failures can retain stale data. */
export class OperationsResource<T> {
  readonly data = signal<T | null>(null);
  readonly loading = signal(false);
  readonly message = signal('');
  readonly stale = signal(false);
  readonly status = signal(0);
  private request?: Subscription;
  constructor() {
    const changed = inject(OperationsApi).changed.subscribe(() => this.clear());
    inject(DestroyRef).onDestroy(() => {
      changed.unsubscribe();
      this.request?.unsubscribe();
    });
  }
  clear() {
    this.request?.unsubscribe();
    this.data.set(null);
    this.loading.set(false);
    this.message.set('');
    this.stale.set(false);
  }
  load(source: Observable<T>, preserve = false) {
    this.request?.unsubscribe();
    if (!preserve) this.data.set(null);
    this.status.set(0);
    this.loading.set(true);
    this.message.set('');
    this.request = source.subscribe({
      next: (data) => {
        this.data.set(data);
        this.loading.set(false);
        this.stale.set(false);
      },
      error: (error) => {
        this.status.set(error.status);
        this.loading.set(false);
        if (![0, 502, 503, 504].includes(error.status)) this.data.set(null);
        this.stale.set(this.data() !== null);
        this.message.set(
          error.status === 403
            ? 'Access denied or context expired.'
            : error.status === 404
              ? 'Record not found or outside your scope.'
              : (error.error?.message ?? 'Unable to load evidence. Try again.'),
        );
      },
    });
  }
}

import { DestroyRef, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable, Subscription } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';

/** One cancellable request per page. Errors are never treated as empty data. */
export class PageResource<T> {
  readonly data = signal<T | null>(null);
  readonly loading = signal(false);
  readonly error = signal('');
  readonly status = signal(0);
  private request?: Subscription;
  constructor() {
    const clear = inject(AuthService).sessionChanged.subscribe(() => {
      this.request?.unsubscribe();
      this.data.set(null);
      this.error.set('');
      this.loading.set(false);
    });
    inject(DestroyRef).onDestroy(() => {
      this.request?.unsubscribe();
      clear.unsubscribe();
    });
  }
  load(source: Observable<T>): void {
    this.request?.unsubscribe();
    this.data.set(null);
    this.error.set('');
    this.status.set(0);
    this.loading.set(true);
    this.request = source.subscribe({
      next: (value) => {
        this.data.set(value);
        this.loading.set(false);
      },
      error: (error: HttpErrorResponse) => {
        this.loading.set(false);
        this.status.set(error.status);
        this.error.set(
          error.status === 403
            ? 'You do not have access to this information.'
            : error.status === 401
              ? 'Your session has expired. Please sign in again.'
              : error.status === 404
                ? 'This record could not be found.'
                : typeof error.error?.message === 'string'
                  ? error.error.message
                  : 'Unable to connect. Please try again.',
        );
      },
    });
  }
}

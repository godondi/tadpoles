import { DestroyRef, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable, Subscription } from 'rxjs';
import { ComplianceApi } from './compliance-api.service';

export class ComplianceResource<T> {
  readonly data = signal<T | null>(null);
  readonly loading = signal(false);
  readonly error = signal('');
  readonly status = signal(0);
  private request?: Subscription;

  constructor() {
    const api = inject(ComplianceApi);
    const changed = api.changed.subscribe(() => this.clear());
    inject(DestroyRef).onDestroy(() => {
      changed.unsubscribe();
      this.request?.unsubscribe();
    });
  }

  clear() {
    this.request?.unsubscribe();
    this.data.set(null);
    this.loading.set(false);
    this.error.set('');
    this.status.set(0);
  }

  load(source: Observable<T>) {
    this.request?.unsubscribe();
    this.data.set(null);
    this.loading.set(true);
    this.error.set('');
    this.status.set(0);
    this.request = source.subscribe({
      next: (value) => {
        this.data.set(value);
        this.loading.set(false);
      },
      error: (error: HttpErrorResponse) => {
        this.loading.set(false);
        this.status.set(error.status ?? 0);
        this.data.set(null);
        this.error.set(this.message(error));
      },
    });
  }

  private message(error: HttpErrorResponse): string {
    if (error.status === 400) return 'The search filters are invalid. Review the values and try again.';
    if (error.status === 401) return 'Your session expired. Sign in again before reviewing evidence.';
    if (error.status === 403 || error.status === 409)
      return 'Access was denied or the purpose context expired. Start a new context.';
    if (error.status === 404) return 'This record could not be found in your permitted scope.';
    if (error.status === 429) return 'Too many requests. Wait briefly before trying again.';
    if (error.status === 503)
      return 'Compliance evidence is unavailable until an approved source and policy are configured.';
    if (error.status === 0) return 'Unable to connect. Check your connection and try again.';
    const body = error.error as { message?: string; detail?: string } | null;
    return body?.message ?? body?.detail ?? 'Unable to load compliance evidence. Try again.';
  }
}

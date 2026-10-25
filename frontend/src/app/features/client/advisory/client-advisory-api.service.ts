import { Injectable, PLATFORM_ID, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, catchError, throwError } from 'rxjs';
import { buildApiUrl } from '../../../core/api/api-url';
import { ClientAdvisory, ClientAssignment } from './client-advisory-models';

@Injectable({ providedIn: 'root' })
export class ClientAdvisoryApi {
  private readonly http = inject(HttpClient);
  private readonly platform = inject(PLATFORM_ID);

  assignment(): Observable<ClientAssignment> {
    return this.get<ClientAssignment>('/api/workspace/client/advisor');
  }

  advisory(): Observable<ClientAdvisory> {
    return this.get<ClientAdvisory>('/api/workspace/client/advisory');
  }

  private get<T>(path: string) {
    return this.http
      .get<T>(buildApiUrl(path, this.platform))
      .pipe(catchError((error) => this.normalizeError(error)));
  }

  private normalizeError(error: unknown): Observable<never> {
    if (!(error instanceof HttpErrorResponse)) return throwError(() => error);
    const body = error.error && typeof error.error === 'object' ? error.error : {};
    const detail = typeof body.detail === 'string' ? body.detail : null;
    const message =
      typeof body.message === 'string'
        ? body.message
        : detail ?? (error.status === 0 ? 'The advisory service is offline.' : 'Advisory information is unavailable.');
    return throwError(
      () =>
        new HttpErrorResponse({
          error: { ...body, message },
          headers: error.headers,
          status: error.status,
          statusText: error.statusText,
          url: error.url ?? undefined,
        }),
    );
  }
}

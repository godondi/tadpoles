import { isPlatformBrowser } from '@angular/common';
import { Injectable, PLATFORM_ID, inject } from '@angular/core';
import {
  HttpRequest,
  HttpHandler,
  HttpEvent,
  HttpInterceptor,
  HttpErrorResponse,
} from '@angular/common/http';
import { Observable, catchError, takeUntil, throwError } from 'rxjs';
import { Router } from '@angular/router';
import { AuthService } from './auth.service';
import { buildAnalyticsApiUrl, buildApiUrl } from '../api/api-url';

@Injectable()
export class AuthInterceptor implements HttpInterceptor {
  private readonly auth = inject(AuthService);
  private readonly platform = inject(PLATFORM_ID);
  private readonly router = inject(Router);

  intercept(request: HttpRequest<unknown>, next: HttpHandler): Observable<HttpEvent<unknown>> {
    const backend = isPlatformBrowser(this.platform)
      ? new URL(window.location.origin)
      : new URL(buildApiUrl('/', this.platform));
    const analytics = new URL(buildAnalyticsApiUrl('/', this.platform), backend);
    const url = new URL(request.url, backend);
    const publicPath = ['/auth/login', '/auth/signup', '/auth/register/client'].includes(
      url.pathname,
    );
    if (![backend.origin, analytics.origin].includes(url.origin) || publicPath) {
      return next.handle(request);
    }
    const token = this.auth.getAccessToken();
    if (token) request = request.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
    return next.handle(request).pipe(
      takeUntil(this.auth.sessionChanged),
      catchError((error) => {
        if (
          error instanceof HttpErrorResponse &&
          error.status === 401 &&
          token === this.auth.getAccessToken()
        ) {
          this.auth.logout(false);
          void this.router.navigate(['/login'], { queryParams: { expired: 'true' } });
        }
        return throwError(() => error);
      }),
    );
  }
}

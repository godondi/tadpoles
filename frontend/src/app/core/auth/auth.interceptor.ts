import { Injectable, inject } from '@angular/core';
import {
  HttpRequest,
  HttpHandler,
  HttpEvent,
  HttpInterceptor,
} from '@angular/common/http';
import { Observable } from 'rxjs';
import { AuthService } from './auth.service';

/**
 * HTTP Interceptor that automatically adds the JWT token to all outgoing requests.
 * Skips auth endpoints that don't require a token.
 */
@Injectable()
export class AuthInterceptor implements HttpInterceptor {
  private readonly authService = inject(AuthService);

  intercept(
    request: HttpRequest<unknown>,
    next: HttpHandler,
  ): Observable<HttpEvent<unknown>> {
    // Skip adding token for auth endpoints
    const authEndpoints = ['/auth/login', '/auth/signup', '/auth/register/client'];
    const isAuthEndpoint = authEndpoints.some(endpoint => request.url.includes(endpoint));

    if (isAuthEndpoint) {
      return next.handle(request);
    }

    // Add token for all other requests
    const token = this.authService.getAccessToken();
    if (token) {
      request = request.clone({
        setHeaders: {
          Authorization: `Bearer ${token}`,
        },
      });
    }

    return next.handle(request);
  }
}

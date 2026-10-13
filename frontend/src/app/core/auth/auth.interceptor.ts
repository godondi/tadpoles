import { Injectable, inject } from '@angular/core';
import {
  HttpRequest,
  HttpHandler,
  HttpEvent,
  HttpInterceptor,
} from '@angular/common/http';
import { Observable } from 'rxjs';
import { AuthService } from './auth.service';

@Injectable()
export class AuthInterceptor implements HttpInterceptor {
  private readonly authService = inject(AuthService);

  intercept(
    request: HttpRequest<unknown>,
    next: HttpHandler,
  ): Observable<HttpEvent<unknown>> {
    const authEndpoints = ['/auth/login', '/auth/signup', '/auth/register/client', '/api/auth/refresh'];
    const isAuthEndpoint = authEndpoints.some((endpoint) => request.url.includes(endpoint));
    const isBackendRequest = request.url.includes(':8082/') || request.url.includes('/api/') || request.url.includes('/auth/');

    if (isAuthEndpoint || !isBackendRequest) {
      return next.handle(request);
    }

    const token = this.authService.getAccessToken();
    if (!token) {
      return next.handle(request);
    }

    return next.handle(request.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`,
      },
    }));
  }
}

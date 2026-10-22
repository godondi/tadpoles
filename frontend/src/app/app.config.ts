import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient, withFetch, withInterceptorsFromDi } from '@angular/common/http';
import { HTTP_INTERCEPTORS } from '@angular/common/http';
import { NavigationError, provideRouter, withNavigationErrorHandler } from '@angular/router';
import { routes } from './app.routes';
import { provideClientHydration } from '@angular/platform-browser';
import { AuthInterceptor } from './core/auth/auth.interceptor';

const lazyChunkErrorPattern =
  /ChunkLoadError|Loading chunk|Failed to fetch dynamically imported module|Importing a module script failed/i;
const lazyChunkReloadKey = 'tadpoles.lazy-route-reload';
const lazyChunkReloadWindowMs = 30_000;

function recoverFromLazyChunkError(error: NavigationError): void {
  if (
    typeof window === 'undefined' ||
    !lazyChunkErrorPattern.test(String(error.error))
  ) {
    return;
  }

  const lastReload = Number(window.sessionStorage.getItem(lazyChunkReloadKey));
  if (Number.isFinite(lastReload) && Date.now() - lastReload < lazyChunkReloadWindowMs) {
    return;
  }

  window.sessionStorage.setItem(lazyChunkReloadKey, String(Date.now()));
  const reloadUrl = new URL(window.location.href);
  reloadUrl.searchParams.set('_app_reload', String(Date.now()));
  window.location.replace(reloadUrl.toString());
}

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withNavigationErrorHandler(recoverFromLazyChunkError)),
    provideHttpClient(withFetch(), withInterceptorsFromDi()),
    provideClientHydration(),
    {
      provide: HTTP_INTERCEPTORS,
      useClass: AuthInterceptor,
      multi: true,
    },
  ]
};

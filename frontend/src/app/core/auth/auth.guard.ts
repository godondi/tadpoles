import { CanActivateFn, CanActivateChildFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { AuthService } from './auth.service';
import { isInstrumentManagementRoute, routeRole } from './workspace-policy';

export const authGuard: CanActivateFn = async (_route, state) => {
  const auth = inject(AuthService),
    router = inject(Router);
  return (
    (await auth.ensureInitialized()) ||
    router.createUrlTree([auth.status() === 'error' ? '/session-error' : '/login'], {
      queryParams: state.url === '/' ? undefined : { redirectTo: state.url },
    })
  );
};
export const workspaceGuard: CanActivateChildFn = async (_route, state) => {
  const auth = inject(AuthService),
    router = inject(Router);
  if (!(await auth.ensureInitialized())) return router.createUrlTree(['/login']);
  if (isInstrumentManagementRoute(state.url) && auth.roles().includes('CLIENT'))
    return router.createUrlTree(['/unauthorized']);
  const role = routeRole(state.url);
  return role && auth.roles().includes(role) ? true : router.createUrlTree(['/unauthorized']);
};
export const clientGuard: CanActivateFn = async (_route, state) => {
  const auth = inject(AuthService),
    router = inject(Router);
  if (!(await auth.ensureInitialized())) return router.createUrlTree(['/login']);
  if (!auth.roles().includes('CLIENT')) return router.createUrlTree(['/unauthorized']);
  if (!(await auth.ensureClientProfile()))
    return router.createUrlTree(['/session-error'], { queryParams: { redirectTo: state.url } });
  return auth.hasCompletedOnboarding() || state.url.split('?')[0] === '/onboarding'
    ? true
    : router.createUrlTree(['/onboarding']);
};
export const landingGuard: CanActivateFn = () =>
  inject(Router).parseUrl(inject(AuthService).home());
export const guestOnlyGuard: CanActivateFn = async () => {
  const auth = inject(AuthService),
    router = inject(Router);
  return (await auth.ensureInitialized()) ? router.parseUrl(auth.home()) : true;
};

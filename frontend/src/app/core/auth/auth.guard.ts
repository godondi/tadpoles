import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { AuthService } from './auth.service';

export const authGuard: CanActivateFn = (_route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (!authService.isAuthenticated()) {
    return router.createUrlTree(['/login'], {
        queryParams: state.url && state.url !== '/' ? { redirectTo: state.url } : undefined,
      });
  }

  if (!authService.hasCompletedOnboarding() && state.url !== '/onboarding') {
    return router.createUrlTree(['/onboarding']);
  }

  return true;
};

export const guestOnlyGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (!authService.isAuthenticated()) {
    return true;
  }

  return authService.hasCompletedOnboarding()
    ? router.createUrlTree(['/'])
    : router.createUrlTree(['/onboarding']);
};


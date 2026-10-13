import { inject } from '@angular/core';
import { CanActivateFn, Router, UrlTree } from '@angular/router';
import { AuthService } from './auth.service';
import { WorkspaceRole } from './workspace-roles';

const SAFE_REDIRECT_PATTERN = /^\/[A-Za-z0-9\-._~!$&'()*+,;=:@/]*$/;

function normalizePath(url: string): string {
  const [path] = url.split(/[?#]/);
  return path || '/';
}

function isInternalRedirect(redirectTo: string | null): redirectTo is string {
  if (!redirectTo) {
    return false;
  }
  if (!SAFE_REDIRECT_PATTERN.test(redirectTo)) {
    return false;
  }
  if (redirectTo.startsWith('/login')) {
    return false;
  }
  return true;
}

function redirectToLogin(router: Router, url: string): UrlTree {
  const queryParams = normalizePath(url) !== '/' ? { redirectTo: url } : undefined;
  return router.createUrlTree(['/login'], { queryParams });
}

async function requireAuthenticated(
  authService: AuthService,
  router: Router,
  url: string,
): Promise<true | UrlTree> {
  await authService.ensureInitialized();

  if (!authService.isAuthenticated()) {
    return redirectToLogin(router, url);
  }

  return true;
}

export const authGuard: CanActivateFn = async (_route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);
  return requireAuthenticated(authService, router, state.url);
};

export const onboardingGuard: CanActivateFn = async () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  await authService.ensureInitialized();
  if (!authService.isAuthenticated()) {
    return redirectToLogin(router, '/onboarding');
  }

  if (!authService.hasAnyRole(['CLIENT'])) {
    return router.createUrlTree([authService.resolveLandingRoute()]);
  }

  if (authService.hasCompletedOnboarding()) {
    return router.createUrlTree([authService.resolveLandingRoute()]);
  }

  return true;
};

export function workspaceGuard(requiredRoles: WorkspaceRole[]): CanActivateFn {
  return async (_route, state) => {
    const authService = inject(AuthService);
    const router = inject(Router);
    const authResult = await requireAuthenticated(authService, router, state.url);
    if (authResult !== true) {
      return authResult;
    }
    if (!authService.hasAnyRole(requiredRoles)) {
      return router.createUrlTree(['/unauthorized']);
    }
    return true;
  };
}

export const clientWorkspaceGuard: CanActivateFn = async (_route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);
  const authResult = await requireAuthenticated(authService, router, state.url);
  if (authResult !== true) {
    return authResult;
  }
  if (!authService.hasAnyRole(['CLIENT'])) {
    return router.createUrlTree(['/unauthorized']);
  }
  if (!authService.hasCompletedOnboarding()) {
    return router.createUrlTree(['/onboarding']);
  }
  return true;
};

export const rootLandingGuard: CanActivateFn = async (route) => {
  const authService = inject(AuthService);
  const router = inject(Router);
  const authResult = await requireAuthenticated(authService, router, '/');
  if (authResult !== true) {
    return authResult;
  }

  const redirectTo = route.queryParamMap.get('redirectTo');
  if (isInternalRedirect(redirectTo)) {
    return router.createUrlTree([redirectTo]);
  }
  return router.createUrlTree([authService.resolveLandingRoute()]);
};

export const guestOnlyGuard: CanActivateFn = async (route) => {
  const authService = inject(AuthService);
  const router = inject(Router);
  await authService.ensureInitialized();

  if (!authService.isAuthenticated()) {
    return true;
  }

  const redirectTo = route.queryParamMap.get('redirectTo');
  if (isInternalRedirect(redirectTo)) {
    return router.createUrlTree([redirectTo]);
  }

  return router.createUrlTree([authService.resolveLandingRoute()]);
};

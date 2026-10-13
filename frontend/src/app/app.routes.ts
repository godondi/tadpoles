import { Routes } from '@angular/router';
import { authGuard, guestOnlyGuard } from './core/auth/auth.guard';
import { AuthenticatedShellComponent } from './layout/authenticated-shell.component';

export const routes: Routes = [
  {
    path: 'login',
    canActivate: [guestOnlyGuard],
    loadComponent: () => import('./login').then((m) => m.LoginComponent),
  },
  {
    path: 'signup',
    canActivate: [guestOnlyGuard],
    loadComponent: () => import('./signup').then((m) => m.SignupComponent),
  },
  {
    path: 'onboarding',
    canActivate: [authGuard],
    loadComponent: () => import('./onboarding').then((m) => m.OnboardingComponent),
  },
  {
    path: '',
    component: AuthenticatedShellComponent,
    canActivate: [authGuard],
    children: [
      {
        path: 'trade/:symbol',
        loadComponent: () => import('./features/trade/trade.component').then((m) => m.TradeComponent),
      },
      {
        path: '',
        loadComponent: () => import('./dashboard').then((m) => m.DashboardComponent),
      },
      {
        path: 'profile',
        loadComponent: () => import('./profile').then((m) => m.ProfileComponent),
      },
      {
        path: 'trades',
        loadComponent: () => import('./trades').then((m) => m.TradesComponent),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];

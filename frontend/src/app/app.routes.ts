import { Routes } from '@angular/router';
import {
  authGuard,
  guestOnlyGuard,
  workspaceGuard,
  clientGuard,
  landingGuard,
} from './core/auth/auth.guard';
import { AuthenticatedShellComponent } from './layout/authenticated-shell.component';
const staff = () =>
  import('./shared/workspace/staff-page.component').then((m) => m.StaffPageComponent);
const analytics = () =>
  import('./features/analytics/analytics-page.component').then((m) => m.AnalyticsPageComponent);
const message = () =>
  import('./shared/workspace/message-page.component').then((m) => m.MessagePageComponent);
const client = [clientGuard];
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
  { path: 'register', redirectTo: 'signup', pathMatch: 'full' },
  {
    path: 'onboarding',
    canActivate: [authGuard, clientGuard],
    loadComponent: () => import('./onboarding').then((m) => m.OnboardingComponent),
  },
  {
    path: 'unauthorized',
    loadComponent: message,
    data: {
      title: 'Access denied',
      message:
        'Your account does not have permission to open this workspace. You can return to an assigned workspace or sign in with another account.',
    },
  },
  {
    path: 'not-found',
    loadComponent: message,
    data: {
      title: 'Page not found',
      message: 'This page does not exist. Check the link or return to your workspace.',
    },
  },
  {
    path: 'session-error',
    loadComponent: message,
    data: {
      title: 'Unable to verify your account',
      message:
        'Your account information could not be loaded. Please check your connection and try again.',
      retryable: true,
    },
  },
  {
    path: '',
    component: AuthenticatedShellComponent,
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', canActivate: [landingGuard], loadComponent: message },
      {
        path: '',
        canActivateChild: [workspaceGuard],
        children: [
          {
            path: 'app/overview',
            canActivate: client,
            loadComponent: () =>
              import('./features/client/account-page.component').then(
                (m) => m.AccountPageComponent,
              ),
          },
          {
            path: 'app/portfolio',
            canActivate: client,
            loadComponent: () =>
              import('./features/client/account-page.component').then(
                (m) => m.AccountPageComponent,
              ),
            data: { portfolioOnly: true },
          },
          {
            path: 'app/markets',
            canActivate: client,
            loadComponent: () =>
              import('./features/client/markets-page.component').then(
                (m) => m.MarketsPageComponent,
              ),
          },
          {
            path: 'app/instruments/:id',
            canActivate: client,
            loadComponent: () =>
              import('./features/client/instrument-page.component').then(
                (m) => m.InstrumentPageComponent,
              ),
          },
          {
            path: 'app/trade/:instrumentId',
            canActivate: client,
            loadComponent: () =>
              import('./features/client/instrument-page.component').then(
                (m) => m.InstrumentPageComponent,
              ),
            data: { ticket: true },
          },
          {
            path: 'app/orders',
            canActivate: client,
            loadComponent: () =>
              import('./features/client/orders-page.component').then((m) => m.OrdersPageComponent),
          },
          {
            path: 'app/orders/:id',
            canActivate: client,
            loadComponent: () =>
              import('./features/client/order-detail.component').then(
                (m) => m.OrderDetailComponent,
              ),
          },
          {
            path: 'profile',
            canActivate: client,
            loadComponent: () => import('./profile').then((m) => m.ProfileComponent),
          },
          { path: 'trades', redirectTo: 'app/orders', pathMatch: 'full' },
          {
            path: 'trade/:symbol',
            canActivate: client,
            loadComponent: () =>
              import('./features/client/instrument-page.component').then(
                (m) => m.LegacyInstrumentComponent,
              ),
          },
            {
              path: 'analytics/overview',
              loadComponent: analytics,
              data: {
                analyticsView: 'overview',
                title: 'Analytics overview',
                description: 'A read-only view of aggregate trading activity with explicit freshness and metric definitions.',
              },
            },
            {
              path: 'analytics/trends',
              loadComponent: analytics,
              data: {
                analyticsView: 'trends',
                title: 'Activity trends',
                description: 'Explore historical activity over an explicit reporting period and granularity.',
              },
            },
            {
              path: 'analytics/instruments',
              loadComponent: analytics,
              data: {
                analyticsView: 'instruments',
                title: 'Instrument analysis',
                description: 'Compare approved aggregate activity by instrument, market, unit, and currency.',
              },
            },
          ...[
            [
              'operations/orders',
              'Orders & monitoring',
              'Trading operations',
              'Search and investigate order outcomes.',
              'Approved, scoped order monitoring with recorded lifecycle and exception details.',
              '/api/operations/orders',
              '',
            ],
            [
              'operations/orders/:id',
              'Order investigation',
              'Trading operations',
              'Review recorded decisions and execution evidence.',
              'Read-only order events, quotes and linked booking effects.',
              '/api/operations/orders/:id',
              '/operations/orders',
            ],
            [
              'compliance/investigations',
              'Investigations',
              'Risk & compliance',
              'Find and review order evidence within your approved scope.',
              'Purpose-controlled access to immutable order evidence and completeness indicators.',
              '/api/compliance/investigations',
              '',
            ],
            [
              'compliance/investigations/:id',
              'Investigation details',
              'Risk & compliance',
              'Review the evidence behind an order outcome.',
              'Original instructions, recorded decisions and audited sensitive access.',
              '/api/compliance/orders/:id/evidence',
              '/compliance/investigations',
            ],
            [
              'compliance/evidence',
              'Evidence & exports',
              'Risk & compliance',
              'Review approved evidence requests and access purposes.',
              'Export history and protected downloads after purpose, redaction and retention policies are approved.',
              '/api/compliance/evidence',
              '',
            ],
            [
              'finance/overview',
              'Finance overview',
              'Finance',
              'Review booked movements and accounting evidence.',
              'Verified fill and cash-movement totals by period and currency.',
              '/api/finance/summary',
              '',
            ],
            [
              'finance/reconciliation',
              'Reconciliation',
              'Finance',
              'Investigate matching results and exceptions.',
              'Matched, unmatched and unknown results based on genuine cash and position postings.',
              '/api/finance/reconciliations',
              '',
            ],
          ].map(([path, title, section, description, expectation, endpoint, back]) => ({
            path,
            loadComponent: staff,
            data: { title, section, description, expectation, endpoint, back },
          })),
        ],
      },
    ],
  },
  { path: '**', redirectTo: 'not-found' },
];

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
          { path: 'app/support/new', data: { new: true }, canActivate: client, loadComponent: () => import('./features/client/support-page.component').then(m => m.SupportPageComponent) },
          { path: 'app/support/:reference', data: {}, canActivate: client, loadComponent: () => import('./features/client/support-page.component').then(m => m.SupportPageComponent) },
          { path: 'app/support', data: {}, canActivate: client, loadComponent: () => import('./features/client/support-page.component').then(m => m.SupportPageComponent) },
          { path: 'operations/cases/:reference', data: { staff: true }, loadComponent: () => import('./features/client/support-page.component').then(m => m.SupportPageComponent) },
          { path: 'operations/cases', data: { staff: true }, loadComponent: () => import('./features/client/support-page.component').then(m => m.SupportPageComponent) },
          {
            path: 'finance/reconciliation',
            loadComponent: () =>
              import('./shared/workspace/reconciliation-page.component').then(
                (m) => m.ReconciliationPageComponent,
              ),
          },
          {
            path: 'operations/overview',
            loadComponent: () =>
              import('./features/operations/overview-page.component').then(
                (m) => m.OperationsOverviewComponent,
              ),
          },
          {
            path: 'operations/orders',
            loadComponent: () =>
              import('./features/operations/orders-page.component').then(
                (m) => m.OperationsOrdersComponent,
              ),
          },
          {
            path: 'operations/orders/:id',
            loadComponent: () =>
              import('./features/operations/order-detail.component').then(
                (m) => m.OperationsOrderDetailComponent,
              ),
          },
          {
            path: 'operations/exceptions',
            data: { exceptions: true },
            loadComponent: () =>
              import('./features/operations/orders-page.component').then(
                (m) => m.OperationsOrdersComponent,
              ),
          },
          {
            path: 'operations/reconciliation',
            loadComponent: () =>
              import('./features/operations/reconciliation-page.component').then(
                (m) => m.OperationsReconciliationComponent,
              ),
          },
          {
            path: 'operations/instruments',
            pathMatch: 'full',
            loadComponent: () =>
              import('./features/operations/instruments-page.component').then(
                (m) => m.OperationsInstrumentsComponent,
              ),
          },
          {
            path: 'operations/instruments/:id',
            loadComponent: () =>
              import('./features/operations/instrument-policy-editor.component').then(
                (m) => m.InstrumentPolicyEditorComponent,
              ),
          },
          {
            path: 'app/banking',
            canActivate: client,
            loadComponent: () =>
              import('./features/client/banking-page.component').then(
                (m) => m.BankingPageComponent,
              ),
          },
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
            pathMatch: 'full',
            redirectTo: '/app/overview',
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
          ...[
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
              'analytics/overview',
              'Analytics overview',
              'Business analytics',
              'Understand aggregate trading activity.',
              'Submitted, accepted, filled and rejected counts by currency, with source freshness.',
              '/api/analytics/summary',
              '',
            ],
            [
              'analytics/trends',
              'Activity trends',
              'Business analytics',
              'Explore activity over time.',
              'Historical daily, weekly and monthly aggregates from a separate reporting source.',
              '/api/analytics/trends',
              '',
            ],
            [
              'analytics/instruments',
              'Instrument analysis',
              'Business analytics',
              'Compare activity across instruments and markets.',
              'Aggregate order and fill counts with separate currency totals.',
              '/api/analytics/instruments',
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

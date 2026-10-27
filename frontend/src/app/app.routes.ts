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
const complianceInvestigations = () =>
  import('./features/compliance/investigations-page.component').then(
    (m) => m.ComplianceInvestigationsPageComponent,
  );
const complianceInvestigationDetail = () =>
  import('./features/compliance/investigation-detail.component').then(
    (m) => m.ComplianceInvestigationDetailComponent,
  );
const complianceEvidence = () =>
  import('./features/compliance/evidence-page.component').then(
    (m) => m.ComplianceEvidencePageComponent,
  );
const financeOverview = () =>
  import('./features/finance/overview/finance-overview.component').then(
    (m) => m.FinanceOverviewComponent,
  );
const financeReconciliation = () =>
  import('./features/finance/reconciliation/finance-reconciliation.component').then(
    (m) => m.FinanceReconciliationComponent,
  );
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
            path: 'app/support/new',
            data: { new: true },
            canActivate: client,
            loadComponent: () =>
              import('./features/client/support-page.component').then(
                (m) => m.SupportPageComponent,
              ),
          },
          {
            path: 'app/support/:reference',
            data: {},
            canActivate: client,
            loadComponent: () =>
              import('./features/client/support-page.component').then(
                (m) => m.SupportPageComponent,
              ),
          },
          {
            path: 'app/support',
            data: {},
            canActivate: client,
            loadComponent: () =>
              import('./features/client/support-page.component').then(
                (m) => m.SupportPageComponent,
              ),
          },
          {
            path: 'operations/cases/:reference',
            data: { staff: true },
            loadComponent: () =>
              import('./features/client/support-page.component').then(
                (m) => m.SupportPageComponent,
              ),
          },
          {
            path: 'operations/cases',
            data: { staff: true },
            loadComponent: () =>
              import('./features/client/support-page.component').then(
                (m) => m.SupportPageComponent,
              ),
          },
          {
            path: 'compliance/cases/:reference',
            loadComponent: () =>
              import('./features/compliance/review-cases.component').then(
                (m) => m.ReviewCasesComponent,
              ),
          },
          {
            path: 'compliance/cases',
            loadComponent: () =>
              import('./features/compliance/review-cases.component').then(
                (m) => m.ReviewCasesComponent,
              ),
          },
          {
            path: 'finance/reconciliation',
            loadComponent: financeReconciliation,
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
            path: 'operations/users/create',
            loadComponent: () =>
              import('./features/operations/create-account.component').then(
                (m) => m.OperationsCreateAccountComponent,
              ),
          },
          {
            path: 'operations/users',
            loadComponent: () =>
              import('./features/operations/user-access.component').then(
                (m) => m.OperationsUserAccessComponent,
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
          {
            path: 'advisor/clients',
            loadComponent: () =>
              import('./features/advisor/advisor-clients-page.component').then(
                (m) => m.AdvisorClientsPageComponent,
              ),
          },
          {
            path: 'advisor/clients/:id',
            loadComponent: () =>
              import('./features/advisor/advisor-client-detail-page.component').then(
                (m) => m.AdvisorClientDetailPageComponent,
              ),
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
              description:
                'A read-only view of aggregate trading activity with explicit freshness and metric definitions.',
            },
          },
          {
            path: 'analytics/trends',
            loadComponent: analytics,
            data: {
              analyticsView: 'trends',
              title: 'Activity trends',
              description:
                'Explore historical activity over an explicit reporting period and granularity.',
            },
          },
          {
            path: 'analytics/instruments',
            loadComponent: analytics,
            data: {
              analyticsView: 'instruments',
              title: 'Instrument analysis',
              description:
                'Compare approved aggregate activity by instrument, market, unit, and currency.',
            },
          },
          {
            path: 'compliance/investigations',
            loadComponent: complianceInvestigations,
          },
          {
            path: 'compliance/investigations/:id',
            loadComponent: complianceInvestigationDetail,
          },
          {
            path: 'compliance/evidence',
            loadComponent: complianceEvidence,
          },
          ...[
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
            loadComponent: path === 'finance/overview' ? financeOverview : staff,
            data: { title, section, description, expectation, endpoint, back },
          })),
        ],
      },
    ],
  },
  { path: '**', redirectTo: 'not-found' },
];

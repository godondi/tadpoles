import { Routes } from '@angular/router';
import {
  authGuard,
  clientWorkspaceGuard,
  guestOnlyGuard,
  onboardingGuard,
  rootLandingGuard,
  workspaceGuard,
} from './core/auth/auth.guard';
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
    path: 'register',
    redirectTo: 'signup',
    pathMatch: 'full',
  },
  {
    path: 'onboarding',
    canActivate: [onboardingGuard],
    loadComponent: () => import('./onboarding').then((m) => m.OnboardingComponent),
  },
  {
    path: '',
    pathMatch: 'full',
    canActivate: [rootLandingGuard],
    loadComponent: () => import('./shared/loading/loading.component').then((m) => m.LoadingComponent),
  },
  {
    path: '',
    component: AuthenticatedShellComponent,
    canActivate: [authGuard],
    canActivateChild: [authGuard],
    children: [
      {
        path: 'app/overview',
        canActivate: [clientWorkspaceGuard],
        loadComponent: () => import('./dashboard').then((m) => m.DashboardComponent),
      },
      {
        path: 'app/markets',
        canActivate: [clientWorkspaceGuard],
        loadComponent: () => import('./features/client/markets.component').then((m) => m.ClientMarketsComponent),
      },
      {
        path: 'app/instruments/:id',
        canActivate: [clientWorkspaceGuard],
        loadComponent: () => import('./features/client/instrument-detail.component').then((m) => m.ClientInstrumentDetailComponent),
      },
      {
        path: 'app/trade/:instrumentId',
        canActivate: [clientWorkspaceGuard],
        loadComponent: () => import('./features/client/ticket.component').then((m) => m.ClientTicketComponent),
      },
      {
        path: 'app/portfolio',
        canActivate: [clientWorkspaceGuard],
        loadComponent: () => import('./features/client/portfolio.component').then((m) => m.ClientPortfolioComponent),
      },
      {
        path: 'app/orders',
        canActivate: [clientWorkspaceGuard],
        loadComponent: () => import('./features/client/orders.component').then((m) => m.ClientOrdersComponent),
      },
      {
        path: 'app/orders/:id',
        canActivate: [clientWorkspaceGuard],
        loadComponent: () => import('./features/client/order-detail.component').then((m) => m.ClientOrderDetailComponent),
      },
      {
        path: 'operations/orders',
        canActivate: [workspaceGuard(['TRADING_OPS'])],
        loadComponent: () => import('./features/operations/orders.component').then((m) => m.OperationsOrdersComponent),
      },
      {
        path: 'operations/orders/:id',
        canActivate: [workspaceGuard(['TRADING_OPS'])],
        loadComponent: () => import('./features/operations/order-detail.component').then((m) => m.OperationsOrderDetailComponent),
      },
      {
        path: 'compliance/investigations',
        canActivate: [workspaceGuard(['COMPLIANCE'])],
        loadComponent: () => import('./features/compliance/investigations.component').then((m) => m.ComplianceInvestigationsComponent),
      },
      {
        path: 'compliance/investigations/:id',
        canActivate: [workspaceGuard(['COMPLIANCE'])],
        loadComponent: () => import('./features/compliance/investigation-detail.component').then((m) => m.ComplianceInvestigationDetailComponent),
      },
      {
        path: 'analytics/overview',
        canActivate: [workspaceGuard(['ANALYST'])],
        loadComponent: () => import('./features/analytics/overview.component').then((m) => m.AnalyticsOverviewComponent),
      },
      {
        path: 'analytics/trends',
        canActivate: [workspaceGuard(['ANALYST'])],
        loadComponent: () => import('./features/analytics/trends.component').then((m) => m.AnalyticsTrendsComponent),
      },
      {
        path: 'analytics/instruments',
        canActivate: [workspaceGuard(['ANALYST'])],
        loadComponent: () => import('./features/analytics/instruments.component').then((m) => m.AnalyticsInstrumentsComponent),
      },
      {
        path: 'finance/overview',
        canActivate: [workspaceGuard(['FINANCE'])],
        loadComponent: () => import('./features/finance/overview.component').then((m) => m.FinanceOverviewComponent),
      },
      {
        path: 'finance/reconciliation',
        canActivate: [workspaceGuard(['FINANCE'])],
        loadComponent: () => import('./features/finance/reconciliation.component').then((m) => m.FinanceReconciliationComponent),
      },
      {
        path: 'profile',
        loadComponent: () => import('./profile').then((m) => m.ProfileComponent),
      },
      {
        path: 'trades',
        redirectTo: 'app/orders',
        pathMatch: 'full',
      },
      {
        path: 'trade/:symbol',
        canActivate: [clientWorkspaceGuard],
        loadComponent: () => import('./features/trade/trade.component').then((m) => m.TradeComponent),
      },
    ],
  },
  {
    path: 'unauthorized',
    loadComponent: () => import('./shared/unauthorized.component').then((m) => m.UnauthorizedComponent),
  },
  {
    path: 'not-found',
    loadComponent: () => import('./shared/not-found.component').then((m) => m.NotFoundComponent),
  },
  {
    path: '**',
    redirectTo: 'not-found',
  },
];

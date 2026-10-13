import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { catchError, combineLatest, map, of, switchMap } from 'rxjs';
import { AuthService } from './core/auth/auth.service';
import { WorkspaceApiService } from './core/api/workspace-api.service';

@Component({
  selector: 'app-dashboard',
  imports: [RouterLink],
  templateUrl: './dashboard.html',
  styleUrl: './app.css',
  host: {
    role: 'main',
  },
})
export class DashboardComponent {
  private readonly authService = inject(AuthService);
  private readonly api = inject(WorkspaceApiService);

  private readonly clientId$ = toObservable(this.authService.clientId);

  readonly overview = toSignal(
    this.clientId$.pipe(
      switchMap((clientId) => {
        if (clientId === null) {
          return of({
            cashBalance: null as number | null,
            holdingsCount: 0,
            recentOrders: 0,
            asOf: '',
          });
        }
        return combineLatest([
          this.api.getClientBalance(clientId).pipe(map((result) => result.cashBalance), catchError(() => of(null))),
          this.api.listClientHoldings(clientId).pipe(map((result) => result.holdings.length), catchError(() => of(0))),
          this.api.listClientTrades(clientId).pipe(
            map((result) => {
              const trades = result.trades.slice(0, 5);
              return {
                count: trades.length,
                date: trades[0]?.tradeDate ?? '',
              };
            }),
            catchError(() => of({ count: 0, date: '' })),
          ),
        ]).pipe(
          map(([cashBalance, holdingsCount, orders]) => ({
            cashBalance,
            holdingsCount,
            recentOrders: orders.count,
            asOf: orders.date,
          })),
        );
      }),
    ),
    {
      initialValue: {
        cashBalance: null as number | null,
        holdingsCount: 0,
        recentOrders: 0,
        asOf: '',
      },
    },
  );

  formatCurrency(value: number | null): string {
    if (value === null) {
      return 'Unavailable';
    }
    return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(value);
  }
}

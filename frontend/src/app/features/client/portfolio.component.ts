import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { catchError, map, of, switchMap } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { WorkspaceApiService } from '../../core/api/workspace-api.service';

@Component({
  selector: 'app-client-portfolio',
  imports: [RouterLink],
  template: `
    <main class="workspace-page" role="main">
      <h1>Portfolio</h1>
      @if (clientId() === null) {
        <p class="error">No client account is linked to this session.</p>
      } @else {
        <p class="subtitle">Cash balance: {{ cashBalance() }}</p>
        <table class="data-table">
          <caption>Current holdings</caption>
          <thead>
            <tr><th>Instrument ID</th><th>Quantity</th><th>As of</th><th></th></tr>
          </thead>
          <tbody>
            @for (holding of holdings(); track holding.holdingId) {
              <tr>
                <td>{{ holding.instrumentId }}</td>
                <td>{{ holding.quantity }}</td>
                <td>{{ holding.asOfDate }}</td>
                <td><a [routerLink]="['/app/trade', holding.instrumentId]">Sell</a></td>
              </tr>
            }
          </tbody>
        </table>
      }
    </main>
  `,
})
export class ClientPortfolioComponent {
  private readonly authService = inject(AuthService);
  private readonly api = inject(WorkspaceApiService);

  readonly clientId = this.authService.clientId;

  readonly holdings = toSignal(
    toObservable(this.clientId).pipe(
      switchMap((clientId) => {
        if (clientId === null) {
          return of([]);
        }
        return this.api.listClientHoldings(clientId).pipe(
          map((response) => response.holdings),
          catchError(() => of([])),
        );
      }),
    ),
    { initialValue: [] },
  );

  readonly cashBalance = toSignal(
    toObservable(this.clientId).pipe(
      switchMap((clientId) => {
        if (clientId === null) {
          return of('Unavailable');
        }
        return this.api.getClientBalance(clientId).pipe(
          map((response) => this.formatCurrency(response.cashBalance)),
          catchError(() => of('Unavailable')),
        );
      }),
    ),
    { initialValue: 'Unavailable' },
  );

  private formatCurrency(value: number): string {
    return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(value);
  }
}

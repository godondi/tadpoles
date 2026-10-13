import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { catchError, map, of, switchMap } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { WorkspaceApiService } from '../../core/api/workspace-api.service';

@Component({
  selector: 'app-client-orders',
  imports: [RouterLink],
  template: `
    <main class="workspace-page" role="main">
      <h1>My orders</h1>
      @if (error()) {
        <p class="error">{{ error() }}</p>
      } @else {
        <table class="data-table">
          <caption>Order history</caption>
          <thead>
            <tr><th>Order</th><th>Instrument</th><th>Side</th><th>Quantity</th><th>Status</th><th>Date</th></tr>
          </thead>
          <tbody>
            @for (trade of trades(); track trade.tradeId) {
              <tr>
                <td><a [routerLink]="['/app/orders', trade.tradeId]">#{{ trade.tradeId }}</a></td>
                <td>{{ trade.instrumentId }}</td>
                <td>{{ trade.tradeType }}</td>
                <td>{{ trade.quantity }}</td>
                <td>{{ trade.status }}</td>
                <td>{{ trade.tradeDate }}</td>
              </tr>
            }
          </tbody>
        </table>
      }
    </main>
  `,
})
export class ClientOrdersComponent {
  private readonly authService = inject(AuthService);
  private readonly api = inject(WorkspaceApiService);

  readonly trades = toSignal(
    toObservable(this.authService.clientId).pipe(
      switchMap((clientId) => {
        if (clientId === null) {
          return of([]);
        }
        return this.api.listClientTrades(clientId).pipe(
          map((response) => response.trades),
          catchError(() => of([])),
        );
      }),
    ),
    { initialValue: [] },
  );

  readonly error = toSignal(
    toObservable(this.authService.clientId).pipe(
      switchMap((clientId) => {
        if (clientId === null) {
          return of('No client account linked to this session.');
        }
        return this.api.listClientTrades(clientId).pipe(
          map(() => ''),
          catchError(() => of('Unable to load your order history.')),
        );
      }),
    ),
    { initialValue: '' },
  );
}

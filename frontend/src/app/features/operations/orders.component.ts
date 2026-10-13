import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { WorkspaceApiService } from '../../core/api/workspace-api.service';
import { toSignal } from '@angular/core/rxjs-interop';
import { catchError, map, of } from 'rxjs';

@Component({
  selector: 'app-operations-orders',
  imports: [RouterLink],
  template: `
    <main class="workspace-page" role="main">
      <h1>Operations · Orders and monitoring</h1>
      @if (error()) {
        <p class="error">{{ error() }}</p>
      } @else {
        <table class="data-table">
          <caption>Order investigation queue</caption>
          <thead>
          <tr><th>Order ID</th><th>Client</th><th>Status</th><th>Date</th><th></th></tr>
          </thead>
          <tbody>
            @for (trade of trades(); track trade.tradeId) {
              <tr>
                <td>{{ trade.tradeId }}</td>
                <td>{{ trade.clientId }}</td>
                <td>{{ trade.status }}</td>
                <td>{{ trade.tradeDate }}</td>
                <td><a [routerLink]="['/operations/orders', trade.tradeId]">Investigate</a></td>
              </tr>
            }
          </tbody>
        </table>
      }
    </main>
  `,
})
export class OperationsOrdersComponent {
  private readonly api = inject(WorkspaceApiService);

  readonly trades = toSignal(
    this.api.listOperationsOrders().pipe(
      map((response) => response.trades),
      catchError(() => of([])),
    ),
    { initialValue: [] },
  );

  readonly error = toSignal(
    this.api.listOperationsOrders().pipe(
      map(() => ''),
      catchError(() => of('Unable to load operations orders.')),
    ),
    { initialValue: '' },
  );
}

import { Component, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { WorkspaceApiService } from '../../core/api/workspace-api.service';
import { toSignal } from '@angular/core/rxjs-interop';
import { catchError, map, of, switchMap } from 'rxjs';

@Component({
  selector: 'app-operations-order-detail',
  imports: [RouterLink],
  template: `
    <main class="workspace-page" role="main">
      @if (trade(); as item) {
        <h1>Operations order #{{ item.tradeId }}</h1>
        <p>Status: {{ item.status }}</p>
        <p>Client reference: {{ item.clientId }}</p>
        <p>Instrument: {{ item.instrumentId }}</p>
        <p>Quantity: {{ item.quantity }}</p>
        <p>Execution: {{ item.executedAt || 'Pending' }}</p>
        <a routerLink="/operations/orders">Back to queue</a>
      } @else {
        <h1>Order unavailable</h1>
        <p class="error">{{ error() }}</p>
      }
    </main>
  `,
})
export class OperationsOrderDetailComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly api = inject(WorkspaceApiService);

  readonly trade = toSignal(
    this.route.paramMap.pipe(
      map((params) => Number(params.get('id') ?? 0)),
      switchMap((id) => this.api.getOperationsOrder(id).pipe(catchError(() => of(null)))),
    ),
    { initialValue: null },
  );

  readonly error = toSignal(
    this.route.paramMap.pipe(
      map((params) => Number(params.get('id') ?? 0)),
      switchMap((id) => this.api.getOperationsOrder(id).pipe(map(() => ''), catchError(() => of('Unable to load this order.')))),
    ),
    { initialValue: '' },
  );
}

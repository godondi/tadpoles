import { Component, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { combineLatest, catchError, map, of, switchMap } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { WorkspaceApiService } from '../../core/api/workspace-api.service';

@Component({
  selector: 'app-client-order-detail',
  imports: [RouterLink],
  template: `
    <main class="workspace-page" role="main">
      @if (trade(); as item) {
        <h1>Order #{{ item.tradeId }}</h1>
        <p class="subtitle">Status: {{ item.status }}</p>
        <dl>
          <dt>Instrument ID</dt><dd>{{ item.instrumentId }}</dd>
          <dt>Side</dt><dd>{{ item.tradeType }}</dd>
          <dt>Quantity</dt><dd>{{ item.quantity }}</dd>
          <dt>Price</dt><dd>{{ item.price }}</dd>
          <dt>Trade date</dt><dd>{{ item.tradeDate }}</dd>
          <dt>Executed at</dt><dd>{{ item.executedAt || 'Not executed' }}</dd>
          <dt>Reason</dt><dd>{{ item.reason || 'None' }}</dd>
        </dl>
        <a routerLink="/app/orders">Back to orders</a>
      } @else {
        <h1>Order unavailable</h1>
        <p class="error">{{ error() }}</p>
      }
    </main>
  `,
})
export class ClientOrderDetailComponent {
  private readonly authService = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly api = inject(WorkspaceApiService);

  readonly trade = toSignal(
    combineLatest([
      toObservable(this.authService.clientId),
      this.route.paramMap.pipe(map((params) => Number(params.get('id') ?? 0))),
    ]).pipe(
      switchMap(([clientId, orderId]) => {
        if (clientId === null || orderId < 1) {
          return of(null);
        }
        return this.api.getClientTrade(clientId, orderId).pipe(catchError(() => of(null)));
      }),
    ),
    { initialValue: null },
  );

  readonly error = toSignal(
    combineLatest([
      toObservable(this.authService.clientId),
      this.route.paramMap.pipe(map((params) => Number(params.get('id') ?? 0))),
    ]).pipe(
      switchMap(([clientId, orderId]) => {
        if (clientId === null || orderId < 1) {
          return of('Invalid order route.');
        }
        return this.api.getClientTrade(clientId, orderId).pipe(
          map(() => ''),
          catchError(() => of('Unable to load this order.')),
        );
      }),
    ),
    { initialValue: '' },
  );
}

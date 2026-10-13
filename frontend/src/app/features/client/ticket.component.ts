import { Component, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { WorkspaceApiService } from '../../core/api/workspace-api.service';
import { toSignal } from '@angular/core/rxjs-interop';
import { catchError, map, of, switchMap } from 'rxjs';

@Component({
  selector: 'app-client-ticket',
  imports: [RouterLink],
  template: `
    <main class="workspace-page" role="main">
      @if (instrument(); as detail) {
        <h1>Order ticket · {{ detail.ticker }}</h1>
        <p class="subtitle">Use market depth and charting before submitting an order.</p>
        <p>Instrument: {{ detail.instrumentName }} ({{ detail.currency }})</p>
        <p class="notice">
          This ticket route is enabled with role-aware routing. The existing execution UI remains at the legacy trade screen
          while server-side workflow hardening is completed.
        </p>
        <a [routerLink]="['/trade', detail.ticker]">Open live trade screen</a>
      } @else {
        <h1>Order ticket unavailable</h1>
        <p class="error">{{ error() }}</p>
      }
    </main>
  `,
})
export class ClientTicketComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly api = inject(WorkspaceApiService);

  readonly instrument = toSignal(
    this.route.paramMap.pipe(
      map((params) => Number(params.get('instrumentId') ?? 0)),
      switchMap((id) => this.api.getInstrument(id).pipe(catchError(() => of(null)))),
    ),
    { initialValue: null },
  );

  readonly error = toSignal(
    this.route.paramMap.pipe(
      map((params) => Number(params.get('instrumentId') ?? 0)),
      switchMap((id) => this.api.getInstrument(id).pipe(map(() => ''), catchError(() => of('Unable to load ticket details.')))),
    ),
    { initialValue: '' },
  );
}

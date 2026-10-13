import { Component, computed, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { WorkspaceApiService } from '../../core/api/workspace-api.service';
import { toSignal } from '@angular/core/rxjs-interop';
import { catchError, map, of, switchMap } from 'rxjs';

@Component({
  selector: 'app-client-instrument-detail',
  imports: [RouterLink],
  template: `
    <main class="workspace-page" role="main">
      @if (error()) {
        <h1>Instrument unavailable</h1>
        <p class="error">{{ error() }}</p>
      } @else if (instrument(); as detail) {
        <h1>{{ detail.ticker }} · {{ detail.instrumentName }}</h1>
        <p class="subtitle">{{ detail.assetClass || 'Unclassified' }} · {{ detail.currency }}</p>
        <p>Status: <strong>{{ detail.isActive ? 'Tradable' : 'Unavailable' }}</strong></p>
        <a class="action-link" [routerLink]="['/app/trade', detail.instrumentId]">Open order ticket</a>
      } @else {
        <h1>Loading instrument</h1>
      }
    </main>
  `,
})
export class ClientInstrumentDetailComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly api = inject(WorkspaceApiService);

  private readonly instrumentId = toSignal(
    this.route.paramMap.pipe(map((params) => Number(params.get('id') ?? 0))),
    { initialValue: 0 },
  );

  readonly error = toSignal(
    this.route.paramMap.pipe(
      map((params) => Number(params.get('id') ?? 0)),
      switchMap((id) => this.api.getInstrument(id).pipe(map(() => ''), catchError(() => of('Unable to load this instrument.')))),
    ),
    { initialValue: '' },
  );

  readonly instrument = toSignal(
    this.route.paramMap.pipe(
      map((params) => Number(params.get('id') ?? 0)),
      switchMap((id) => this.api.getInstrument(id).pipe(catchError(() => of(null)))),
    ),
    { initialValue: null },
  );

  readonly ticketRoute = computed(() => ['/app/trade', this.instrumentId()]);
}

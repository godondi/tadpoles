import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { WorkspaceApiService } from '../../core/api/workspace-api.service';
import { toSignal } from '@angular/core/rxjs-interop';
import { map, catchError, of } from 'rxjs';

@Component({
  selector: 'app-client-markets',
  imports: [RouterLink],
  template: `
    <main class="workspace-page" role="main">
      <h1>Markets</h1>
      <p class="subtitle">Approved instruments and tradability status.</p>
      @if (error()) {
        <p class="error" role="alert">{{ error() }}</p>
      } @else {
        <table class="data-table">
          <caption>Instrument catalog</caption>
          <thead>
          <tr>
            <th>Symbol</th>
            <th>Name</th>
            <th>Class</th>
            <th>Currency</th>
            <th>Status</th>
          </tr>
          </thead>
          <tbody>
            @for (instrument of instruments(); track instrument.instrumentId) {
              <tr>
                <td><a [routerLink]="['/app/instruments', instrument.instrumentId]">{{ instrument.ticker }}</a></td>
                <td>{{ instrument.instrumentName }}</td>
                <td>{{ instrument.assetClass || '-' }}</td>
                <td>{{ instrument.currency }}</td>
                <td>{{ instrument.isActive ? 'Active' : 'Inactive' }}</td>
              </tr>
            }
          </tbody>
        </table>
      }
    </main>
  `,
})
export class ClientMarketsComponent {
  private readonly api = inject(WorkspaceApiService);

  readonly error = toSignal(
    this.api.listInstruments().pipe(
      map(() => ''),
      catchError(() => of('Unable to load markets right now.')),
    ),
    { initialValue: '' },
  );

  readonly instruments = toSignal(
    this.api.listInstruments().pipe(
      map((response) => response.instruments),
      catchError(() => of([])),
    ),
    { initialValue: [] },
  );
}

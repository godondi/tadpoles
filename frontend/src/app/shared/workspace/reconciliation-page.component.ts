import { Component, PLATFORM_ID, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { buildApiUrl } from '../../core/api/api-url';
import { PageResource } from './page-resource';
import { RequestStateComponent } from './request-state.component';
interface Reconciliation {
  runId: string | null;
  checkedAt?: string;
  matched?: boolean;
  exceptions?: { kind: string; reference: string }[];
  historicalExceptions?: number;
  message?: string;
  scope?: string;
}
@Component({
  selector: 'app-reconciliation-page',
  imports: [DatePipe, RequestStateComponent],
  template: `<header class="workspace-heading">
      <p class="eyebrow">FINANCE</p>
      <h1 tabindex="-1">Reconciliation</h1>
      <p>
        Compare committed cash, units, reservations and posting evidence in one consistent snapshot.
      </p>
    </header>
    <button type="button" [disabled]="resource.loading()" (click)="run()">
      Run reconciliation
    </button>
    <app-request-state
      [loading]="resource.loading()"
      [error]="resource.error()"
      [status]="resource.status()"
      (retry)="load()"
    />
    @if (resource.data(); as report) {
      <section class="panel" aria-live="polite">
        @if (report.runId) {
          <h2>
            {{ report.matched ? 'No unexplained differences' : 'Differences require review' }}
          </h2>
          <p>Run {{ report.runId }} · {{ report.checkedAt | date: 'medium' : 'UTC' }} UTC</p>
          <p>Preserved historical migration exceptions: {{ report.historicalExceptions }}</p>
          @if (report.exceptions?.length) {
            <table>
              <caption>
                Investigate these references before further financial activity
              </caption>
              <thead>
                <tr>
                  <th>Difference</th>
                  <th>Reference</th>
                </tr>
              </thead>
              <tbody>
                @for (item of report.exceptions; track $index) {
                  <tr>
                    <td>{{ item.kind }}</td>
                    <td>{{ item.reference }}</td>
                  </tr>
                }
              </tbody>
            </table>
          }
          <p>{{ report.scope }}</p>
        } @else {
          <p>{{ report.message }}</p>
        }
      </section>
    }
    <p class="notice">
      This check records evidence and does not repair balances. It compares internal records; it
      does not confirm external bank transfers or custody. Historical migration exceptions remain a
      separate review obligation.
    </p>`,
})
export class ReconciliationPageComponent {
  private readonly http = inject(HttpClient);
  private readonly url = buildApiUrl('/api/finance/reconciliations', inject(PLATFORM_ID));
  readonly resource = new PageResource<Reconciliation>();

  constructor() {
    this.load();
  }

  load() {
    this.resource.load(this.http.get<Reconciliation>(this.url));
  }

  run() {
    this.resource.load(this.http.post<Reconciliation>(this.url, {}));
  }
}

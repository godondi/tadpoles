import { Component, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { WorkspaceApiService } from '../../core/api/workspace-api.service';
import { toSignal } from '@angular/core/rxjs-interop';
import { catchError, map, of, switchMap } from 'rxjs';

@Component({
  selector: 'app-compliance-investigation-detail',
  imports: [RouterLink],
  template: `
    <main class="workspace-page" role="main">
      @if (evidence(); as item) {
        <h1>Evidence for order #{{ item.trade.tradeId }}</h1>
        <p>Status: {{ item.trade.status }}</p>
        <p>Client reference: {{ item.trade.clientId }}</p>
        <h2>Audit trail</h2>
        <ul>
          @for (entry of item.auditLogs; track entry.auditLogId) {
            <li>{{ entry.createdAt }} · {{ entry.entityName }} · {{ entry.actionType }}</li>
          }
        </ul>
        @if (item.auditLogs.length === 0) {
          <p>No audit entries were recorded for this order.</p>
        }
        <a routerLink="/compliance/investigations">Back to investigations</a>
      } @else {
        <h1>Evidence unavailable</h1>
        <p class="error">{{ error() }}</p>
      }
    </main>
  `,
})
export class ComplianceInvestigationDetailComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly api = inject(WorkspaceApiService);

  readonly evidence = toSignal(
    this.route.paramMap.pipe(
      map((params) => Number(params.get('id') ?? 0)),
      switchMap((id) => this.api.getComplianceEvidence(id).pipe(catchError(() => of(null)))),
    ),
    { initialValue: null },
  );

  readonly error = toSignal(
    this.route.paramMap.pipe(
      map((params) => Number(params.get('id') ?? 0)),
      switchMap((id) => this.api.getComplianceEvidence(id).pipe(map(() => ''), catchError(() => of('Unable to load evidence.')))),
    ),
    { initialValue: '' },
  );
}

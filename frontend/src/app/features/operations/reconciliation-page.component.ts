import { RequestStateComponent } from '../../shared/workspace/request-state.component';
import { Component, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  OperationsApi,
  ReconciliationView,
  EvidencePage,
} from '../../core/api/operations-api.service';
import { OperationsContextComponent } from './operations-context.component';
import { OperationsResource } from './operations-resource';
import { EvidenceTableComponent } from './evidence-table.component';

@Component({
  selector: 'app-operations-reconciliation',
  imports: [
    RequestStateComponent,
    DatePipe,
    RouterLink,
    OperationsContextComponent,
    EvidenceTableComponent,
  ],
  template: `
    <header class="workspace-heading">
      <p class="eyebrow">TRADING OPERATIONS</p>
      <h1 tabindex="-1">Read-only reconciliation</h1>
      <p>Stored internal-record consistency checks. Finance runs reconciliation.</p>
    </header>
    <app-operations-context />
    @if (api.context()) {
      <button type="button" (click)="load()" [disabled]="resource.loading()">
        Read latest stored run
      </button>
      <app-request-state
        [loading]="resource.loading() && !resource.data()"
        [error]="resource.message()"
        [status]="resource.status()"
        (retry)="load()"
      />
      @if (resource.data(); as run) {
        <section class="panel">
          <h2>{{ run.status }}</h2>
          <p>
            Stored at {{ run.checkedAt ? (run.checkedAt | date: 'medium' : 'UTC') : 'Unknown' }} UTC
          </p>
          <p>{{ run.coverage }}</p>
          <app-evidence-table [rows]="run.items" caption="Permitted reconciliation findings" />
          @for (finding of run.items; track finding.reference) {
            @if (finding.values['orderId']; as order) {
              <p>
                <a [routerLink]="['/operations/orders', order]">Investigate order {{ order }}</a>
              </p>
            }
          }
          <div class="actions">
            <button
              type="button"
              (click)="page(-1)"
              [disabled]="run.page === 0 || resource.loading()"
            >
              Previous</button
            ><button
              type="button"
              (click)="page(1)"
              [disabled]="!run.hasMore || resource.loading()"
            >
              Next
            </button>
          </div>
        </section>
      }
      <section class="panel">
        <h2>Historical migration review</h2>
        <p>
          Historical quarantined records are separate from current reconciliation. Viewing does not
          approve or release balances. Original details remain restricted.
        </p>
        @if (migration.message()) {
          <p role="alert">{{ migration.message() }}</p>
        }
        @if (migration.data(); as records) {
          <app-evidence-table [rows]="records.items" caption="Permitted historical exceptions" />
          <div class="actions">
            <button (click)="migrationPage(-1)" [disabled]="records.page === 0">Previous</button
            ><button (click)="migrationPage(1)" [disabled]="!records.hasMore">Next</button>
          </div>
        }
      </section>
    }
  `,
})
export class OperationsReconciliationComponent {
  readonly api = inject(OperationsApi);
  readonly resource = new OperationsResource<ReconciliationView>();
  readonly migration = new OperationsResource<EvidencePage>();
  constructor() {
    this.api.changed.pipe(takeUntilDestroyed()).subscribe(() => this.load());
    this.load();
  }
  load() {
    if (this.api.context()) {
      this.resource.load(this.api.reconciliation());
      this.migration.load(this.api.migration());
    }
  }
  page(delta: number) {
    const run = this.resource.data();
    if (run?.runId) this.resource.load(this.api.reconciliation(run.runId, run.page + delta));
  }
  migrationPage(delta: number) {
    this.migration.load(this.api.migration((this.migration.data()?.page ?? 0) + delta));
  }
}

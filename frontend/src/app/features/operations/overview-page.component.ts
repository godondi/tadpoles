import { RequestStateComponent } from '../../shared/workspace/request-state.component';
import { Component, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { OperationsApi, Readiness } from '../../core/api/operations-api.service';
import { CatalogRefreshService } from '../../core/api/catalog-refresh.service';
import { OperationsResource } from './operations-resource';

@Component({
  selector: 'app-operations-overview',
  imports: [RequestStateComponent, DatePipe, RouterLink],
  template: `
    <header class="workspace-heading">
      <p class="eyebrow">TRADING OPERATIONS</p>
      <h1 tabindex="-1">Operations overview</h1>
      <p>Configuration and observed health of this application instance.</p>
    </header>
    <div class="actions">
      <button type="button" (click)="load()" [disabled]="resource.loading()">Refresh health</button
      ><a routerLink="/operations/exceptions">Inspect exception queue</a
      ><a routerLink="/operations/reconciliation">Read reconciliation</a>
    </div>
    <app-request-state
      [loading]="resource.loading() && !resource.data()"
      [error]="resource.message()"
      [status]="resource.status()"
      (retry)="load()"
    />
    @if (resource.stale()) {
      <p class="notice" role="status">Health information is stale.</p>
    }
    @if (resource.data(); as data) {
      <p>
        Checked {{ data.checkedAt | date: 'medium' : 'UTC' }} UTC. Refresh does not call the market
        provider.
      </p>
      <section class="panel">
        <h2>Configuration</h2>
        <dl class="details">
          <div>
            <dt>Schema / reference data</dt>
            <dd>{{ data.schemaReady && data.referenceDataReady ? 'READY' : 'DEGRADED' }}</dd>
          </div>
          <div>
            <dt>Admission</dt>
            <dd>{{ data.admissionEnabled ? 'Enabled' : 'Disabled' }}</dd>
          </div>
          <div>
            <dt>Provider key configured</dt>
            <dd>{{ data.providerConfigured ? 'Yes — configuration only' : 'No' }}</dd>
          </div>
          <div>
            <dt>Enabled instrument policies</dt>
            <dd>{{ data.enabledPolicies }}</dd>
          </div>
          <div>
            <dt>Runtime operational access</dt>
            <dd>{{ data.runtimePermissions.operationalAccess ? 'Available' : 'Unavailable' }}</dd>
          </div>
          <div>
            <dt>Evidence protected from runtime mutation</dt>
            <dd>
              {{
                data.runtimePermissions.evidenceProtected
                  ? 'Verified'
                  : 'DEGRADED — review database identity and grants'
              }}
            </dd>
          </div>
          <div>
            <dt>External notification delivery</dt>
            <dd>{{ data.outboxDelivery }}</dd>
          </div>
        </dl>
      </section>
      <section class="panel">
        <h2>Worker · {{ data.workerHealth.status }}</h2>
        <p>{{ data.workerHealth.scope }}</p>
        <p>
          Last poll:
          {{
            data.workerHealth.lastPollAt
              ? (data.workerHealth.lastPollAt | date: 'medium' : 'UTC')
              : 'Unknown'
          }}
          UTC
        </p>
        <p>
          Last completed poll:
          {{
            data.workerHealth.lastCompletedPollAt
              ? (data.workerHealth.lastCompletedPollAt | date: 'medium' : 'UTC')
              : 'Unknown'
          }}
          UTC
        </p>
        <p>
          Failed jobs observed by this instance: {{ data.workerHealth.failedJobs }}. A completed
          poll can include failed jobs.
        </p>
      </section>
      <section class="panel">
        <h2>Provider transport · {{ data.providerHealth.status }}</h2>
        <p>{{ data.providerHealth.scope }}</p>
        <p>
          Last observation:
          {{
            data.providerHealth.lastObservedAt
              ? (data.providerHealth.lastObservedAt | date: 'medium' : 'UTC')
              : 'Unknown'
          }}
          UTC
        </p>
        <p>{{ data.providerHealth.failureCategory ?? 'No current failure category' }}</p>
        <p>{{ data.providerHealth.note }}</p>
      </section>
      <p>{{ data.note }}</p>
    }
  `,
})
export class OperationsOverviewComponent {
  private readonly api = inject(OperationsApi);
  readonly resource = new OperationsResource<Readiness>();
  constructor() {
    this.load();
    inject(CatalogRefreshService)
      .watch()
      .pipe(takeUntilDestroyed())
      .subscribe(() => {
        if (!this.resource.loading()) this.load();
      });
  }
  load() {
    this.resource.load(this.api.readiness(), true);
  }
}

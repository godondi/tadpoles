import { RequestStateComponent } from '../../shared/workspace/request-state.component';
import { Subject, merge, takeUntil } from 'rxjs';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { DatePipe, JsonPipe, KeyValuePipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { OperationsApi, Investigation, ExportReceipt } from '../../core/api/operations-api.service';
import { CatalogRefreshService } from '../../core/api/catalog-refresh.service';
import { OperationsContextComponent } from './operations-context.component';
import { EvidenceTableComponent } from './evidence-table.component';
import { OperationsResource } from './operations-resource';
import { createIdempotencyKey } from '../../core/idempotency-key';

@Component({
  selector: 'app-operations-investigation',
  imports: [
    RequestStateComponent,
    DatePipe,
    JsonPipe,
    KeyValuePipe,
    RouterLink,
    OperationsContextComponent,
    EvidenceTableComponent,
  ],
  template: `
    <header class="workspace-heading">
      <p class="eyebrow">TRADING OPERATIONS</p>
      <h1 tabindex="-1">Order investigation</h1>
      <a [routerLink]="backPath" [queryParams]="backQuery">Back to monitoring</a>
    </header>
    <app-operations-context />
    <app-request-state
      [loading]="resource.loading() && !resource.data()"
      [error]="resource.message()"
      [status]="resource.status()"
      (retry)="load()"
    />
    @if (resource.stale()) {
      <p class="notice" role="status">Evidence is stale; refresh failed.</p>
    }
    @if (resource.data(); as detail) {
      <section class="panel">
        <h2>Order {{ detail.order.orderId }} · {{ detail.order.status }}</h2>
        <p>
          {{ detail.order.side }} {{ detail.order.quantity }} {{ detail.order.ticker }} ·
          {{ detail.order.currency }} · {{ detail.order.executionMode }}
        </p>
        <p>
          {{ detail.order.accountReference }} · submitted by {{ detail.submittedActor }} · version
          {{ detail.order.version }}
        </p>
        <p>
          Submitted
          {{
            detail.order.submittedAt
              ? (detail.order.submittedAt | date: 'medium' : 'UTC')
              : 'time not recorded'
          }}
          UTC.<br />
          {{
            detail.order.acceptedAt
              ? 'Accepted ' + (detail.order.acceptedAt | date: 'medium' : 'UTC') + ' UTC'
              : 'No recorded acceptance. An admission rejection was never accepted.'
          }}
        </p>
        <p>{{ detail.order.reason }} {{ detail.order.rejectionCode }}</p>
        <p>
          Snapshot {{ detail.snapshotAt | date: 'medium' : 'UTC' }} UTC ·
          {{ detail.evidenceStatus }}
        </p>
        @for (finding of detail.findings; track $index) {
          <p role="status">{{ finding.code }}: {{ finding.explanation }}</p>
        }
        <p>{{ detail.orderingNote }} {{ detail.historicalContext }}</p>
        <p>
          Internal execution references identify internal booking. Linked journal deltas are exact
          changes, not historical account balances.
        </p>
        <button type="button" (click)="load()" [disabled]="resource.loading()">
          Refresh investigation
        </button>
        <a routerLink="/operations/reconciliation">Open permitted reconciliation findings</a>
      </section>
      <section class="panel">
        <h2>Policy at acceptance and current controls</h2>
        <details>
          <summary>Retained policy at admission</summary>
          <pre class="evidence-json">{{ detail.acceptedPolicy | json }}</pre>
        </details>
        <details>
          <summary>Current controls (may have changed)</summary>
          <pre class="evidence-json">{{ detail.currentPolicy | json }}</pre>
        </details>
        <a [routerLink]="['/operations/instruments', detail.order.instrumentId]"
          >Instrument and change history</a
        >
      </section>
      @for (section of detail.sections | keyvalue; track section.key) {
        <section class="panel">
          <h2>{{ label(section.key) }}</h2>
          <app-evidence-table [rows]="section.value.items" [caption]="label(section.key)" />
          <div class="actions">
            <button
              type="button"
              (click)="page(section.key, section.value.page - 1)"
              [disabled]="section.value.page === 0 || paging()"
            >
              Previous
            </button>
            <span>Page {{ section.value.page + 1 }}</span
            ><button
              type="button"
              (click)="page(section.key, section.value.page + 1)"
              [disabled]="!section.value.hasMore || paging()"
            >
              Next
            </button>
          </div>
        </section>
      }
      <section class="panel">
        <h2>Evidence export</h2>
        <p>
          Single-order ZIP: retained instruction, quotes, decisions, attempts, reservations, fills,
          journal deltas and notification facts, plus a SHA-256 manifest. Bank details, credentials,
          profiles and raw idempotency keys are excluded. Policy history is included. Related
          reconciliation is included only with its separate account permission; coverage is stated
          in the package.
        </p>
        @if (detail.exportAllowed) {
          <p>
            Confirm that this package is needed for your current recorded purpose. Download
            authorization is checked again and recorded.
          </p>
          <button type="button" (click)="export()" [disabled]="exporting()">
            Confirm purpose and prepare export
          </button>
        } @else {
          <p>Export requires a separate account permission.</p>
        }
        @if (receipt(); as file) {
          <p>Ready · expires {{ file.expiresAt | date: 'medium' : 'UTC' }} UTC</p>
          <p>
            Archive SHA-256: <code>{{ file.digest }}</code>
          </p>
          <button type="button" (click)="download(file)" [disabled]="exporting()">
            Download evidence ZIP
          </button>
          @if(api.context()?.referenceType==='MANAGED') {<button type="button" (click)="associate(file)" [disabled]="exporting()">Associate export with {{api.context()?.caseReference}}</button>}
        }
        @if (actionMessage()) {
          <p role="alert">{{ actionMessage() }}</p>
        }
      </section>
    }
  `,
})
export class OperationsOrderDetailComponent {
  readonly api = inject(OperationsApi);
  private readonly route = inject(ActivatedRoute);
  private readonly destroy = inject(DestroyRef);
  readonly resource = new OperationsResource<Investigation>();
  readonly receipt = signal<ExportReceipt | null>(null);
  readonly actionMessage = signal('');
  readonly exporting = signal(false);
  readonly paging = signal(false);
  private id = 0;
  private exportKey = '';
  private readonly routeChanged = new Subject<void>();
  private readonly cancel = merge(this.api.changed, this.routeChanged);
  backPath = '/operations/orders';
  backQuery: Record<string, string> = {};
  constructor() {
    const back = this.route.snapshot.queryParamMap.get('returnTo');
    if (back && /^\/operations\/(orders|exceptions)(\?|$)/.test(back)) {
      const [path, query] = back.split('?');
      this.backPath = path;
      const allowed = ['status', 'side', 'recordKind', 'rejectionStage', 'queue', 'from', 'to'];
      this.backQuery = Object.fromEntries(
        [...new URLSearchParams(query)].filter(([key]) => allowed.includes(key)),
      );
    }
    this.route.paramMap.pipe(takeUntilDestroyed()).subscribe((params) => {
      this.routeChanged.next();
      this.id = Number(params.get('id'));
      this.exporting.set(false);
      this.paging.set(false);
      this.resource.clear();
      this.receipt.set(null);
      this.exportKey = '';
      this.load();
    });
    this.api.changed.pipe(takeUntilDestroyed()).subscribe(() => {
      this.receipt.set(null);
      this.actionMessage.set('');
      this.exporting.set(false);
      this.paging.set(false);
      this.exportKey = '';
      this.load();
    });
    inject(CatalogRefreshService)
      .watch()
      .pipe(takeUntilDestroyed())
      .subscribe(() => {
        const evidence = this.resource.data();
        const firstPages =
          evidence && Object.values(evidence.sections).every((page) => page.page === 0);
        if (!this.resource.loading() && !this.paging() && firstPages) this.load(true);
      });
  }
  label(value: string) {
    return value.charAt(0).toUpperCase() + value.slice(1);
  }
  load(preserve = false) {
    if (this.api.context()) this.resource.load(this.api.detail(this.id), preserve);
  }
  page(section: string, page: number) {
    this.paging.set(true);
    this.api
      .section(this.id, section, page)
      .pipe(takeUntil(this.cancel), takeUntilDestroyed(this.destroy))
      .subscribe({
        next: (data) => {
          this.resource.data.update((old) =>
            old ? { ...old, sections: { ...old.sections, [section]: data } } : null,
          );
          this.paging.set(false);
        },
        error: () => {
          this.actionMessage.set('Unable to load the evidence page.');
          this.paging.set(false);
        },
      });
  }
  export() {
    this.exporting.set(true);
    this.actionMessage.set('');
    if (!this.exportKey) {
      const key = createIdempotencyKey();
      if (!key) {
        this.actionMessage.set('Unable to create a secure export reference. Please reload and try again.');
        this.exporting.set(false);
        return;
      }
      this.exportKey = key;
    }
    this.api
      .createExport(this.id, this.exportKey)
      .pipe(takeUntil(this.cancel), takeUntilDestroyed(this.destroy))
      .subscribe({
        next: (receipt) => {
          this.receipt.set(receipt);
          this.exporting.set(false);
        },
        error: (error) => {
          this.actionMessage.set(error.error?.message ?? 'Export unavailable.');
          this.exporting.set(false);
          if ([409, 410].includes(error.status)) this.exportKey = '';
        },
      });
  }
  download(receipt: ExportReceipt) {
    this.exporting.set(true);
    this.api
      .download(receipt.exportId)
      .pipe(takeUntil(this.cancel), takeUntilDestroyed(this.destroy))
      .subscribe({
        next: (blob) => {
          const url = URL.createObjectURL(blob);
          const anchor = document.createElement('a');
          anchor.href = url;
          anchor.download = `order-evidence-${receipt.exportId}.zip`;
          anchor.click();
          setTimeout(() => URL.revokeObjectURL(url), 1000);
          this.exporting.set(false);
        },
        error: () => {
          this.actionMessage.set(
            'Download denied, expired or unavailable. Request a new package if needed.',
          );
          this.exporting.set(false);
          this.exportKey = '';
        },
      });
  }
  associate(file:ExportReceipt) {
    this.exporting.set(true);this.actionMessage.set('');
    this.api.associateCaseExport(file.exportId).pipe(takeUntil(this.cancel),takeUntilDestroyed(this.destroy)).subscribe({next:()=>{this.exporting.set(false);this.actionMessage.set('Evidence association retained. The original export permissions and expiry still apply.');},error:error=>{this.exporting.set(false);this.actionMessage.set(error.error?.message??'Unable to associate this export.');}});
  }
}

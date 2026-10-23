import { Component, DestroyRef, inject, signal } from '@angular/core';
import { DatePipe, JsonPipe, KeyValuePipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Observable, Subject, takeUntil } from 'rxjs';
import { ComplianceApi } from './compliance-api.service';
import { ComplianceContextComponent } from './compliance-context.component';
import { ComplianceResource } from './compliance-resource';
import {
  ComplianceEvidenceSection,
  ComplianceInvestigationDetail,
  ComplianceExportRecord,
} from './compliance.models';
import { RequestStateComponent } from '../../shared/workspace/request-state.component';
import { createIdempotencyKey } from '../../core/idempotency-key';
import {
  ExportRecovery,
  downloadServerArchive,
  readExportRecovery,
  saveExportRecovery,
} from './export-recovery';
import { restoreSearch, safeSearchQuery } from './search-state';
import { EvidenceFactsComponent } from '../../shared/workspace/evidence-facts.component';

@Component({
  selector: 'app-compliance-investigation-detail',
  imports: [
    DatePipe,
    JsonPipe,
    KeyValuePipe,
    RouterLink,
    RequestStateComponent,
    ComplianceContextComponent,
    EvidenceFactsComponent,
  ],
  template: `
    <header class="workspace-heading">
      <p class="eyebrow">RISK &amp; COMPLIANCE</p>
      <h1 tabindex="-1">Investigation details</h1>
      <a [routerLink]="['/compliance/investigations']" [queryParams]="backQuery">
        Back to investigations
      </a>
    </header>
    <app-compliance-context />
    @if (invalidId()) {
      <p class="notice" role="alert">The investigation reference must be a positive order ID.</p>
    }
    @if (api.context() && !invalidId()) {
      <app-request-state
        [loading]="resource.loading()"
        [error]="resource.error()"
        [status]="resource.status()"
        (retry)="load()"
      />
      @if (resource.data(); as detail) {
        <section class="panel">
          <h2>Order {{ detail.summary.orderId }} · {{ detail.summary.status }}</h2>
          <p>
            {{ detail.summary.side }} {{ detail.summary.quantity }} {{ detail.summary.unit }} ·
            {{ detail.summary.instrument }}
          </p>
          <dl class="details">
            <div>
              <dt>Safe account reference</dt>
              <dd>{{ detail.accountReference }}</dd>
            </div>
            <div>
              <dt>Authenticated actor</dt>
              <dd>{{ detail.authenticatedActor }}</dd>
            </div>
            <div>
              <dt>Record kind</dt>
              <dd>{{ detail.summary.recordKind }}</dd>
            </div>
            <div>
              <dt>Execution mode</dt>
              <dd>{{ detail.summary.executionMode }}</dd>
            </div>
            <div>
              <dt>Submitted (UTC)</dt>
              <dd>{{ time(detail.summary.submittedAt) }}</dd>
            </div>
            <div>
              <dt>Accepted (UTC)</dt>
              <dd>{{ time(detail.summary.acceptedAt) }}</dd>
            </div>
            <div>
              <dt>Outcome (UTC)</dt>
              <dd>{{ time(detail.summary.outcomeAt) }}</dd>
            </div>
            <div>
              <dt>Evidence completeness</dt>
              <dd>{{ detail.summary.evidenceCompleteness }}</dd>
            </div>
          </dl>
          <p>Snapshot {{ detail.snapshotAt | date: 'medium' : 'UTC' }} UTC.</p>
          @for (finding of detail.findings; track $index) {
            <p class="evidence-status" [attr.data-status]="finding.status">
              {{ finding.status }} · {{ finding.code }}: {{ finding.explanation }}
            </p>
          }
          @for (limitation of detail.limitations; track limitation) {
            <p class="notice">{{ limitation }}</p>
          }
        </section>
        <section class="panel">
          <h2>Accepted policy and current controls</h2>
          <p>
            Current controls must not be read as the policy that governed the historical decision.
          </p>
          <h3>Policy at acceptance</h3>
          @if (detail.acceptedPolicy) {
            <app-evidence-facts [value]="detail.acceptedPolicy" />
            <details>
              <summary>Accepted policy raw JSON</summary>
              <pre class="evidence-json">{{ detail.acceptedPolicy | json }}</pre>
            </details>
          } @else {
            <p>NOT_EVALUATED: accepted policy was not retained.</p>
          }
          <h3>Current policy</h3>
          @if (detail.currentPolicy) {
            <app-evidence-facts [value]="detail.currentPolicy" />
            <details>
              <summary>Current policy raw JSON</summary>
              <pre class="evidence-json">{{ detail.currentPolicy | json }}</pre>
            </details>
          } @else {
            <p>UNKNOWN: current policy was not supplied.</p>
          }
        </section>
        <nav class="panel" aria-label="Evidence sections">
          <h2>Evidence section index</h2>
          <div class="actions">
            @for (section of detail.sections; track section.name) {
              <a [attr.href]="'#compliance-evidence-' + section.name"
                >{{ section.name }} / {{ section.completeness }} / {{ section.total }} records</a
              >
            }
          </div>
        </nav>
        @for (section of detail.sections; track section.name) {
          <section
            class="panel"
            [id]="'compliance-evidence-' + section.name"
            [attr.aria-label]="section.name"
          >
            <div class="section-heading">
              <h2>{{ section.name }}</h2>
              <span class="evidence-status" [attr.data-status]="section.completeness">
                {{ section.completeness }}
              </span>
            </div>
            <p>
              {{ section.coverage }} · source {{ section.source }} · as of
              {{ section.asOf | date: 'medium' : 'UTC' }} UTC
            </p>
            <div
              class="table-wrapper"
              tabindex="0"
              [attr.aria-label]="section.name + ', scroll horizontally'"
            >
              <table>
                <caption>
                  {{
                    section.name
                  }}
                  retained records
                </caption>
                <thead>
                  <tr>
                    <th scope="col">Reference</th>
                    <th scope="col">Sequence</th>
                    <th scope="col">Actor</th>
                    <th scope="col">Recorded (UTC)</th>
                    <th scope="col">Retained facts</th>
                  </tr>
                </thead>
                <tbody>
                  @for (record of section.items; track record.reference) {
                    <tr>
                      <td>{{ record.reference }}</td>
                      <td>{{ record.sequence ?? 'Not recorded' }}</td>
                      <td>{{ record.actor ?? 'Not recorded' }}</td>
                      <td>{{ time(record.occurredAt) }}</td>
                      <td>
                        <dl class="details">
                          @for (field of record.values | keyvalue; track field.key) {
                            <div>
                              <dt>{{ label(field.key) }}</dt>
                              <dd>{{ field.value ?? 'Not recorded' }}</dd>
                            </div>
                          }
                        </dl>
                      </td>
                    </tr>
                  } @empty {
                    <tr>
                      <td colspan="5">No retained records in this section.</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
            <div class="pagination">
              <span>Page {{ section.page + 1 }} · {{ section.total }} scoped records</span>
              <button
                type="button"
                (click)="page(section, section.page - 1)"
                [disabled]="section.page === 0 || sectionLoading(section.name)"
              >
                Previous
              </button>
              <button
                type="button"
                (click)="page(section, section.page + 1)"
                [disabled]="!section.hasMore || sectionLoading(section.name)"
              >
                Next
              </button>
            </div>
            @if (sectionError(section.name)) {
              <p class="notice" role="alert">{{ sectionError(section.name) }}</p>
            }
          </section>
        }
        <section class="panel">
          <h2>Evidence exports</h2>
          @if (detail.exportAllowed) {
            <p>
              Export requests are deliberate, scoped and re-authorized. The server must return a
              receipt and manifest; displayed rows are never converted into a local file.
            </p>
            <button
              type="button"
              (click)="requestExport(detail.summary.orderId)"
              [disabled]="exporting() || recoveryBlocked()"
            >
              {{ recovery() ? 'Recover original export request' : 'Request authorized export' }}
            </button>
          } @else {
            <p>
              Export is disabled because an approved export entitlement, redaction policy and
              retention contract were not supplied by the server.
            </p>
          }
          @if (!receipt() && recovery()?.reference; as reference) {
            <button type="button" (click)="recoverReceipt(reference)" [disabled]="exporting()">
              Recover server receipt
            </button>
          }
          @if (receipt(); as receipt) {
            <p>Export {{ receipt.requestReference }} · {{ receipt.state }}</p>
            @if (receipt.manifest; as manifest) {
              <p>
                Server manifest: {{ manifest.schemaVersion }} · {{ manifest.verification }} · digest
                {{ manifest.contentDigest }}
              </p>
            }
            @if (receipt.authorizedActions.includes('RECOVER_RECEIPT')) {
              <button
                type="button"
                (click)="recoverReceipt(receipt.requestReference)"
                [disabled]="exporting()"
              >
                Refresh server receipt
              </button>
            }
            @if (receipt.authorizedActions.includes('DOWNLOAD')) {
              <button type="button" (click)="download(receipt)" [disabled]="exporting()">
                Download authorized archive
              </button>
            }
          }
          @if (exportMessage()) {
            <p class="notice" role="alert">{{ exportMessage() }}</p>
          }
        </section>
      }
    }
  `,
  styles: [
    `
      .details {
        display: grid;
        gap: 0.65rem;
        grid-template-columns: repeat(auto-fit, minmax(14rem, 1fr));
      }
      .details div {
        border-bottom: 1px solid var(--border-gray);
        padding-bottom: 0.4rem;
      }
      dt {
        color: var(--text-secondary);
        font-size: 0.85rem;
      }
      dd {
        margin: 0.2rem 0 0;
      }
      .section-heading {
        display: flex;
        align-items: baseline;
        justify-content: space-between;
        gap: 1rem;
      }
      .evidence-status {
        font-family: var(--font-family-mono);
        font-weight: 700;
      }
      .evidence-status[data-status='FAIL'] {
        color: var(--negative);
      }
      .evidence-status[data-status='PASS'] {
        color: var(--positive);
      }
      .pagination {
        display: flex;
        align-items: center;
        gap: 1rem;
        margin-top: 1rem;
      }
    `,
  ],
})
export class ComplianceInvestigationDetailComponent {
  readonly api = inject(ComplianceApi);
  readonly resource = new ComplianceResource<ComplianceInvestigationDetail>();
  readonly sectionStates = signal<Record<string, { loading: boolean; error: string }>>({});
  readonly invalidId = signal(false);
  readonly exporting = signal(false);
  readonly exportMessage = signal('');
  readonly receipt = signal<ComplianceExportRecord | null>(null);
  readonly recovery = signal<ExportRecovery | null>(null);
  readonly recoveryBlocked = signal(false);
  private readonly route = inject(ActivatedRoute);
  private readonly destroy = inject(DestroyRef);
  private id = 0;
  private generation = 0;
  private readonly cancel = new Subject<void>();
  backQuery: Record<string, string> = {};

  constructor() {
    try {
      this.backQuery = safeSearchQuery(restoreSearch(this.route.snapshot.queryParamMap));
    } catch {
      this.backQuery = {};
    }
    this.destroy.onDestroy(() => {
      this.cancel.next();
      this.cancel.complete();
    });
    this.route.paramMap.pipe(takeUntilDestroyed()).subscribe((params) => {
      const raw = params.get('id') ?? '';
      this.id = /^[1-9]\d*$/.test(raw) ? Number(raw) : 0;
      this.invalidId.set(!this.id || !Number.isSafeInteger(this.id));
      this.load();
    });
    this.api.changed.pipe(takeUntilDestroyed()).subscribe(() => {
      this.load();
    });
  }

  load() {
    this.generation++;
    this.cancel.next();
    this.sectionStates.set({});
    this.exporting.set(false);
    this.exportMessage.set('');
    this.receipt.set(null);
    const actor = this.api.actorKey?.();
    this.recovery.set(null);
    this.recoveryBlocked.set(false);
    try {
      if (actor && this.id > 0) this.recovery.set(readExportRecovery(actor, this.id));
    } catch (error) {
      this.recoveryBlocked.set(true);
      this.exportMessage.set((error as Error).message);
    }
    if (this.invalidId()) {
      this.resource.clear();
      this.resource.error.set('The investigation reference must be a positive order ID.');
      this.resource.status.set(400);
      return;
    }
    if (!this.api.context()) {
      this.resource.clear();
      return;
    }
    this.resource.load(this.api.investigation(this.id));
  }

  page(section: ComplianceEvidenceSection, page: number) {
    if (
      !this.api.context() ||
      !this.resource.data() ||
      this.sectionLoading(section.name) ||
      page < 0 ||
      (!section.hasMore && page > section.page)
    )
      return;
    this.setSection(section.name, true);
    this.action(
      this.api.section(this.id, section.name, page),
      (updated) => {
        this.setSection(section.name, false);
        const detail = this.resource.data();
        if (detail)
          this.resource.data.set({
            ...detail,
            sections: detail.sections.map((current) =>
              current.name === updated.name ? updated : current,
            ),
          });
      },
      () =>
        this.setSection(section.name, false, 'Unable to load this evidence section. Try again.'),
    );
  }

  requestExport(orderId: number) {
    if (
      this.exporting() ||
      this.recoveryBlocked() ||
      orderId !== this.id ||
      !this.api.context() ||
      !this.resource.data()?.exportAllowed
    )
      return;
    const actor = this.api.actorKey?.();
    const key = this.recovery()?.key ?? createIdempotencyKey();
    if (!key || !actor) {
      this.exportMessage.set('This browser cannot create a safe export request key.');
      return;
    }
    const recovery: ExportRecovery = this.recovery() ?? {
      actor,
      order: orderId,
      action: 'EXPORT_CREATE',
      key,
    };
    try {
      saveExportRecovery(recovery);
      this.recovery.set(recovery);
    } catch {
      this.exportMessage.set('Export recovery storage is unavailable. No request was sent.');
      return;
    }
    this.exporting.set(true);
    this.exportMessage.set('');
    this.action(
      this.api.createExport(orderId, key),
      (receipt) => {
        this.exporting.set(false);
        this.receipt.set(receipt);
        const saved = { ...recovery, reference: receipt.requestReference };
        this.recovery.set(saved);
        try {
          saveExportRecovery(saved);
          this.exportMessage.set(`Export ${receipt.requestReference} is ${receipt.state}.`);
        } catch {
          this.exportMessage.set(
            'The server returned a receipt, but its reference could not be saved. The original request key remains available for recovery.',
          );
        }
      },
      (error) => this.exportFailure(error),
    );
  }

  recoverReceipt(reference: string) {
    const receipt = this.receipt();
    if (
      this.exporting() ||
      !this.api.context() ||
      (!(
        receipt?.requestReference === reference &&
        receipt.authorizedActions.includes('RECOVER_RECEIPT')
      ) &&
        !(this.recovery()?.reference === reference && !receipt))
    )
      return;
    this.exporting.set(true);
    this.action(
      this.api.receipt(reference),
      (receipt) => {
        this.receipt.set(receipt);
        this.exporting.set(false);
        this.exportMessage.set('');
      },
      (error) => this.exportFailure(error),
    );
  }

  download(receipt: ComplianceExportRecord) {
    if (
      this.exporting() ||
      this.receipt()?.requestReference !== receipt.requestReference ||
      !receipt.authorizedActions.includes('DOWNLOAD') ||
      !this.api.context()
    )
      return;
    this.exporting.set(true);
    this.action(
      this.api.downloadExport(receipt.requestReference),
      (blob) => {
        this.exporting.set(false);
        downloadServerArchive(blob, receipt.requestReference);
      },
      (error) => this.exportFailure(error),
    );
  }

  sectionLoading(name: string) {
    return this.sectionStates()[name]?.loading ?? false;
  }
  sectionError(name: string) {
    return this.sectionStates()[name]?.error ?? '';
  }
  private setSection(name: string, loading: boolean, error = '') {
    this.sectionStates.update((states) => ({ ...states, [name]: { loading, error } }));
  }
  private action<T>(request: Observable<T>, next: (value: T) => void, error: (error: any) => void) {
    const generation = this.generation;
    request.pipe(takeUntil(this.cancel), takeUntilDestroyed(this.destroy)).subscribe({
      next: (value) => {
        if (generation === this.generation) next(value);
      },
      error: (failure) => {
        if (generation === this.generation) error(failure);
      },
    });
  }
  private exportFailure(error: { status?: number; error?: { code?: string } }) {
    this.exporting.set(false);
    const messages: Record<string, string> = {
      IDEMPOTENCY_CONFLICT:
        'The original key belongs to a different request. No replacement key was generated.',
      EVIDENCE_LIMIT: 'Evidence exceeds the export limit. Use independent section pagination.',
      ARCHIVE_LIMIT: 'The server archive exceeds its size limit. No local archive was created.',
      EXPORT_EXPIRED: 'The server export has expired and cannot be downloaded.',
      CONTEXT_INVALID: 'The purpose context is no longer authorized.',
    };
    this.exportMessage.set(
      messages[error.error?.code ?? ''] ??
        (error.status === 410
          ? 'The server export has expired and cannot be downloaded.'
          : [401, 403].includes(error.status ?? 0)
            ? 'The server did not authorize this export request.'
            : 'The request outcome is unknown. Recover the original request; do not create a replacement key.'),
    );
  }

  time(value: string | null) {
    return value ? `${value} UTC` : 'Not recorded';
  }

  label(value: string) {
    return value.replace(/([A-Z])/g, ' $1').replace(/^./, (first) => first.toUpperCase());
  }
}

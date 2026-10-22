import { Component, DestroyRef, inject, signal } from '@angular/core';
import { DatePipe, JsonPipe, KeyValuePipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ComplianceApi } from './compliance-api.service';
import { ComplianceContextComponent } from './compliance-context.component';
import { ComplianceResource } from './compliance-resource';
import {
  ComplianceEvidenceSection,
  ComplianceInvestigationDetail,
} from './compliance.models';
import { RequestStateComponent } from '../../shared/workspace/request-state.component';
import { createIdempotencyKey } from '../../core/idempotency-key';

@Component({
  selector: 'app-compliance-investigation-detail',
  imports: [
    DatePipe,
    JsonPipe,
    KeyValuePipe,
    RouterLink,
    RequestStateComponent,
    ComplianceContextComponent,
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
            <div><dt>Safe account reference</dt><dd>{{ detail.accountReference }}</dd></div>
            <div><dt>Authenticated actor</dt><dd>{{ detail.authenticatedActor }}</dd></div>
            <div><dt>Record kind</dt><dd>{{ detail.summary.recordKind }}</dd></div>
            <div><dt>Execution mode</dt><dd>{{ detail.summary.executionMode }}</dd></div>
            <div><dt>Submitted (UTC)</dt><dd>{{ time(detail.summary.submittedAt) }}</dd></div>
            <div><dt>Accepted (UTC)</dt><dd>{{ time(detail.summary.acceptedAt) }}</dd></div>
            <div><dt>Outcome (UTC)</dt><dd>{{ time(detail.summary.outcomeAt) }}</dd></div>
            <div><dt>Evidence completeness</dt><dd>{{ detail.summary.evidenceCompleteness }}</dd></div>
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
          <p>Current controls must not be read as the policy that governed the historical decision.</p>
          <details>
            <summary>Policy at acceptance</summary>
            @if (detail.acceptedPolicy) {
              <pre class="evidence-json">{{ detail.acceptedPolicy | json }}</pre>
            } @else {
              <p>NOT_EVALUATED: accepted policy was not retained.</p>
            }
          </details>
          <details>
            <summary>Current policy</summary>
            @if (detail.currentPolicy) {
              <pre class="evidence-json">{{ detail.currentPolicy | json }}</pre>
            } @else {
              <p>UNKNOWN: current policy was not supplied.</p>
            }
          </details>
        </section>
        @for (section of detail.sections; track section.name) {
          <section class="panel">
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
            <div class="table-wrapper" tabindex="0" [attr.aria-label]="section.name + ', scroll horizontally'">
              <table>
                <caption>{{ section.name }} retained records</caption>
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
                            <div><dt>{{ label(field.key) }}</dt><dd>{{ field.value ?? 'Not recorded' }}</dd></div>
                          }
                        </dl>
                      </td>
                    </tr>
                  } @empty {
                    <tr><td colspan="5">No retained records in this section.</td></tr>
                  }
                </tbody>
              </table>
            </div>
            <div class="pagination">
              <span>Page {{ section.page + 1 }} · {{ section.total }} scoped records</span>
              <button
                type="button"
                (click)="page(section, section.page - 1)"
                [disabled]="section.page === 0 || sectionLoading()"
              >
                Previous
              </button>
              <button
                type="button"
                (click)="page(section, section.page + 1)"
                [disabled]="!section.hasMore || sectionLoading()"
              >
                Next
              </button>
            </div>
            @if (sectionError()) {
              <p class="notice" role="alert">{{ sectionError() }}</p>
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
            <button type="button" (click)="requestExport(detail.summary.orderId)" [disabled]="exporting()">
              Request authorized export
            </button>
          } @else {
            <p>
              Export is disabled because an approved export entitlement, redaction policy and
              retention contract were not supplied by the server.
            </p>
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
  readonly sectionLoading = signal(false);
  readonly sectionError = signal('');
  readonly invalidId = signal(false);
  readonly exporting = signal(false);
  readonly exportMessage = signal('');
  private readonly route = inject(ActivatedRoute);
  private readonly destroy = inject(DestroyRef);
  private id = 0;
  private exportKey = '';
  backQuery: Record<string, string> = {};

  constructor() {
    const allowed = ['orderId', 'from', 'to', 'clientReference', 'instrument', 'status'];
    this.backQuery = Object.fromEntries(
      this.route.snapshot.queryParamMap.keys
        .filter((key) => allowed.includes(key))
        .map((key) => [key, this.route.snapshot.queryParamMap.get(key) ?? '']),
    );
    this.route.paramMap.pipe(takeUntilDestroyed()).subscribe((params) => {
      const raw = params.get('id') ?? '';
      this.id = /^[1-9]\d*$/.test(raw) ? Number(raw) : 0;
      this.invalidId.set(!this.id || !Number.isSafeInteger(this.id));
      this.exportKey = '';
      this.exportMessage.set('');
      this.load();
    });
    this.api.changed.pipe(takeUntilDestroyed()).subscribe(() => {
      if (this.api.context() && !this.invalidId()) this.load();
    });
  }

  load() {
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
    if (page < 0 || !section.hasMore && page > section.page) return;
    this.sectionLoading.set(true);
    this.sectionError.set('');
    const request = this.api.section(this.id, section.name, page);
    request.pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next: (updated) => {
        this.sectionLoading.set(false);
        const detail = this.resource.data();
        if (detail)
          this.resource.data.set({
            ...detail,
            sections: detail.sections.map((current) =>
              current.name === updated.name ? updated : current,
            ),
          });
      },
      error: () => {
        this.sectionLoading.set(false);
        this.sectionError.set('Unable to load this evidence section. Try again.');
      },
    });
  }

  requestExport(orderId: number) {
    if (this.exporting() || !this.exportKey) this.exportKey = createIdempotencyKey() ?? '';
    if (!this.exportKey) {
      this.exportMessage.set('This browser cannot create a safe export request key.');
      return;
    }
    this.exporting.set(true);
    this.exportMessage.set('');
    this.api
      .createExport(orderId, this.exportKey)
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe({
        next: (receipt) => {
          this.exporting.set(false);
          this.exportMessage.set(`Export ${receipt.requestReference} is ${receipt.state}.`);
        },
        error: (error) => {
          this.exporting.set(false);
          this.exportMessage.set(
            error.status === 409
              ? 'The export request outcome is uncertain. Recover the same request before retrying.'
              : 'The server did not authorize this export request.',
          );
        },
      });
  }

  time(value: string | null) {
    return value ? `${value} UTC` : 'Not recorded';
  }

  label(value: string) {
    return value.replace(/([A-Z])/g, ' $1').replace(/^./, (first) => first.toUpperCase());
  }

}

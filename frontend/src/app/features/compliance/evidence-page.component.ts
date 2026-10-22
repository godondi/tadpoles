import { Component, inject } from '@angular/core';
import { DatePipe, KeyValuePipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ComplianceApi } from './compliance-api.service';
import { ComplianceContextComponent } from './compliance-context.component';
import { ComplianceResource } from './compliance-resource';
import { ComplianceExportPage } from './compliance.models';
import { RequestStateComponent } from '../../shared/workspace/request-state.component';

@Component({
  selector: 'app-compliance-evidence-page',
  imports: [DatePipe, KeyValuePipe, RequestStateComponent, ComplianceContextComponent],
  template: `
    <header class="workspace-heading">
      <p class="eyebrow">RISK &amp; COMPLIANCE</p>
      <h1 tabindex="-1">Evidence &amp; exports</h1>
      <p>Review scoped export history and server-provided integrity metadata.</p>
    </header>
    <app-compliance-context />
    @if (api.context()) {
      <section class="panel">
        <app-request-state
          [loading]="resource.loading()"
          [error]="resource.error()"
          [status]="resource.status()"
          (retry)="load()"
        />
        @if (resource.data(); as page) {
          <p aria-live="polite">
            {{ page.total }} scoped export request{{ page.total === 1 ? '' : 's' }} · as of
            {{ page.asOf | date: 'medium' : 'UTC' }} UTC · {{ page.scope }}
          </p>
          <div class="table-wrapper" tabindex="0" aria-label="Evidence exports, scroll horizontally">
            <table>
              <caption>Scoped evidence export history</caption>
              <thead>
                <tr>
                  <th scope="col">Request reference</th>
                  <th scope="col">Purpose / scope</th>
                  <th scope="col">State</th>
                  <th scope="col">Created / expiry (UTC)</th>
                  <th scope="col">Manifest</th>
                </tr>
              </thead>
              <tbody>
                @for (item of page.items; track item.requestReference) {
                  <tr>
                    <td>{{ item.requestReference }}<small>{{ item.actor ?? 'Actor not disclosed' }}</small></td>
                    <td>{{ item.purposeReference }}<small>{{ item.scope }}</small></td>
                    <td>{{ item.state }}</td>
                    <td>
                      <small>Created: {{ item.createdAt | date: 'medium' : 'UTC' }} UTC</small>
                      <small>As of: {{ item.asOf | date: 'medium' : 'UTC' }} UTC</small>
                      <small>Expires: {{ item.expiresAt ? (item.expiresAt | date: 'medium' : 'UTC') + ' UTC' : 'Not supplied' }}</small>
                    </td>
                    <td>
                      @if (item.manifest; as manifest) {
                        <dl class="manifest">
                          <div><dt>Schema</dt><dd>{{ manifest.schemaVersion }}</dd></div>
                          <div><dt>Digest</dt><dd class="mono-value">{{ manifest.contentDigest }}</dd></div>
                          <div><dt>Verification</dt><dd>{{ manifest.verification }}</dd></div>
                          <div><dt>Signature status</dt><dd>{{ manifest.signatureStatus }}</dd></div>
                          <div><dt>Anchor status</dt><dd>{{ manifest.anchorStatus }}</dd></div>
                        </dl>
                        <details>
                          <summary>Section counts</summary>
                          <dl class="manifest">
                            @for (count of manifest.sectionCounts | keyvalue; track count.key) {
                              <div><dt>{{ count.key }}</dt><dd>{{ count.value }}</dd></div>
                            }
                          </dl>
                        </details>
                      } @else {
                        Not supplied
                      }
                    </td>
                  </tr>
                } @empty {
                  <tr><td colspan="5">No scoped export requests are available.</td></tr>
                }
              </tbody>
            </table>
          </div>
          <nav aria-label="Compliance export pages" class="pagination">
            <button type="button" (click)="load(page.page - 1)" [disabled]="page.page === 0 || resource.loading()">Previous</button>
            <span>Page {{ page.page + 1 }}</span>
            <button type="button" (click)="load(page.page + 1)" [disabled]="(page.page + 1) * page.pageSize >= page.total || resource.loading()">Next</button>
          </nav>
        }
      </section>
      <aside class="notice">
        <h2>Export actions are policy-gated</h2>
        <p>
          The current server has no approved Compliance export contract. This screen never creates
          a local file, changes retention, edits audit history or labels a digest as tamper-proof.
          A future server receipt must authorize any download separately.
        </p>
      </aside>
    }
  `,
  styles: [
    `
      small {
        display: block;
        color: var(--text-secondary);
        margin-top: 0.25rem;
      }
      .manifest {
        display: grid;
        gap: 0.25rem;
      }
      .manifest div {
        display: grid;
        grid-template-columns: 8rem minmax(0, 1fr);
        gap: 0.5rem;
      }
      .manifest dd {
        margin: 0;
        overflow-wrap: anywhere;
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
export class ComplianceEvidencePageComponent {
  readonly api = inject(ComplianceApi);
  readonly resource = new ComplianceResource<ComplianceExportPage>();

  constructor() {
    this.api.changed.pipe(takeUntilDestroyed()).subscribe(() => {
      if (this.api.context()) this.load(0);
    });
    if (this.api.context()) this.load(0);
  }

  load(page = 0) {
    if (!this.api.context()) {
      this.resource.clear();
      return;
    }
    this.resource.load(this.api.evidence(page, 25));
  }
}

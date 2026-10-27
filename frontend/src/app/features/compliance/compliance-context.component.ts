import { Component, DestroyRef, Input, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ComplianceApi } from './compliance-api.service';
import { DisplayCodePipe } from '../../shared/display-code.pipe';

@Component({
  selector: 'app-compliance-context',
  imports: [DatePipe, FormsModule, DisplayCodePipe],
  template: `
    <section class="panel" aria-label="Compliance access purpose">
      <h2>Access purpose</h2>
      @if (api.context(); as context) {
        <p>
          <strong>{{ context.purposeCode | displayCode }}</strong> · case {{ context.caseReference }} ·
          {{ context.scopeLabel }} · expires {{ context.expiresAt | date: 'medium' : 'UTC' }} UTC
        </p>
        <p>Approved permissions: {{ context.permissions.join(', ') || 'Read-only evidence' }}</p>
        <button type="button" (click)="api.clear()">End context and clear evidence</button>
      } @else {
        <form class="filters" (ngSubmit)="start()">
          <div class="form-field">
            <label for="compliance-purpose">Approved purpose</label>
            <select
              id="compliance-purpose"
              name="purpose"
              [(ngModel)]="purpose"
              [disabled]="!policy()?.configured"
              required
            >
              <option value="">Select purpose</option>
              @for (code of policy()?.purposes ?? []; track code) {
                <option [value]="code">{{ code | displayCode }}</option>
              }
            </select>
          </div>
          <div class="form-field">
            <label for="compliance-reference-type">Reference type</label>
            <select id="compliance-reference-type" name="referenceType" [(ngModel)]="referenceType">
              <option value="EXTERNAL">External operational reference</option>
              <option value="MANAGED">Managed Compliance review case</option>
            </select>
          </div>
          <div class="form-field">
            <label for="compliance-case"
              >Case reference <small class="min-length-hint">(minimum 8 characters)</small></label
            >
            <input
              id="compliance-case"
              name="caseReference"
              [(ngModel)]="caseReference"
              minlength="8"
              maxlength="200"
              autocomplete="off"
              required
            />
          </div>
          <button
            type="submit"
            [disabled]="
              loading() || !policy()?.configured || !purpose || caseReference.trim().length < 8
            "
          >
            Start authorized context
          </button>
        </form>
        @if (!policy()?.configured) {
          <p class="notice" role="status">
            Purpose policy is unavailable. Sensitive searches, detail reads and exports remain
            disabled; entering a purpose here does not grant access.
          </p>
        }
      }
      @if (policyError()) {
        <p class="notice" role="alert">{{ policyError() }}</p>
      }
      @if (api.contextMessage()) {
        <p class="notice" role="alert">{{ api.contextMessage() }}</p>
      }
      @if (message()) {
        <p class="notice" role="alert">{{ message() }}</p>
      }
    </section>
  `,
})
export class ComplianceContextComponent {
  readonly api = inject(ComplianceApi);
  readonly policy = signal<{
    configured: boolean;
    purposes: string[];
    scopeLabel: string;
    policyVersion: string;
  } | null>(null);
  readonly policyError = signal('');
  readonly message = signal('');
  readonly loading = signal(false);
  private readonly destroy = inject(DestroyRef);
  purpose = '';
  caseReference = '';
  referenceType: 'EXTERNAL' | 'MANAGED' = 'EXTERNAL';
  @Input() set managedReference(value: string) {
    if (value) {
      this.caseReference = value;
      this.referenceType = 'MANAGED';
    }
  }

  constructor() {
    this.api
      .policy()
      .pipe(takeUntilDestroyed())
      .subscribe({
        next: (policy) => {
          this.policy.set(policy);
          this.policyError.set('');
        },
        error: (error) => {
          this.policy.set(null);
          this.policyError.set(
            error.status === 503
              ? 'Compliance purpose policy is not configured on the server.'
              : 'Unable to load the approved purpose policy.',
          );
        },
      });
  }

  start() {
    const purpose = this.purpose.trim();
    const caseReference = this.caseReference.trim();
    if (!this.policy()?.configured || !this.policy()?.purposes.includes(purpose)) {
      this.message.set('Choose a purpose from the approved policy.');
      return;
    }
    if (caseReference.length < 8 || caseReference.length > 200) {
      this.message.set('Enter a case reference between 8 and 200 characters.');
      return;
    }
    this.loading.set(true);
    this.message.set('');
    this.api
      .createContext({ purposeCode: purpose, caseReference, referenceType: this.referenceType })
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe({
        next: () => {
          this.loading.set(false);
          this.caseReference = '';
        },
        error: (error) => {
          this.loading.set(false);
          this.message.set(
            error.status === 503
              ? 'The server cannot authorize this context until Compliance policy is configured.'
              : (error.error?.message ?? 'Unable to start the authorized context.'),
          );
        },
      });
  }
}

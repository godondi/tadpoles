import { Component, DestroyRef, Input, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { OperationsApi, AccessPolicy } from '../../core/api/operations-api.service';
import { DisplayCodePipe } from '../../shared/display-code.pipe';

@Component({
  selector: 'app-operations-context',
  imports: [DatePipe, FormsModule, DisplayCodePipe],
  template: `
    <section class="panel" aria-label="Investigation access purpose">
      <h2>Access purpose</h2>
      @if (api.context(); as context) {
        <p>
          {{ context.purposeCode | displayCode }} · {{ context.referenceType | displayCode }} · {{ context.caseReference }} ·
          expires {{ context.expiresAt | date: 'medium' : 'UTC' }} UTC
        </p>
        <button type="button" (click)="api.clear()">End context and clear evidence</button>
      } @else {
        @if (policy()?.configured) {
          <form class="filters" (ngSubmit)="start()">
            <div class="form-field">
              <label for="ops-purpose">Purpose</label>
              <select id="ops-purpose" name="purpose" [(ngModel)]="purpose" required>
                <option value="">Select purpose</option>
                @for (code of policy()?.purposes; track code) {
                  <option [value]="code">{{ code | displayCode }}</option>
                }
              </select>
            </div>
            <div class="form-field">
              <label for="ops-reference-kind">Reference type</label
              ><select id="ops-reference-kind" name="referenceType" [(ngModel)]="referenceType">
                <option value="EXTERNAL">External / operational reference</option>
                <option value="MANAGED">Managed support case</option>
              </select>
            </div>
            <div class="form-field">
              <label for="ops-case">Case reference (8–200 characters)</label>
              <input
                id="ops-case"
                name="case"
                [(ngModel)]="caseReference"
                required
                minlength="8"
                maxlength="200"
                autocomplete="off"
              />
            </div>
            <button [disabled]="loading() || !purpose || caseReference.trim().length < 8">
              Start context
            </button>
          </form>
        } @else {
          <p>Investigation access is unavailable until an access policy has been configured.</p>
        }
      }
      @if (api.contextMessage()) {
        <p role="alert">{{ api.contextMessage() }}</p>
      }
      @if (message()) {
        <p role="alert">{{ message() }}</p>
      }
    </section>
  `,
})
export class OperationsContextComponent {
  readonly api = inject(OperationsApi);
  readonly policy = signal<AccessPolicy | null>(null);
  readonly message = signal('');
  readonly loading = signal(false);
  purpose = '';
  caseReference = '';
  referenceType: 'EXTERNAL' | 'MANAGED' = 'EXTERNAL';
  @Input() set managedReference(value: string) {
    if (value) {
      this.caseReference = value;
      this.referenceType = 'MANAGED';
    }
  }
  private readonly destroy = inject(DestroyRef);
  constructor() {
    this.api
      .policy()
      .pipe(takeUntilDestroyed())
      .subscribe({
        next: (policy) => this.policy.set(policy),
        error: () => this.message.set('Unable to load access policy.'),
      });
  }
  start() {
    this.loading.set(true);
    this.message.set('');
    this.api
      .createContext(this.purpose, this.caseReference, this.referenceType)
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe({
        next: () => {
          if (this.referenceType === 'EXTERNAL') this.caseReference = '';
          this.loading.set(false);
        },
        error: (error) => {
          this.loading.set(false);
          this.message.set(error.error?.message ?? 'Unable to start access context.');
        },
      });
  }
}

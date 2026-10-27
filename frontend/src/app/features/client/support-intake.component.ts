import {
  Component,
  DestroyRef,
  EventEmitter,
  Input,
  OnChanges,
  Output,
  SimpleChanges,
  effect,
  inject,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { SupportApiService, IntakePolicy } from '../../core/api/support-api.service';
import { AuthService } from '../../core/auth/auth.service';
import { utcFilter } from '../../shared/workspace/utc-filter';

const fields: Record<
  string,
  { code: string; label: string; type: 'text' | 'number' | 'datetime-local' | 'textarea' }[]
> = {
  ORDER_QUESTION: [{ code: 'relatedOrderId', label: 'Optional owned order ID', type: 'number' }],
  ACCOUNT_REVIEW: [
    { code: 'movementId', label: 'Optional owned movement reference (UUID)', type: 'text' },
  ],
  HISTORICAL_RECORD: [
    { code: 'exceptionId', label: 'Owned historical exception ID', type: 'number' },
    { code: 'recordKind', label: 'Type of record, if known', type: 'text' },
  ],
  TRADING_AVAILABILITY: [
    { code: 'instrumentId', label: 'Instrument ID', type: 'number' },
    { code: 'side', label: 'Attempted side (BUY or SELL)', type: 'text' },
    { code: 'observedAt', label: 'Reported observation time (UTC)', type: 'datetime-local' },
    { code: 'reportedReason', label: 'Displayed unavailability reason', type: 'text' },
  ],
  OTHER: [
    { code: 'affectedArea', label: 'Affected area', type: 'text' },
    { code: 'expectedBehavior', label: 'Expected behavior', type: 'textarea' },
    { code: 'actualBehavior', label: 'Actual behavior', type: 'textarea' },
    { code: 'occurredAt', label: 'Approximate occurrence time (UTC)', type: 'datetime-local' },
    { code: 'steps', label: 'Safe reproduction steps; no secrets', type: 'textarea' },
    { code: 'duplicateReference', label: 'Optional owned duplicate case reference', type: 'text' },
  ],
};
@Component({
  selector: 'app-support-intake',
  imports: [FormsModule],
  template: `
    @if (message()) {
      <p role="alert">{{ message() }}</p>
    }
    @if (policy()?.intakeEnabled) {
      <fieldset [disabled]="disabled">
        <legend class="sr-only">Category details - {{ category }}</legend>
        <h3 class="intake-heading" aria-hidden="true">Category details - {{ category }}</h3>
        <label
          >Issue reason<select
            name="intakeReason"
            [(ngModel)]="reason"
            (ngModelChange)="publish()"
            required
          >
            <option value="">Choose reason</option>
            @for (code of reasons(); track code) {
              <option>{{ code }}</option>
            }
          </select></label
        >
        @for (field of categoryFields(); track field.code) {
          <label [class.intake-wide]="field.type === 'textarea' || field.code === 'reportedReason'"
            >{{ field.label }}
            @if (field.type === 'textarea') {
              <textarea
                [name]="'intake' + field.code"
                [(ngModel)]="values[field.code]"
                (ngModelChange)="publish()"
                maxlength="2000"
              ></textarea>
            } @else {
              <input
                [name]="'intake' + field.code"
                [type]="field.type"
                [(ngModel)]="values[field.code]"
                (ngModelChange)="publish()"
                [maxLength]="500"
                min="1"
                step="1"
              />
            }
          </label>
        }
        <p>
          References are checked against your account. Unknown facts may be omitted; do not guess.
        </p>
      </fieldset>
    }
  `,
})
export class SupportIntakeComponent implements OnChanges {
  @Input({ required: true }) category = '';
  @Input() disabled = false;
  @Output() changed = new EventEmitter<{
    enabled: boolean;
    intake: Record<string, string | number>;
    valid: boolean;
  }>();
  private readonly api = inject(SupportApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly auth = inject(AuthService);
  private readonly destroy = inject(DestroyRef);
  readonly policy = signal<IntakePolicy | null>(null);
  readonly message = signal('');
  values: Record<string, string | number> = {};
  reason = '';
  constructor() {
    this.loadPolicy();
    let activeUserId = this.auth.identity()?.userId ?? null;
    effect(() => {
      const userId = this.auth.identity()?.userId ?? null;
      if (userId === activeUserId) return;
      activeUserId = userId;
      this.values = {};
      this.reason = '';
      this.policy.set(null);
      this.message.set('');
      this.changed.emit({ enabled: false, intake: {}, valid: false });
      if (userId !== null) this.loadPolicy(false);
    });
  }
  private loadPolicy(restoreHints = true) {
    this.api
      .intakePolicy()
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe({
        next: (policy) => {
          this.policy.set(policy);
          if (restoreHints) this.hints();
          this.publish();
        },
        error: () => {
          this.message.set(
            'Category intake policy unavailable. Refresh before reporting a new issue.',
          );
          this.changed.emit({ enabled: false, intake: {}, valid: false });
        },
      });
  }
  ngOnChanges(changes: SimpleChanges) {
    if (changes['category']) {
      this.reason = '';
      this.values = {};
      this.hints();
      this.publish();
    }
  }
  reasons() {
    return this.policy()?.categories.find((c) => c.code === this.category)?.reasons ?? [];
  }
  categoryFields() {
    return fields[this.category] ?? [];
  }
  private hints() {
    for (const field of this.categoryFields()) {
      const value = this.route.snapshot.queryParamMap.get(field.code);
      if (value !== null)
        this.values[field.code] =
          field.type === 'number'
            ? Number(value)
            : field.type === 'datetime-local'
              ? value.replace(/Z$/, '')
              : value;
    }
    const reason = this.route.snapshot.queryParamMap.get('reason');
    if (reason && this.reasons().includes(reason)) this.reason = reason;
  }
  publish() {
    const enabled = this.policy()?.intakeEnabled ?? false;
    const intake: Record<string, string | number> = { reason: this.reason };
    let valid = !!this.policy() && (!enabled || this.reasons().includes(this.reason));
    for (const field of this.categoryFields()) {
      const value = this.values[field.code];
      if (value === undefined || value === '') continue;
      if (field.type === 'number') {
        const number = Number(value);
        valid = valid && Number.isSafeInteger(number) && number > 0;
        intake[field.code] = number;
      } else if (field.type === 'datetime-local') {
        try {
          const instant = utcFilter(String(value));
          if (instant) intake[field.code] = instant;
          else valid = false;
        } catch {
          valid = false;
        }
      } else intake[field.code] = String(value).trim();
    }
    this.changed.emit({ enabled, intake, valid });
  }
}

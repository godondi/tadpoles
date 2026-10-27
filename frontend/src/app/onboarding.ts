import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from './core/auth/auth.service';
import { CompleteClientOnboardingRequest, UserService } from './user.service';

@Component({
  selector: 'app-onboarding',
  imports: [FormsModule],
  template: `
    <div class="page-shell">
      <section class="panel">
        <header class="panel-header">
          <div>
            <h1>Complete your onboarding</h1>
            <p>Tell us a bit more so we can activate your client profile.</p>
          </div>
          <button type="button" class="link-button" (click)="logout()">Log out</button>
        </header>

        @if (isLoading()) {
          <p class="status">Loading your profile…</p>
        }

        <form class="form-grid" novalidate (ngSubmit)="submit()">
          <label>
            <span>Client name</span>
            <input
              type="text"
              [value]="form().clientName"
              (input)="updateField('clientName', $event)"
              required
            />
          </label>
          <label>
            <span>Phone</span>
            <input
              type="tel"
              name="phone"
              inputmode="numeric"
              maxlength="14"
              [value]="form().phone"
              (input)="updateField('phone', $event)"
              required
            />
          </label>
          <label>
            <span>Date of birth</span>
            <input
              type="date"
              [value]="form().dateOfBirth"
              (input)="updateField('dateOfBirth', $event)"
              required
            />
          </label>
          <label>
            <span>Employment status</span>
            <select
              name="employmentStatus"
              [value]="form().employmentStatus"
              (change)="updateField('employmentStatus', $event)"
              required
            >
              <option value="" disabled>Select employment status</option>
              @for (option of employmentStatusOptions; track option) {
                <option [value]="option">{{ option }}</option>
              }
            </select>
          </label>
          <label class="span-2">
            <span>Address line 1</span>
            <input
              type="text"
              [value]="form().addressLine1"
              (input)="updateField('addressLine1', $event)"
              required
            />
          </label>
          <label class="span-2">
            <span>Address line 2</span>
            <input
              type="text"
              [value]="form().addressLine2"
              (input)="updateField('addressLine2', $event)"
            />
          </label>
          <label>
            <span>City</span>
            <input
              type="text"
              [value]="form().city"
              (input)="updateField('city', $event)"
              required
            />
          </label>
          <label>
            <span>State</span>
            <input
              type="text"
              [value]="form().state"
              (input)="updateField('state', $event)"
              required
            />
          </label>
          <label>
            <span>Postal code</span>
            <input
              type="text"
              name="postalCode"
              inputmode="numeric"
              pattern="[0-9]*"
              [value]="form().postalCode"
              (input)="updateField('postalCode', $event)"
              required
            />
          </label>
          <label>
            <span>Country</span>
            <input
              type="text"
              [value]="form().country"
              (input)="updateField('country', $event)"
              required
            />
          </label>
          <label>
            <span>Net worth</span>
            <select
              name="netWorth"
              [value]="selectedNetWorthRange()"
              (change)="updateNetWorth($event)"
              required
            >
              <option value="" disabled>Select net worth</option>
              @for (range of netWorthRanges; track range.minimum) {
                <option [value]="range.minimum">{{ range.label }}</option>
              }
            </select>
          </label>
          <label>
            <span>Risk tolerance</span>
            <select
              name="riskTolerance"
              [value]="form().riskTolerance"
              (change)="updateField('riskTolerance', $event)"
              required
            >
              <option value="" disabled>Select risk tolerance</option>
              @for (option of riskToleranceOptions; track option) {
                <option [value]="option">{{ option }}</option>
              }
            </select>
          </label>
          <label class="span-2">
            <span>Investment objective</span>
            <select
              name="investmentObjective"
              [value]="form().investmentObjective"
              (change)="updateField('investmentObjective', $event)"
              required
            >
              <option value="" disabled>Select investment objective</option>
              @for (option of investmentObjectiveOptions; track option) {
                <option [value]="option">{{ option }}</option>
              }
            </select>
          </label>
          <label>
            <span>Preferred contact method</span>
            <select
              name="preferredContactMethod"
              [value]="form().preferredContactMethod"
              (change)="updateField('preferredContactMethod', $event)"
              required
            >
              <option value="" disabled>Select contact method</option>
              @for (option of preferredContactMethodOptions; track option) {
                <option [value]="option">{{ option }}</option>
              }
            </select>
          </label>
          @if (errorMessage()) {
            <div class="message error span-2" role="alert" tabindex="-1">{{ errorMessage() }}</div>
          }

          <div class="actions span-2">
            <button type="submit" [disabled]="isSubmitting() || isLoading()">
              {{ isSubmitting() ? 'Saving…' : 'Complete onboarding' }}
            </button>
          </div>
        </form>
      </section>
    </div>
  `,
  styles: [
    `
      :host {
        display: block;
        min-height: 100vh;
        background: var(--page-bg);
        padding: 2rem 1rem;
      }

      .page-shell {
        max-width: 72rem;
        margin: 0 auto;
      }

      .panel {
        background: var(--surface);
        border-radius: 0;
        padding: clamp(1.25rem, 3vw, 2rem);
        border: 1px solid var(--border-gray);
        box-shadow: 0 20px 40px rgba(15, 23, 42, 0.08);
      }

      .panel-header {
        display: flex;
        justify-content: space-between;
        gap: 1rem;
        align-items: flex-start;
        margin-bottom: 1.5rem;
      }

      h1 {
        margin: 0 0 0.5rem;
        color: var(--dark-green);
      }

      p {
        margin: 0;
        color: var(--text-secondary);
      }

      .form-grid {
        display: grid;
        grid-template-columns: repeat(2, minmax(0, 1fr));
        gap: 1rem;
      }

      label {
        display: grid;
        gap: 0.45rem;
        font-weight: 600;
        color: var(--text-primary);
      }

      input,
      select {
        border: 1px solid var(--border-gray);
        border-radius: 0;
        padding: 0.75rem 0.875rem;
        min-height: 46px;
        font: inherit;
        background: var(--surface);
        color: var(--text-primary);
        min-width: 0;
        width: 100%;
      }

      small {
        color: var(--text-secondary);
        font-weight: 400;
        line-height: 1.4;
      }

      .span-2 {
        grid-column: span 2;
      }

      .actions {
        display: flex;
        justify-content: flex-end;
        margin-top: 0.5rem;
      }

      button {
        border: 1px solid var(--dark-green);
        border-radius: 0;
        padding: 0.75rem 1.125rem;
        min-height: 46px;
        background: var(--action-bg);
        color: white;
        font: inherit;
        font-weight: 700;
        cursor: pointer;
      }

      button:disabled {
        background: var(--surface-muted);
        color: var(--text-secondary);
        border-color: var(--border-gray);
        cursor: not-allowed;
      }

      button:not(:disabled):hover {
        background: var(--action-hover);
      }

      .link-button {
        background: transparent;
        color: var(--dark-green);
        padding: 0;
      }

      .status,
      .message {
        margin-bottom: 1rem;
        padding: 0.85rem 1rem;
        border-radius: 0;
      }

      .status {
        background: var(--success-bg);
        color: var(--dark-green);
      }

      .message.error {
        background: var(--error-bg);
        color: var(--error-text);
      }

      @media (max-width: 768px) {
        .panel {
          padding: 1.5rem;
        }

        .panel-header {
          flex-direction: column;
        }

        .form-grid {
          grid-template-columns: 1fr;
        }

        .span-2 {
          grid-column: auto;
        }

        .actions {
          justify-content: stretch;
        }

        .actions button {
          width: 100%;
        }
      }
    `,
  ],
})
export class OnboardingComponent implements OnInit {
  private readonly userService = inject(UserService);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  readonly employmentStatusOptions = [
    'Unemployed',
    'Intern',
    'Student',
    'Employed (Part-Time)',
    'Employed (Full-Time)',
    'Retired',
  ];
  readonly investmentObjectiveOptions = ['Preservation', 'Income', 'Growth', 'Speculation'];
  readonly preferredContactMethodOptions = ['Email', 'Phone'];
  readonly riskToleranceOptions = ['Conservative', 'Moderate', 'Aggressive'];
  // Keep the numeric API contract: newly selected ranges submit their lower bound.
  readonly netWorthRanges = [
    { minimum: 0, label: '$0.00 - $24,999.00' },
    { minimum: 25000, label: '$25,000.00 - $49,999.00' },
    { minimum: 50000, label: '$50,000.00 - $99,999.00' },
    { minimum: 100000, label: '$100,000.00 - $249,999.00' },
    { minimum: 250000, label: '$250,000.00 - $499,999.00' },
    { minimum: 500000, label: '$500,000.00 - $999,999.00' },
    { minimum: 1000000, label: '$1,000,000.00+' },
  ];
  readonly selectedNetWorthRange = computed(() => {
    const amount = this.form().netWorth;
    if (amount === null || !Number.isFinite(amount) || amount < 0) return '';
    const range = this.netWorthRanges.reduce((selected, next) =>
      amount >= next.minimum ? next : selected,
    );
    return String(range.minimum);
  });

  readonly form = signal<CompleteClientOnboardingRequest>({
    clientName: '',
    phone: '',
    dateOfBirth: '',
    addressLine1: '',
    addressLine2: '',
    city: '',
    state: '',
    postalCode: '',
    country: '',
    employmentStatus: '',
    netWorth: null,
    riskTolerance: '',
    investmentObjective: '',
    preferredContactMethod: '',
    paperlessStatements: true,
    marketingOptIn: false,
  });
  readonly isLoading = signal(false);
  readonly isSubmitting = signal(false);
  readonly errorMessage = signal('');

  async ngOnInit(): Promise<void> {
    this.isLoading.set(true);
    try {
      const profile = await this.userService.loadCurrentProfile();
      if (profile.onboardingComplete) {
        this.authService.updateOnboardingStatus(true, profile.clientId);
        await this.router.navigateByUrl('/');
        return;
      }

      this.form.set({
        clientName: profile.clientName,
        phone: this.formatPhoneNumber(profile.phone),
        dateOfBirth: profile.dateOfBirth,
        addressLine1: profile.addressLine1,
        addressLine2: profile.addressLine2,
        city: profile.city,
        state: profile.state,
        postalCode: profile.postalCode.replace(/\D/g, ''),
        country: profile.country,
        employmentStatus: profile.employmentStatus,
        netWorth: profile.netWorth,
        riskTolerance: profile.riskTolerance,
        investmentObjective: profile.investmentObjective,
        preferredContactMethod: profile.preferredContactMethod,
        paperlessStatements: profile.paperlessStatements,
        marketingOptIn: profile.marketingOptIn,
      });
    } catch (error) {
      this.errorMessage.set(this.toErrorMessage(error));
    } finally {
      this.isLoading.set(false);
    }
  }

  updateField(field: keyof CompleteClientOnboardingRequest, event: Event): void {
    const input = event.target as HTMLInputElement | HTMLSelectElement;
    let value = input.value;
    if (field === 'phone') value = this.formatPhoneNumber(value);
    if (field === 'postalCode') value = value.replace(/\D/g, '');
    input.value = value;
    this.form.update((current) => ({ ...current, [field]: value }));
  }

  updateNetWorth(event: Event): void {
    const value = (event.target as HTMLInputElement | HTMLSelectElement).value;
    this.form.update((current) => ({
      ...current,
      netWorth: value === '' ? null : Number(value),
    }));
  }

  async submit(): Promise<void> {
    const payload = this.form();
    if (
      !payload.clientName.trim() ||
      !payload.phone.trim() ||
      !payload.addressLine1.trim() ||
      !payload.city.trim() ||
      !payload.state.trim() ||
      !payload.postalCode.trim() ||
      !payload.country.trim()
    ) {
      this.errorMessage.set('Please complete all required onboarding fields.');
      return;
    }

    if (!/^\d{10}$/.test(payload.phone.replace(/\D/g, ''))) {
      this.errorMessage.set('Phone number must contain 10 digits.');
      return;
    }

    if (!/^\d+$/.test(payload.postalCode)) {
      this.errorMessage.set('Postal code must contain numbers only.');
      return;
    }

    if (!payload.dateOfBirth) {
      this.errorMessage.set('Date of birth is required.');
      return;
    }

    if (payload.netWorth === null || !Number.isFinite(payload.netWorth) || payload.netWorth < 0) {
      this.errorMessage.set('Net worth must be zero or greater.');
      return;
    }

    if (
      !this.employmentStatusOptions.includes(payload.employmentStatus) ||
      !this.investmentObjectiveOptions.includes(payload.investmentObjective) ||
      !this.preferredContactMethodOptions.includes(payload.preferredContactMethod)
    ) {
      this.errorMessage.set(
        'Please select an employment status, investment objective, and preferred contact method.',
      );
      return;
    }

    if (!this.riskToleranceOptions.includes(payload.riskTolerance)) {
      this.errorMessage.set('Please select a risk tolerance.');
      return;
    }

    this.isSubmitting.set(true);
    this.errorMessage.set('');
    try {
      const profile = await this.userService.completeOnboarding({
        ...payload,
        clientName: payload.clientName.trim(),
        phone: this.formatPhoneNumber(payload.phone),
        addressLine1: payload.addressLine1.trim(),
        addressLine2: payload.addressLine2.trim(),
        city: payload.city.trim(),
        state: payload.state.trim(),
        postalCode: payload.postalCode.trim(),
        country: payload.country.trim(),
        employmentStatus: payload.employmentStatus.trim(),
        riskTolerance: payload.riskTolerance.trim(),
        investmentObjective: payload.investmentObjective.trim(),
        preferredContactMethod: payload.preferredContactMethod.trim(),
      });
      this.authService.updateOnboardingStatus(true, profile.clientId);
      await this.router.navigateByUrl('/');
    } catch (error) {
      this.errorMessage.set(this.toErrorMessage(error));
    } finally {
      this.isSubmitting.set(false);
    }
  }

  logout(): void {
    this.authService.logout();
    void this.router.navigateByUrl('/login');
  }

  private toErrorMessage(error: unknown): string {
    if (typeof error === 'object' && error !== null && 'error' in error) {
      const apiError = (error as { error?: { message?: string } }).error;
      if (apiError?.message) {
        return apiError.message;
      }
    }

    return 'Unable to load or save onboarding details.';
  }

  private formatPhoneNumber(value: string): string {
    let digits = value.replace(/\D/g, '');
    if (digits.length === 11 && digits.startsWith('1')) digits = digits.slice(1);
    digits = digits.slice(0, 10);
    if (!digits) return '';
    if (digits.length <= 3) return `(${digits}`;
    if (digits.length <= 6) return `(${digits.slice(0, 3)}) ${digits.slice(3)}`;
    return `(${digits.slice(0, 3)}) ${digits.slice(3, 6)}-${digits.slice(6)}`;
  }
}

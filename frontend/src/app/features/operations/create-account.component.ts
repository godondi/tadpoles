import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { OperationsUsersApi } from '../../core/api/operations-users-api.service';

@Component({
  selector: 'app-operations-create-account',
  imports: [FormsModule],
  template: `
    <header class="workspace-heading">
      <p class="eyebrow">TRADING OPERATIONS</p>
      <h1 tabindex="-1">Create account</h1>
      <p>Create a staff account for an approved workspace role.</p>
    </header>
    @if (message()) {
      <p class="notice" role="status">{{ message() }}</p>
    }
    @if (error()) {
      <p class="notice error" role="alert">{{ error() }}</p>
    }
    <section class="panel create-panel">
      <h2>Staff account details</h2>
      <p class="form-intro">
        Choose a role and enter the account details. Passwords must have at least 8 characters,
        upper and lower case letters, a number, and a symbol.
      </p>
      <form class="create-form" novalidate (ngSubmit)="createAccount()">
        <label
          >Role<select name="role" [(ngModel)]="staff.roleType">
            <option>ANALYST</option>
            <option>ADVISOR</option>
            <option>COMPLIANCE</option>
            <option>FINANCE</option>
          </select></label
        >
        <label
          ><span>Username <small class="min-length-hint">(minimum 3 characters)</small></span
          ><input
            name="username"
            required
            minlength="3"
            maxlength="60"
            [(ngModel)]="staff.username"
            autocomplete="username"
        /></label>
        <label
          >Name<input
            name="displayName"
            required
            maxlength="120"
            [(ngModel)]="staff.displayName"
            autocomplete="name"
        /></label>
        <label
          >Email<input
            name="email"
            type="email"
            required
            [(ngModel)]="staff.email"
            autocomplete="email"
        /></label>
        <label
          ><span>Password <small class="min-length-hint">(minimum 8 characters)</small></span
          ><input
            name="password"
            type="password"
            required
            minlength="8"
            pattern="(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9])(?=.*[^A-Za-z0-9]).{8,}"
            [(ngModel)]="staff.password"
            autocomplete="new-password"
        /></label>
        @if (staff.roleType === 'ADVISOR') {
          <label
            >Advisor name<input name="advisorName" required [(ngModel)]="staff.advisorName"
          /></label>
        }
        <button type="submit" [disabled]="saving()">
          {{ saving() ? 'Creating account…' : 'Create account' }}
        </button>
      </form>
    </section>
  `,
  styles: [
    `
      .create-panel {
        max-width: 820px;
        padding: 24px;
      }
      .create-panel h2 {
        margin: 0;
      }
      .form-intro {
        max-width: 64ch;
        color: var(--text-secondary);
        line-height: 1.5;
        margin: 8px 0 22px;
      }
      .create-form {
        display: grid;
        grid-template-columns: repeat(2, minmax(0, 1fr));
        gap: 18px 16px;
      }
      .create-form label {
        display: grid;
        gap: 7px;
        color: var(--text-secondary);
        font-size: 0.88rem;
        font-weight: 700;
      }
      .create-form input,
      .create-form select {
        width: 100%;
        min-width: 0;
        min-height: 46px;
        padding: 11px 13px;
        border: 1px solid var(--border-gray);
        background: var(--surface);
        color: var(--text-primary);
        font: inherit;
      }
      .create-form input:focus,
      .create-form select:focus {
        outline: 2px solid var(--dark-green);
        outline-offset: 1px;
      }
      .create-form button {
        grid-column: 2;
        justify-self: start;
        align-self: end;
        min-height: 46px;
        padding: 11px 18px;
        border: 1px solid var(--dark-green);
        background: var(--action-bg);
        color: var(--action-text);
        font: inherit;
        font-weight: 700;
        cursor: pointer;
      }
      .create-form button:disabled {
        opacity: 0.7;
        cursor: progress;
      }
      .notice {
        padding: 12px 16px;
        background: var(--surface-muted);
        color: var(--text-primary);
      }
      .notice.error {
        background: var(--error-bg);
        color: var(--error-text);
      }
      @media (max-width: 640px) {
        .create-panel {
          padding: 18px;
        }
        .create-form {
          grid-template-columns: 1fr;
        }
        .create-form button {
          grid-column: auto;
          width: 100%;
        }
      }
    `,
  ],
})
export class OperationsCreateAccountComponent {
  private readonly api = inject(OperationsUsersApi);
  readonly saving = signal(false);
  readonly error = signal('');
  readonly message = signal('');
  staff = {
    username: '',
    displayName: '',
    email: '',
    password: '',
    roleType: 'ANALYST',
    advisorName: '',
  };
  createAccount() {
    const value = this.staff;
    if (
      !value.username.trim() ||
      !value.displayName.trim() ||
      !value.email.trim() ||
      !value.password
    ) {
      this.error.set('Complete all required fields.');
      return;
    }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value.email)) {
      this.error.set('Enter a valid email address.');
      return;
    }
    if (!/^(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9])(?=.*[^A-Za-z0-9]).{8,}$/.test(value.password)) {
      this.error.set(
        'Use at least 8 characters with upper and lower case letters, a number, and a symbol.',
      );
      return;
    }
    if (value.roleType === 'ADVISOR' && !value.advisorName.trim()) {
      this.error.set('Advisor name is required for Advisor accounts.');
      return;
    }
    this.saving.set(true);
    this.error.set('');
    this.api
      .createStaff({
        ...value,
        username: value.username.trim(),
        displayName: value.displayName.trim(),
        email: value.email.trim().toLowerCase(),
        advisorName: value.advisorName.trim(),
        enabled: true,
      })
      .subscribe({
        next: () => {
          this.message.set(`${value.roleType} account created.`);
          this.staff = {
            username: '',
            displayName: '',
            email: '',
            password: '',
            roleType: 'ANALYST',
            advisorName: '',
          };
          this.saving.set(false);
        },
        error: (err) => {
          this.error.set(
            err?.error?.message ??
              'Unable to create account. Check the required fields and ensure the username/email are unused.',
          );
          this.saving.set(false);
        },
      });
  }
}

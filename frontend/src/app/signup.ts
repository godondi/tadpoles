import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from './core/auth/auth.service';

interface SignupForm {
  email: string;
  displayName: string;
  password: string;
  confirmPassword: string;
}

@Component({
  selector: 'app-signup',
  imports: [FormsModule, RouterLink],
  template: `
    <div class="auth-page">
      <section class="auth-card">
        <div class="auth-copy">
          <h1>Create your Tadpoles account</h1>
          <p>Start with your credentials, then finish your client onboarding profile.</p>
        </div>

        <form class="auth-form" novalidate (ngSubmit)="handleSignup()">
          <label>
            <span>Email</span>
            <input
              type="email"
              [value]="form().email"
              (input)="updateField('email', $event)"
              required
            />
          </label>

          <label>
            <span>Display name</span>
            <input
              type="text"
              pattern="[A-Za-z0-9]*"
              title="Use letters and numbers only."
              [value]="form().displayName"
              (input)="updateField('displayName', $event)"
              required
            />
          </label>

          <label>
            <span>Password</span>
            <input
              type="password"
              [value]="form().password"
              (input)="updateField('password', $event)"
              required
            />
          </label>

          <label>
            <span>Confirm password</span>
            <input
              type="password"
              [value]="form().confirmPassword"
              (input)="updateField('confirmPassword', $event)"
              required
            />
          </label>

          <p class="password-help">
            Use at least 8 characters with upper/lowercase, a number, and a special character.
          </p>

          @if (errorMessage()) {
            <div class="message error" role="alert">{{ errorMessage() }}</div>
          }

          <button type="submit" [disabled]="isSubmitting()">
            {{ isSubmitting() ? 'Creating account…' : 'Create account' }}
          </button>
        </form>

        <p class="auth-footer">
          Already have an account?
          <a routerLink="/login">Sign in</a>
        </p>
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

      .auth-page {
        min-height: calc(100vh - 4rem);
        display: grid;
        place-items: center;
      }

      .auth-card {
        width: min(100%, 38rem);
        background: var(--surface);
        border-radius: 0;
        padding: clamp(1.25rem, 3vw, 2rem);
        border: 1px solid var(--border-gray);
        box-shadow: 0 24px 48px rgba(0, 0, 0, 0.18);
      }

      .auth-copy h1 {
        margin: 0 0 0.5rem;
        color: var(--dark-green);
      }

      .auth-copy p,
      .auth-footer,
      .password-help {
        color: var(--text-secondary);
      }

      .auth-form {
        display: grid;
        gap: 1rem;
        margin-top: 1.5rem;
      }

      label {
        display: grid;
        gap: 0.4375rem;
        color: var(--text-primary);
        font-weight: 600;
      }

      input {
        background: var(--surface);
        color: var(--text-primary);
        border: 1px solid var(--border-gray);
        border-radius: 0;
        padding: 0.75rem 0.875rem;
        min-height: 46px;
        font: inherit;
      }

      input:focus {
        outline: 2px solid rgba(118, 169, 35, 0.3);
        border-color: #76a923;
      }

      button {
        border: 1px solid var(--dark-green);
        border-radius: 0;
        padding: 0.75rem 1rem;
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
        border-color: var(--action-hover);
      }

      .message {
        padding: 0.85rem 1rem;
        border-radius: 0;
        font-size: 0.95rem;
      }

      .message.error {
        background: var(--error-bg);
        color: var(--error-text);
      }

      .auth-footer {
        margin: 1.5rem 0 0;
        text-align: center;
      }

      a {
        color: var(--dark-green);
        font-weight: 700;
        text-decoration: none;
      }

      @media (max-width: 480px) {
        .auth-card {
          padding: 1.25rem;
        }
      }
    `,
  ],
})
export class SignupComponent {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  readonly form = signal<SignupForm>({
    email: '',
    displayName: '',
    password: '',
    confirmPassword: '',
  });
  readonly errorMessage = signal('');
  readonly isSubmitting = signal(false);

  updateField(field: keyof SignupForm, event: Event): void {
    const input = event.target as HTMLInputElement;
    const value = field === 'displayName' ? input.value.replace(/[^A-Za-z0-9]/g, '') : input.value;
    if (field === 'displayName') input.value = value;
    this.form.update((current) => ({ ...current, [field]: value }));
  }

  async handleSignup(): Promise<void> {
    const form = this.form();
    const email = form.email.trim().toLowerCase();
    const displayName = form.displayName;

    if (!email || !displayName || !form.password || !form.confirmPassword) {
      this.errorMessage.set('Please complete all required fields.');
      return;
    }

    if (!/^[A-Za-z0-9]+$/.test(displayName)) {
      this.errorMessage.set('Display name must use letters and numbers only.');
      return;
    }

    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      this.errorMessage.set('Please enter a valid email address.');
      return;
    }

    if (form.password !== form.confirmPassword) {
      this.errorMessage.set('Passwords do not match.');
      return;
    }

    if (!/^(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9])(?=.*[^A-Za-z0-9]).{8,}$/.test(form.password)) {
      this.errorMessage.set(
        'Use at least 8 characters with upper and lower case letters, a number, and a special character.',
      );
      return;
    }

    this.isSubmitting.set(true);
    this.errorMessage.set('');
    try {
      await this.authService.signup({
        email,
        password: form.password,
        displayName,
      });
      await this.router.navigateByUrl('/onboarding');
    } catch (error) {
      this.errorMessage.set(this.toErrorMessage(error));
    } finally {
      this.isSubmitting.set(false);
    }
  }

  private toErrorMessage(error: unknown): string {
    if (typeof error === 'object' && error !== null && 'error' in error) {
      const apiError = (error as { error?: { message?: string } }).error;
      if (apiError?.message) {
        return apiError.message;
      }
    }

    return 'Unable to create your account. Please try again.';
  }
}

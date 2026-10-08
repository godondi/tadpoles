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

        <form class="auth-form" (ngSubmit)="handleSignup()">
          <label>
            <span>Email</span>
            <input type="email" [value]="form().email" (input)="updateField('email', $event)" required />
          </label>

          <label>
            <span>Display name</span>
            <input type="text" [value]="form().displayName" (input)="updateField('displayName', $event)" required />
          </label>

          <label>
            <span>Password</span>
            <input type="password" [value]="form().password" (input)="updateField('password', $event)" required />
          </label>

          <label>
            <span>Confirm password</span>
            <input type="password" [value]="form().confirmPassword" (input)="updateField('confirmPassword', $event)" required />
          </label>

          <p class="password-help">Use at least 8 characters with upper/lowercase, a number, and a special character.</p>

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
  styles: [`
    :host {
      display: block;
      min-height: 100vh;
      background: linear-gradient(135deg, #006044 0%, #76A923 100%);
      padding: 2rem 1rem;
    }

    .auth-page {
      min-height: calc(100vh - 4rem);
      display: grid;
      place-items: center;
    }

    .auth-card {
      width: min(100%, 34rem);
      background: rgba(255, 255, 255, 0.98);
      border-radius: 1.25rem;
      padding: 2rem;
      box-shadow: 0 24px 48px rgba(0, 0, 0, 0.18);
    }

    .auth-copy h1 {
      margin: 0 0 0.5rem;
      color: #006044;
    }

    .auth-copy p,
    .auth-footer,
    .password-help {
      color: #51606f;
    }

    .auth-form {
      display: grid;
      gap: 1rem;
      margin-top: 1.5rem;
    }

    label {
      display: grid;
      gap: 0.45rem;
      color: #1f2937;
      font-weight: 600;
    }

    input {
      border: 1px solid #d5dde5;
      border-radius: 0.75rem;
      padding: 0.85rem 1rem;
      font: inherit;
    }

    input:focus {
      outline: 2px solid rgba(118, 169, 35, 0.3);
      border-color: #76A923;
    }

    button {
      border: none;
      border-radius: 0.75rem;
      padding: 0.9rem 1rem;
      background: linear-gradient(135deg, #76A923 0%, #006044 100%);
      color: white;
      font: inherit;
      font-weight: 700;
      cursor: pointer;
    }

    button:disabled {
      opacity: 0.7;
      cursor: progress;
    }

    .message {
      padding: 0.85rem 1rem;
      border-radius: 0.75rem;
      font-size: 0.95rem;
    }

    .message.error {
      background: #fdecec;
      color: #b42318;
    }

    .auth-footer {
      margin: 1.5rem 0 0;
      text-align: center;
    }

    a {
      color: #006044;
      font-weight: 700;
      text-decoration: none;
    }
  `],
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
    const value = (event.target as HTMLInputElement).value;
    this.form.update((current) => ({ ...current, [field]: value }));
  }

  async handleSignup(): Promise<void> {
    const form = this.form();
    const email = form.email.trim().toLowerCase();
    const displayName = form.displayName.trim();

    if (!email || !displayName || !form.password || !form.confirmPassword) {
      this.errorMessage.set('Please complete all required fields.');
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



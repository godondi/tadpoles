import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { AuthService } from './core/auth/auth.service';

interface LoginForm {
  email: string;
  password: string;
  rememberMe: boolean;
}

@Component({
  selector: 'app-login',
  template: `
    <div class="login-container">
      <div class="login-box">
        <div class="login-header">
          <h1 class="login-title">Tadpoles</h1>
          <p class="login-subtitle">Sign in to continue to your dashboard or complete onboarding.</p>
        </div>

        <form class="login-form" (ngSubmit)="handleLogin()">
          <div class="form-group">
            <label for="email">Email Address</label>
            <input
              id="email"
              type="email"
              placeholder="Enter your email"
              [value]="loginForm().email"
              (input)="updateLoginForm('email', $event)"
              required
              aria-required="true"
            />
          </div>

          <div class="form-group">
            <label for="password">Password</label>
            <input
              id="password"
              type="password"
              placeholder="Enter your password"
              [value]="loginForm().password"
              (input)="updateLoginForm('password', $event)"
              required
              aria-required="true"
            />
          </div>

          <div class="form-group checkbox">
            <label for="remember">
              <input
                id="remember"
                type="checkbox"
                [checked]="loginForm().rememberMe"
                (change)="updateLoginForm('rememberMe', $event)"
              />
              Remember me
            </label>
            <a href="#" class="forgot-password">Forgot password?</a>
          </div>

          @if (errorMessage()) {
            <div class="error-message" role="alert">
              {{ errorMessage() }}
            </div>
          }

          <button type="submit" class="login-btn" [disabled]="isSubmitting()">
            {{ isSubmitting() ? 'Signing In…' : 'Sign In' }}
          </button>
        </form>

        <div class="login-footer">
          <p>
            Don't have an account?
            <a routerLink="/signup" class="signup-link">Sign up here</a>
          </p>
        </div>
      </div>
    </div>
  `,
  styles: [`
    :host {
      display: block;
      width: 100%;
      height: 100vh;
      background: #006044;
      font-family: Arial, 'Helvetica Neue', Helvetica, sans-serif;
      margin: 0;
      padding: 0;
      overflow: hidden;
    }

    .login-container {
      display: flex;
      justify-content: center;
      align-items: center;
      width: 100%;
      height: 100%;
      padding: 0;
    }

    .login-box {
      background: rgba(255, 255, 255, 0.98);
      border-radius: 0;
      box-shadow: 0 24px 50px rgba(0, 0, 0, 0.18);
      width: 100%;
      max-width: 420px;
      padding: 2.5rem;
      margin: 0 1rem;
    }

    .login-header {
      text-align: center;
      margin-bottom: 2rem;
    }

    .login-title {
      font-size: 2rem;
      font-weight: 700;
      color: #006044;
      margin: 0 0 0.5rem 0;
      letter-spacing: 0.01em;
    }

    .login-subtitle {
      font-size: 0.95rem;
      color: #51606f;
      margin: 0;
    }

    .login-form {
      display: flex;
      flex-direction: column;
      gap: 1.5rem;
    }

    .form-group {
      display: flex;
      flex-direction: column;
    }

    .form-group label {
      font-weight: 600;
      color: #333;
      margin-bottom: 0.5rem;
      font-size: 0.95rem;
    }

    .form-group input[type="email"],
    .form-group input[type="password"] {
      padding: 0.75rem;
      border: 2px solid #e0e0e0;
      border-radius: 0;
      font-size: 0.95rem;
      transition: all 0.3s;
    }

    .form-group input[type="email"]:focus,
    .form-group input[type="password"]:focus {
      outline: none;
      border-color: #76A923;
      box-shadow: 0 0 0 3px rgba(118, 169, 35, 0.1);
    }

    .form-group.checkbox {
      flex-direction: row;
      justify-content: space-between;
      align-items: center;
    }

    .form-group.checkbox label {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      margin: 0;
      font-weight: 500;
      color: #333;
      cursor: pointer;
    }

    .form-group.checkbox input[type="checkbox"] {
      width: auto;
      margin: 0;
      cursor: pointer;
      accent-color: #76A923;
    }

    .forgot-password {
      color: #AF8A49;
      text-decoration: none;
      font-size: 0.9rem;
      transition: color 0.3s;
    }

    .forgot-password:hover {
      color: #9a7840;
      text-decoration: underline;
    }

    .error-message {
      padding: 0.75rem;
      background-color: #ffebee;
      color: #c62828;
      border-radius: 0;
      font-size: 0.9rem;
      border-left: 4px solid #c62828;
    }

    .login-btn {
      padding: 0.85rem;
      background-color: #76A923;
      color: white;
      border: none;
      border-radius: 0;
      font-weight: 600;
      font-size: 0.95rem;
      cursor: pointer;
      transition: all 0.3s;
      margin-top: 0.5rem;
    }

    .login-btn:hover {
      background-color: #6a9419;
      transform: translateY(-2px);
      box-shadow: 0 4px 12px rgba(118, 169, 35, 0.3);
    }

    .login-btn:disabled {
      opacity: 0.75;
      cursor: progress;
      transform: none;
      box-shadow: none;
    }

    .login-btn:active {
      transform: translateY(0);
    }

    .login-btn:focus {
      outline: none;
      box-shadow: 0 0 0 3px rgba(118, 169, 35, 0.3);
    }

    .login-footer {
      text-align: center;
      margin-top: 1.5rem;
      padding-top: 1.5rem;
      border-top: 1px solid #e0e0e0;
      color: #666;
      font-size: 0.9rem;
    }

    .signup-link {
      color: #76A923;
      text-decoration: none;
      font-weight: 600;
      transition: color 0.3s;
    }

    .signup-link:hover {
      color: #6a9419;
      text-decoration: underline;
    }

    @media (max-width: 480px) {
      .login-box {
        padding: 2rem 1.5rem;
      }

      .login-title {
        font-size: 1.5rem;
      }

      .login-form {
        gap: 1.25rem;
      }
    }
  `],
  imports: [FormsModule, RouterLink],
  host: {
    'role': 'application',
    'aria-label': 'Login page',
  },
})
export class LoginComponent {
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly authService = inject(AuthService);

  readonly loginForm = signal<LoginForm>({
    email: '',
    password: '',
    rememberMe: false,
  });

  readonly errorMessage = signal('');
  readonly isSubmitting = signal(false);

  updateLoginForm(field: string, event: any): void {
    const value = field === 'rememberMe' ? event.target.checked : event.target.value;
    this.loginForm.update(form => ({
      ...form,
      [field]: value,
    }));
  }

  async handleLogin(): Promise<void> {
    const form = this.loginForm();
    const trimmedEmail = form.email.trim();

    if (!trimmedEmail || !form.password) {
      this.errorMessage.set('Please enter both email and password');
      setTimeout(() => this.errorMessage.set(''), 4000);
      return;
    }

    if (!this.isValidEmail(trimmedEmail)) {
      this.errorMessage.set('Please enter a valid email address');
      setTimeout(() => this.errorMessage.set(''), 4000);
      return;
    }

    if (form.password.length < 8) {
      this.errorMessage.set('Password must be at least 8 characters');
      setTimeout(() => this.errorMessage.set(''), 4000);
      return;
    }

    this.errorMessage.set('');
    this.isSubmitting.set(true);

    try {
      await this.authService.login(trimmedEmail, form.password);

      const redirectTarget = this.route.snapshot.queryParamMap.get('redirectTo');
      const nextRoute = this.authService.hasCompletedOnboarding()
        ? redirectTarget || '/'
        : '/onboarding';

      await this.router.navigateByUrl(nextRoute);
    } catch (error) {
      this.errorMessage.set(this.toErrorMessage(error));
    } finally {
      this.isSubmitting.set(false);
    }
  }

  private isValidEmail(email: string): boolean {
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    return emailRegex.test(email);
  }

  private toErrorMessage(error: unknown): string {
    if (typeof error === 'object' && error !== null && 'error' in error) {
      const apiError = (error as { error?: { message?: string } }).error;
      if (apiError?.message) {
        return apiError.message;
      }
    }

    return 'Unable to sign in. Please try again.';
  }
}

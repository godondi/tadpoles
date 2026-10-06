import { Component, signal, inject } from '@angular/core';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';

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
          <h1 class="login-title">📈 Pole Trading</h1>
          <p class="login-subtitle">Professional Trading Platform</p>
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

          <button type="submit" class="login-btn">
            Sign In
          </button>
        </form>

        <div class="login-footer">
          <p>
            Don't have an account?
            <a href="#" class="signup-link">Sign up here</a>
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
      background: linear-gradient(135deg, #006044 0%, #76A923 100%);
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
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
      background: white;
      border-radius: 12px;
      box-shadow: 0 10px 40px rgba(0, 0, 0, 0.2);
      width: 100%;
      max-width: 400px;
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
    }

    .login-subtitle {
      font-size: 0.95rem;
      color: #666;
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
      border-radius: 6px;
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
      border-radius: 6px;
      font-size: 0.9rem;
      border-left: 4px solid #c62828;
    }

    .login-btn {
      padding: 0.85rem;
      background-color: #76A923;
      color: white;
      border: none;
      border-radius: 6px;
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
  imports: [FormsModule],
  host: {
    'role': 'application',
    'aria-label': 'Login page',
  },
})
export class LoginComponent {
  private readonly router = inject(Router);

  readonly loginForm = signal<LoginForm>({
    email: '',
    password: '',
    rememberMe: false,
  });

  readonly errorMessage = signal('');

  updateLoginForm(field: string, event: any): void {
    const value = field === 'rememberMe' ? event.target.checked : event.target.value;
    this.loginForm.update(form => ({
      ...form,
      [field]: value,
    }));
  }

  handleLogin(): void {
    const form = this.loginForm();
    
    if (!form.email || !form.password) {
      this.errorMessage.set('Please enter both email and password');
      setTimeout(() => this.errorMessage.set(''), 4000);
      return;
    }

    if (!this.isValidEmail(form.email)) {
      this.errorMessage.set('Please enter a valid email address');
      setTimeout(() => this.errorMessage.set(''), 4000);
      return;
    }

    if (form.password.length < 6) {
      this.errorMessage.set('Password must be at least 6 characters');
      setTimeout(() => this.errorMessage.set(''), 4000);
      return;
    }

    // Simulate successful login
    console.log('Login attempt:', { email: form.email, rememberMe: form.rememberMe });
    
    // Navigate to home/dashboard
    this.router.navigate(['']);
  }

  private isValidEmail(email: string): boolean {
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    return emailRegex.test(email);
  }
}

import { Component, signal, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { UserService } from './user.service';

@Component({
  selector: 'app-profile',
  template: `
    <div class="profile-container">
      <nav class="navbar" role="navigation" aria-label="Site navigation">
        <div class="navbar-content">
          <a routerLink="/" class="navbar-brand" aria-label="Pole Trading Dashboard">
            <span class="brand-icon">📈</span>
            <span class="brand-name">Pole</span>
          </a>
          <div class="navbar-right">
            <a routerLink="/" class="back-button" aria-label="Back to dashboard">
              ← Dashboard
            </a>
          </div>
        </div>
      </nav>

      <main class="profile-main" role="main">
        <div class="profile-header">
          <div class="header-content">
            <div class="avatar-large">{{ profile().avatar }}</div>
            <div class="header-text">
              <h1>{{ profile().name }}</h1>
              <p class="email-text">{{ profile().email }}</p>
            </div>
          </div>
        </div>

        <div class="profile-content">
          <!-- Personal Information Section -->
          <div class="profile-card">
            <h2>Personal Information</h2>
            <div class="info-grid">
              <div class="info-item">
                <label>Full Name</label>
                <p>{{ profile().name }}</p>
              </div>
              <div class="info-item">
                <label>Email</label>
                <p>{{ profile().email }}</p>
              </div>
              <div class="info-item">
                <label>Phone</label>
                <p>{{ profile().phone }}</p>
              </div>
              <div class="info-item">
                <label>Date of Birth</label>
                <p>{{ profile().dateOfBirth }}</p>
              </div>
              <div class="info-item">
                <label>Address</label>
                <p>{{ profile().address }}<br/>{{ profile().city }}, {{ profile().state }} {{ profile().zipCode }}</p>
              </div>
              <div class="info-item">
                <label>Account Number</label>
                <p>{{ profile().accountNumber }}</p>
              </div>
            </div>
          </div>

          <!-- Account Information Section -->
          <div class="profile-card">
            <h2>Account Information</h2>
            <div class="info-grid">
              <div class="info-item">
                <label>Account Type</label>
                <p>{{ profile().accountType }}</p>
              </div>
              <div class="info-item">
                <label>Account Status</label>
                <p><span class="status-badge">{{ profile().accountStatus }}</span></p>
              </div>
              <div class="info-item">
                <label>Member Since</label>
                <p>{{ profile().memberSince }}</p>
              </div>
              <div class="info-item">
                <label>Verification Status</label>
                <p><span class="verification-badge">{{ profile().verificationStatus }}</span></p>
              </div>
              <div class="info-item">
                <label>Last Login</label>
                <p>{{ profile().lastLoginDate }}</p>
              </div>
            </div>
          </div>

          <!-- Change Password Section -->
          <div class="profile-card">
            <h2>Security</h2>
            <div class="password-form">
              <div class="form-group">
                <label for="current-password">Current Password</label>
                <input
                  id="current-password"
                  type="password"
                  class="form-input"
                  [value]="passwordForm().currentPassword"
                  (input)="updatePasswordForm('currentPassword', $event)"
                  placeholder="Enter current password"
                  aria-label="Current password"
                />
              </div>

              <div class="form-group">
                <label for="new-password">New Password</label>
                <input
                  id="new-password"
                  type="password"
                  class="form-input"
                  [value]="passwordForm().newPassword"
                  (input)="updatePasswordForm('newPassword', $event)"
                  placeholder="Enter new password"
                  aria-label="New password"
                />
              </div>

              <div class="form-group">
                <label for="confirm-password">Confirm Password</label>
                <input
                  id="confirm-password"
                  type="password"
                  class="form-input"
                  [value]="passwordForm().confirmPassword"
                  (input)="updatePasswordForm('confirmPassword', $event)"
                  placeholder="Confirm new password"
                  aria-label="Confirm password"
                />
              </div>

              @if (passwordError()) {
                <div class="error-message" role="alert">{{ passwordError() }}</div>
              }

              @if (passwordSuccess()) {
                <div class="success-message" role="status">{{ passwordSuccess() }}</div>
              }

              <button class="btn-primary" (click)="changePassword()" aria-label="Update password">
                Update Password
              </button>
            </div>
          </div>
        </div>
      </main>
    </div>
  `,
  styles: [`
    :host {
      display: block;
      width: 100%;
      min-height: 100vh;
      background: linear-gradient(135deg, #f8f9fa 0%, #f0f1f3 100%);
    }

    .profile-container {
      width: 100%;
      display: flex;
      flex-direction: column;
    }

    .navbar {
      background-color: #006044;
      border-bottom: 3px solid #76A923;
      padding: 1rem 2rem;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
      position: sticky;
      top: 0;
      z-index: 100;
    }

    .navbar-content {
      max-width: 1400px;
      margin: 0 auto;
      display: flex;
      justify-content: space-between;
      align-items: center;
    }

    .navbar-brand {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      color: white;
      text-decoration: none;
      font-size: 1.5rem;
      font-weight: 700;
      cursor: pointer;
      transition: opacity 0.2s ease;
    }

    .navbar-brand:hover {
      opacity: 0.8;
    }

    .navbar-brand:focus-visible {
      outline: 3px solid #76A923;
      outline-offset: 2px;
      border-radius: 4px;
    }

    .brand-icon {
      font-size: 1.75rem;
    }

    .brand-name {
      color: white;
    }

    .navbar-right {
      display: flex;
      gap: 1.5rem;
      align-items: center;
    }

    .back-button {
      color: white;
      text-decoration: none;
      padding: 0.5rem 1rem;
      border-radius: 4px;
      transition: background-color 0.2s ease;
      cursor: pointer;
    }

    .back-button:hover {
      background-color: rgba(118, 169, 35, 0.2);
    }

    .back-button:focus-visible {
      outline: 3px solid #76A923;
      outline-offset: 2px;
    }

    .profile-main {
      flex: 1;
      padding: 2rem;
      max-width: 1000px;
      margin: 0 auto;
      width: 100%;
    }

    .profile-header {
      background: white;
      padding: 2rem;
      border-radius: 8px;
      margin-bottom: 2rem;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
    }

    .header-content {
      display: flex;
      align-items: center;
      gap: 2rem;
    }

    .avatar-large {
      font-size: 3.5rem;
      width: 100px;
      height: 100px;
      display: flex;
      align-items: center;
      justify-content: center;
      background: linear-gradient(135deg, #76A923 0%, #006044 100%);
      border-radius: 50%;
      color: white;
    }

    .header-text h1 {
      color: #006044;
      margin: 0;
      font-size: 1.75rem;
    }

    .email-text {
      color: #666;
      margin: 0.5rem 0 0 0;
      font-size: 1rem;
    }

    .profile-content {
      display: flex;
      flex-direction: column;
      gap: 1.5rem;
    }

    .profile-card {
      background: white;
      padding: 1.5rem;
      border-radius: 8px;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
    }

    .profile-card h2 {
      color: #006044;
      margin: 0 0 1.5rem 0;
      font-size: 1.25rem;
      border-bottom: 2px solid #76A923;
      padding-bottom: 0.75rem;
    }

    .info-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
      gap: 1.5rem;
    }

    .info-item {
      display: flex;
      flex-direction: column;
    }

    .info-item label {
      color: #999;
      font-weight: 600;
      font-size: 0.75rem;
      margin-bottom: 0.5rem;
      text-transform: uppercase;
      letter-spacing: 0.5px;
    }

    .info-item p {
      color: #006044;
      font-size: 1rem;
      margin: 0;
      font-weight: 500;
      line-height: 1.5;
    }

    .status-badge, .verification-badge {
      display: inline-block;
      padding: 0.35rem 0.75rem;
      border-radius: 4px;
      font-size: 0.875rem;
      font-weight: 600;
      background-color: #76A923;
      color: white;
    }

    /* Password Form Styles */
    .password-form {
      display: flex;
      flex-direction: column;
      gap: 1rem;
    }

    .form-group {
      display: flex;
      flex-direction: column;
    }

    .form-group label {
      color: #006044;
      font-weight: 600;
      margin-bottom: 0.5rem;
      font-size: 0.95rem;
    }

    .form-input {
      padding: 0.75rem 1rem;
      border: 1px solid #ddd;
      border-radius: 4px;
      font-size: 1rem;
      transition: border-color 0.2s ease, box-shadow 0.2s ease;
    }

    .form-input:hover {
      border-color: #76A923;
    }

    .form-input:focus {
      outline: none;
      border-color: #76A923;
      box-shadow: 0 0 0 3px rgba(118, 169, 35, 0.1);
    }

    .error-message {
      padding: 1rem;
      background-color: #f8d7da;
      color: #721c24;
      border: 1px solid #f5c6cb;
      border-radius: 4px;
      font-size: 0.95rem;
    }

    .success-message {
      padding: 1rem;
      background-color: #d4edda;
      color: #155724;
      border: 1px solid #c3e6cb;
      border-radius: 4px;
      font-size: 0.95rem;
    }

    .btn-primary {
      align-self: flex-start;
      background: linear-gradient(135deg, #76A923 0%, #006044 100%);
      color: white;
      border: none;
      padding: 0.75rem 1.5rem;
      border-radius: 4px;
      cursor: pointer;
      font-size: 1rem;
      font-weight: 600;
      transition: transform 0.2s ease, box-shadow 0.2s ease;
    }

    .btn-primary:hover {
      transform: translateY(-2px);
      box-shadow: 0 4px 12px rgba(118, 169, 35, 0.3);
    }

    .btn-primary:focus-visible {
      outline: 3px solid #AF8A49;
      outline-offset: 2px;
    }

    @media (max-width: 768px) {
      .profile-main {
        padding: 1rem;
      }

      .header-content {
        flex-direction: column;
        text-align: center;
      }

      .header-text h1 {
        font-size: 1.5rem;
      }

      .info-grid {
        grid-template-columns: 1fr;
      }

      .navbar {
        padding: 1rem;
      }

      .navbar-content {
        flex-direction: column;
        gap: 1rem;
      }

      .avatar-large {
        width: 80px;
        height: 80px;
        font-size: 3rem;
      }
    }
  `],
  imports: [RouterLink, CommonModule, FormsModule],
})
export class ProfileComponent {
  private userService = inject(UserService);

  profile = this.userService.getProfile();

  passwordForm = signal({
    currentPassword: '',
    newPassword: '',
    confirmPassword: '',
  });

  passwordError = signal('');
  passwordSuccess = signal('');

  updatePasswordForm(field: 'currentPassword' | 'newPassword' | 'confirmPassword', event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    const current = this.passwordForm();
    this.passwordForm.set({ ...current, [field]: value });
    this.passwordError.set('');
    this.passwordSuccess.set('');
  }

  changePassword(): void {
    const form = this.passwordForm();

    if (!form.currentPassword || !form.newPassword || !form.confirmPassword) {
      this.passwordError.set('All fields are required.');
      return;
    }

    if (form.newPassword.length < 8) {
      this.passwordError.set('New password must be at least 8 characters long.');
      return;
    }

    if (form.newPassword !== form.confirmPassword) {
      this.passwordError.set('New passwords do not match.');
      return;
    }

    if (form.currentPassword === form.newPassword) {
      this.passwordError.set('New password must be different from current password.');
      return;
    }

    this.passwordError.set('');
    this.passwordSuccess.set('✓ Password updated successfully!');
    this.passwordForm.set({
      currentPassword: '',
      newPassword: '',
      confirmPassword: '',
    });

    setTimeout(() => {
      this.passwordSuccess.set('');
    }, 3000);
  }
}

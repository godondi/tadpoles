import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { UserService } from './user.service';

@Component({
  selector: 'app-profile',
  template: `
    <div class="profile-container">
      <main class="profile-main" role="main">
        <div class="profile-header">
          <div class="header-content">
            <div class="avatar-large">{{ initials() }}</div>
            <div class="header-text">
              <h1>{{ profile().displayName || profile().clientName || 'Client Profile' }}</h1>
              <p class="email-text">{{ profile().email }}</p>
              <p class="subtle-text">Client ID: {{ profile().clientId ?? 'Pending' }}</p>
            </div>
          </div>
        </div>

        @if (isLoading()) {
          <div class="status-card">Loading profile…</div>
        }

        @if (errorMessage()) {
          <div class="error-message" role="alert">{{ errorMessage() }}</div>
        }

        <div class="profile-content">
          <div class="profile-card">
            <h2>Identity</h2>
            <div class="info-grid">
              <div class="info-item">
                <label>Display name</label>
                <p>{{ profile().displayName || 'Not provided' }}</p>
              </div>
              <div class="info-item">
                <label>Client name</label>
                <p>{{ profile().clientName || 'Not provided' }}</p>
              </div>
              <div class="info-item">
                <label>Email</label>
                <p class="mono-value">{{ profile().email || 'Not provided' }}</p>
              </div>
              <div class="info-item">
                <label>Phone</label>
                <p class="mono-value">{{ profile().phone || 'Not provided' }}</p>
              </div>
              <div class="info-item">
                <label>Date of birth</label>
                <p>{{ profile().dateOfBirth || 'Not provided' }}</p>
              </div>
              <div class="info-item">
                <label>Onboarding status</label>
                <p><span class="status-badge">{{ profile().onboardingComplete ? 'Complete' : 'In progress' }}</span></p>
              </div>
            </div>
          </div>

          <div class="profile-card">
            <h2>Address & Contact Preferences</h2>
            <div class="info-grid">
              <div class="info-item wide">
                <label>Address</label>
                <p>
                  {{ profile().addressLine1 || 'Not provided' }}
                  @if (profile().addressLine2) {<br />{{ profile().addressLine2 }}}
                </p>
              </div>
              <div class="info-item">
                <label>City</label>
                <p>{{ profile().city || 'Not provided' }}</p>
              </div>
              <div class="info-item">
                <label>State</label>
                <p>{{ profile().state || 'Not provided' }}</p>
              </div>
              <div class="info-item">
                <label>Postal code</label>
                <p>{{ profile().postalCode || 'Not provided' }}</p>
              </div>
              <div class="info-item">
                <label>Country</label>
                <p>{{ profile().country || 'Not provided' }}</p>
              </div>
              <div class="info-item">
                <label>Preferred contact method</label>
                <p>{{ profile().preferredContactMethod || 'Not provided' }}</p>
              </div>
              <div class="info-item">
                <label>Paperless statements</label>
                <p>{{ profile().paperlessStatements ? 'Enabled' : 'Disabled' }}</p>
              </div>
              <div class="info-item">
                <label>Marketing updates</label>
                <p>{{ profile().marketingOptIn ? 'Subscribed' : 'Not subscribed' }}</p>
              </div>
            </div>
          </div>

          <div class="profile-card">
            <h2>Investment Profile</h2>
            <div class="info-grid">
              <div class="info-item">
                <label>Employment status</label>
                <p>{{ profile().employmentStatus || 'Not provided' }}</p>
              </div>
              <div class="info-item">
                <label>Net worth</label>
                <p>{{ profile().netWorth !== null ? (profile().netWorth | number:'1.0-2') : 'Not provided' }}</p>
              </div>
              <div class="info-item">
                <label>Risk tolerance</label>
                <p>{{ profile().riskTolerance || 'Not provided' }}</p>
              </div>
              <div class="info-item wide">
                <label>Investment objective</label>
                <p>{{ profile().investmentObjective || 'Not provided' }}</p>
              </div>
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
      background: #f8f9fa;
    }

    .profile-container {
      width: 100%;
      display: flex;
      flex-direction: column;
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
      border-radius: 0;
      margin-bottom: 2rem;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
    }

    .header-content {
      display: flex;
      align-items: center;
      gap: 2rem;
    }

    .avatar-large {
       font-size: 2rem;
       font-weight: 700;
       letter-spacing: 0.12em;
      width: 100px;
      height: 100px;
      display: flex;
      align-items: center;
      justify-content: center;
      background: #006044;
      border-radius: 0;
      color: white;
    }

    .header-text h1 {
      color: #006044;
      margin: 0;
      font-size: 1.75rem;
       font-weight: 700;
    }

    .email-text,
    .subtle-text {
      color: #666;
      margin: 0.5rem 0 0 0;
      font-size: 1rem;
    }

    .subtle-text {
      font-size: 0.9rem;
    }

    .profile-content {
      display: flex;
      flex-direction: column;
      gap: 1.5rem;
    }

    .profile-card {
      background: white;
      padding: 1.5rem;
      border-radius: 0;
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

    .wide {
      grid-column: span 2;
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

    .status-badge {
      display: inline-block;
      padding: 0.35rem 0.75rem;
      border-radius: 0;
      font-size: 0.875rem;
      font-weight: 600;
      background-color: #76A923;
      color: white;
    }

    .error-message {
      padding: 1rem;
      background-color: #f8d7da;
      color: #721c24;
      border: 1px solid #f5c6cb;
      border-radius: 0;
      font-size: 0.95rem;
    }

    .status-card {
      padding: 1rem;
      background-color: #eef7ef;
      color: #006044;
      border-radius: 0;
      margin-bottom: 1rem;
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

      .avatar-large {
        width: 80px;
        height: 80px;
        font-size: 1.6rem;
      }
    }
  `],
  imports: [CommonModule],
})
export class ProfileComponent implements OnInit {
  private readonly userService = inject(UserService);

  readonly profile = this.userService.getProfile();
  readonly errorMessage = signal('');
  readonly isLoading = signal(false);

  readonly initials = signal('CP');

  async ngOnInit(): Promise<void> {
    this.setInitials();
    if (this.profile().onboardingComplete && this.profile().email) {
      return;
    }

    this.isLoading.set(true);
    try {
      await this.userService.loadCurrentProfile();
      this.setInitials();
    } catch (error) {
      this.errorMessage.set(this.toErrorMessage(error));
    } finally {
      this.isLoading.set(false);
    }
  }

  private setInitials(): void {
    const source = this.profile().displayName || this.profile().clientName || this.profile().email || 'Client Profile';
    this.initials.set(
      source
        .split(/\s+/)
        .slice(0, 2)
        .map((part) => part[0]?.toUpperCase() ?? '')
        .join('') || 'CP',
    );
  }

  private toErrorMessage(error: unknown): string {
    if (typeof error === 'object' && error !== null && 'error' in error) {
      const apiError = (error as { error?: { message?: string } }).error;
      if (apiError?.message) {
        return apiError.message;
      }
    }

    return 'Unable to load your profile.';
  }
}

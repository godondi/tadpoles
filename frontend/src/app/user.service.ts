import { Injectable, PLATFORM_ID, inject, signal } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { buildApiUrl } from './core/api/api-url';

const PROFILE_STORAGE_KEY = 'tadpoles.user.profile';

export interface UserProfile {
  userId: number | null;
  clientId: number | null;
  email: string;
  displayName: string;
  clientName: string;
  phone: string;
  dateOfBirth: string;
  addressLine1: string;
  addressLine2: string;
  city: string;
  state: string;
  postalCode: string;
  country: string;
  employmentStatus: string;
  netWorth: number | null;
  riskTolerance: string;
  investmentObjective: string;
  preferredContactMethod: string;
  paperlessStatements: boolean;
  marketingOptIn: boolean;
  onboardingComplete: boolean;
}

export interface CompleteClientOnboardingRequest {
  clientName: string;
  phone: string;
  dateOfBirth: string;
  addressLine1: string;
  addressLine2: string;
  city: string;
  state: string;
  postalCode: string;
  country: string;
  employmentStatus: string;
  netWorth: number | null;
  riskTolerance: string;
  investmentObjective: string;
  preferredContactMethod: string;
  paperlessStatements: boolean;
  marketingOptIn: boolean;
}

interface UserProfileResponse {
  userId: number;
  clientId: number | null;
  email: string;
  displayName: string;
  clientName: string | null;
  phone: string | null;
  dateOfBirth: string | null;
  addressLine1: string | null;
  addressLine2: string | null;
  city: string | null;
  state: string | null;
  postalCode: string | null;
  country: string | null;
  employmentStatus: string | null;
  netWorth: number | null;
  riskTolerance: string | null;
  investmentObjective: string | null;
  preferredContactMethod: string | null;
  paperlessStatements: boolean;
  marketingOptIn: boolean;
  onboardingComplete: boolean;
}

const EMPTY_PROFILE: UserProfile = {
  userId: null,
  clientId: null,
  email: '',
  displayName: '',
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
  onboardingComplete: false,
};

@Injectable({
  providedIn: 'root',
})
export class UserService {
  private readonly http = inject(HttpClient);
  private readonly platformId = inject(PLATFORM_ID);
  private readonly userProfile = signal<UserProfile>(this.readStoredProfile());
  readonly loading = signal(false);
  readonly errorMessage = signal('');

  getProfile() {
    return this.userProfile.asReadonly();
  }

  getProfileValue(): UserProfile {
    return this.userProfile();
  }

  setProfile(profile: UserProfile): void {
    this.userProfile.set(profile);
    this.persistProfile(profile);
  }

  updateProfile(updates: Partial<UserProfile>): void {
    const current = this.userProfile();
    const nextProfile = { ...current, ...updates };
    this.userProfile.set(nextProfile);
    this.persistProfile(nextProfile);
  }

  async loadCurrentProfile(): Promise<UserProfile> {
    this.loading.set(true);
    this.errorMessage.set('');

    try {
      const response = await firstValueFrom(
        this.http.get<UserProfileResponse>(buildApiUrl('/api/users/me/profile', this.platformId), {
          headers: this.buildAuthHeaders(),
        }),
      );

      const profile = this.mapProfile(response);
      this.setProfile(profile);
      return profile;
    } catch (error) {
      this.errorMessage.set(this.toErrorMessage(error, 'Unable to load your profile right now.'));
      throw error;
    } finally {
      this.loading.set(false);
    }
  }

  async completeOnboarding(request: CompleteClientOnboardingRequest): Promise<UserProfile> {
    this.loading.set(true);
    this.errorMessage.set('');

    try {
      const response = await firstValueFrom(
        this.http.put<UserProfileResponse>(buildApiUrl('/api/users/me/onboarding', this.platformId), request, {
          headers: this.buildAuthHeaders(),
        }),
      );

      const profile = this.mapProfile(response);
      this.setProfile(profile);
      return profile;
    } catch (error) {
      this.errorMessage.set(this.toErrorMessage(error, 'Unable to complete onboarding.'));
      throw error;
    } finally {
      this.loading.set(false);
    }
  }

  clearProfile(): void {
    this.userProfile.set(EMPTY_PROFILE);
    if (typeof localStorage !== 'undefined') {
      localStorage.removeItem(PROFILE_STORAGE_KEY);
    }
  }

  private mapProfile(response: UserProfileResponse): UserProfile {
    return {
      userId: response.userId,
      clientId: response.clientId,
      email: response.email,
      displayName: response.displayName,
      clientName: response.clientName ?? '',
      phone: response.phone ?? '',
      dateOfBirth: response.dateOfBirth ?? '',
      addressLine1: response.addressLine1 ?? '',
      addressLine2: response.addressLine2 ?? '',
      city: response.city ?? '',
      state: response.state ?? '',
      postalCode: response.postalCode ?? '',
      country: response.country ?? '',
      employmentStatus: response.employmentStatus ?? '',
      netWorth: response.netWorth,
      riskTolerance: response.riskTolerance ?? '',
      investmentObjective: response.investmentObjective ?? '',
      preferredContactMethod: response.preferredContactMethod ?? '',
      paperlessStatements: response.paperlessStatements,
      marketingOptIn: response.marketingOptIn,
      onboardingComplete: response.onboardingComplete,
    };
  }

  private buildAuthHeaders(): HttpHeaders {
    const token = typeof localStorage === 'undefined' ? null : localStorage.getItem('tadpoles.auth.token');
    return new HttpHeaders(token ? { Authorization: `Bearer ${token}` } : {});
  }

  private readStoredProfile(): UserProfile {
    if (typeof localStorage === 'undefined') {
      return EMPTY_PROFILE;
    }

    const rawProfile = localStorage.getItem(PROFILE_STORAGE_KEY);
    if (!rawProfile) {
      return EMPTY_PROFILE;
    }

    try {
      return { ...EMPTY_PROFILE, ...(JSON.parse(rawProfile) as Partial<UserProfile>) };
    } catch {
      localStorage.removeItem(PROFILE_STORAGE_KEY);
      return EMPTY_PROFILE;
    }
  }

  private persistProfile(profile: UserProfile): void {
    if (typeof localStorage === 'undefined') {
      return;
    }

    localStorage.setItem(PROFILE_STORAGE_KEY, JSON.stringify(profile));
  }

  private toErrorMessage(error: unknown, fallback: string): string {
    if (typeof error === 'object' && error !== null && 'error' in error) {
      const apiError = (error as { error?: { message?: string } }).error;
      if (apiError?.message) {
        return apiError.message;
      }
    }

    return fallback;
  }
}

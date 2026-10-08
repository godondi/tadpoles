import { Injectable } from '@angular/core';
import { signal } from '@angular/core';

export interface UserProfile {
  id: string;
  name: string;
  email: string;
  phone: string;
  accountNumber: string;
  memberSince: string;
  verificationStatus: string;
  avatar: string;
  address: string;
  city: string;
  state: string;
  zipCode: string;
  country: string;
  dateOfBirth: string;
  ssn: string;
  employmentStatus: string;
  netWorth: number;
  riskTolerance: string;
  investmentObjective: string;
  preferredContactMethod: string;
  accountType: string;
  accountStatus: string;
  totalDeposits: number;
  totalWithdrawals: number;
  accountOpenDate: string;
  lastLoginDate: string;
  twoFactorEnabled: boolean;
  paperlessStatements: boolean;
  marketingOptIn: boolean;
}

@Injectable({
  providedIn: 'root',
})
export class UserService {
  private userProfile = signal<UserProfile>({
    id: 'USR-2024-001',
    name: 'John Anderson',
    email: 'john.anderson@example.com',
    phone: '+1 (555) 123-4567',
    accountNumber: 'PA-2024-1234567',
    memberSince: 'January 15, 2024',
    verificationStatus: 'Verified',
    avatar: 'JA',
    address: '1234 Investment Ave',
    city: 'New York',
    state: 'NY',
    zipCode: '10001',
    country: 'United States',
    dateOfBirth: 'March 15, 1985',
    ssn: '***-**-7890',
    employmentStatus: 'Employed',
    netWorth: 500000,
    riskTolerance: 'Moderate',
    investmentObjective: 'Long-term growth',
    preferredContactMethod: 'Email',
    accountType: 'Individual Brokerage',
    accountStatus: 'Active',
    totalDeposits: 250000,
    totalWithdrawals: 45000,
    accountOpenDate: 'January 15, 2024',
    lastLoginDate: 'September 30, 2026',
    twoFactorEnabled: true,
    paperlessStatements: true,
    marketingOptIn: false,
  });

  getProfile() {
    return this.userProfile.asReadonly();
  }

  getProfileValue(): UserProfile {
    return this.userProfile();
  }

  updateProfile(updates: Partial<UserProfile>): void {
    const current = this.userProfile();
    this.userProfile.set({ ...current, ...updates });
  }
}

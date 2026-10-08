import { Injectable, PLATFORM_ID, inject, signal } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { UserService } from '../../user.service';

const AUTH_STORAGE_KEY = 'tadpoles.authenticated';
const AUTH_EMAIL_KEY = 'tadpoles.auth.email';

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private readonly platformId = inject(PLATFORM_ID);
  private readonly userService = inject(UserService);
  private readonly isBrowser = isPlatformBrowser(this.platformId);
  private readonly authenticated = signal(this.readStoredAuthState());

  readonly isAuthenticated = this.authenticated.asReadonly();

  constructor() {
    if (!this.isBrowser) {
      return;
    }

    const storedEmail = localStorage.getItem(AUTH_EMAIL_KEY);
    if (storedEmail) {
      this.userService.updateProfile({ email: storedEmail });
    }
  }

  login(email: string): void {
    this.userService.updateProfile({ email });

    if (this.isBrowser) {
      localStorage.setItem(AUTH_STORAGE_KEY, 'true');
      localStorage.setItem(AUTH_EMAIL_KEY, email);
    }

    this.authenticated.set(true);
  }

  logout(): void {
    if (this.isBrowser) {
      localStorage.removeItem(AUTH_STORAGE_KEY);
      localStorage.removeItem(AUTH_EMAIL_KEY);
    }

    this.authenticated.set(false);
  }

  private readStoredAuthState(): boolean {
    if (!this.isBrowser) {
      return false;
    }

    return localStorage.getItem(AUTH_STORAGE_KEY) === 'true';
  }
}



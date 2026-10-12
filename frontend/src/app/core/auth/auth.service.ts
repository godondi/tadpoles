import { Injectable, PLATFORM_ID, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { UserService } from '../../user.service';
import { buildApiUrl } from '../api/api-url';

const AUTH_STORAGE_KEY = 'tadpoles.auth.session';
const AUTH_TOKEN_KEY = 'tadpoles.auth.token';

export interface AuthSession {
  token: string;
  tokenType: string;
  expiresIn: number;
  userId: number;
  email: string;
  displayName: string;
  clientId: number | null;
  onboardingComplete: boolean;
}

export interface SignupRequest {
  email: string;
  password: string;
  displayName: string;
}

interface LoginResponse {
  token: string;
  tokenType: string;
  expiresIn: number;
  userId: number;
  email: string;
  displayName: string;
  clientId: number | null;
  onboardingComplete: boolean;
}

interface SignupResponse extends LoginResponse {
  role: string;
}

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly platformId = inject(PLATFORM_ID);
  private readonly userService = inject(UserService);
  private readonly session = signal<AuthSession | null>(this.readStoredSession());
  private readonly authenticated = signal(this.session() !== null);
  private readonly onboardingComplete = signal(this.session()?.onboardingComplete ?? false);

  readonly isAuthenticated = this.authenticated.asReadonly();
  readonly hasCompletedOnboarding = this.onboardingComplete.asReadonly();

  constructor() {
    const storedSession = this.session();
    if (storedSession) {
      this.userService.updateProfile({
        userId: storedSession.userId,
        clientId: storedSession.clientId,
        email: storedSession.email,
        displayName: storedSession.displayName,
        onboardingComplete: storedSession.onboardingComplete,
      });
    }
  }

  async login(email: string, password: string): Promise<void> {
    const response = await firstValueFrom(
      this.http.post<LoginResponse>(buildApiUrl('/auth/login', this.platformId), {
        username: email.trim().toLowerCase(),
        password,
      }),
    );

    this.applySession(response);
    await this.userService.loadCurrentProfile();
  }

  async signup(request: SignupRequest): Promise<void> {
    const response = await firstValueFrom(
      this.http.post<SignupResponse>(buildApiUrl('/auth/signup', this.platformId), {
        email: request.email.trim().toLowerCase(),
        password: request.password,
        displayName: request.displayName.trim(),
      }),
    );

    this.applySession(response);
    this.userService.updateProfile({
      userId: response.userId,
      clientId: response.clientId,
      email: response.email,
      displayName: response.displayName,
      onboardingComplete: response.onboardingComplete,
    });
  }

  logout(): void {
    if (typeof localStorage !== 'undefined') {
      localStorage.removeItem(AUTH_STORAGE_KEY);
      localStorage.removeItem(AUTH_TOKEN_KEY);
    }

    this.session.set(null);
    this.authenticated.set(false);
    this.onboardingComplete.set(false);
    this.userService.clearProfile();
  }

  getAccessToken(): string | null {
    return this.session()?.token ?? null;
  }

  updateOnboardingStatus(onboardingComplete: boolean, clientId: number | null): void {
    const currentSession = this.session();
    if (!currentSession) {
      return;
    }

    this.applySession({
      ...currentSession,
      clientId,
      onboardingComplete,
    });
  }

  private applySession(response: LoginResponse): void {
    const session: AuthSession = {
      token: response.token,
      tokenType: response.tokenType,
      expiresIn: response.expiresIn,
      userId: response.userId,
      email: response.email,
      displayName: response.displayName,
      clientId: response.clientId,
      onboardingComplete: response.onboardingComplete,
    };

    if (typeof localStorage !== 'undefined') {
      localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(session));
      localStorage.setItem(AUTH_TOKEN_KEY, session.token);
    }

    this.session.set(session);
    this.authenticated.set(true);
    this.onboardingComplete.set(session.onboardingComplete);
  }

  private readStoredSession(): AuthSession | null {
    if (typeof localStorage === 'undefined') {
      return null;
    }

    const rawSession = localStorage.getItem(AUTH_STORAGE_KEY);
    if (!rawSession) {
      return null;
    }

    try {
      return JSON.parse(rawSession) as AuthSession;
    } catch {
      localStorage.removeItem(AUTH_STORAGE_KEY);
      localStorage.removeItem(AUTH_TOKEN_KEY);
      return null;
    }
  }
}



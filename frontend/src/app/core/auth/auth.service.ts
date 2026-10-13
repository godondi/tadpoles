import { Injectable, PLATFORM_ID, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { UserService } from '../../user.service';
import { buildApiUrl } from '../api/api-url';
import { WorkspaceRole, resolveWorkspaceLandingRoute, toWorkspaceRoles } from './workspace-roles';

const AUTH_STORAGE_KEY = 'tadpoles.auth.session';
const AUTH_TOKEN_KEY = 'tadpoles.auth.token';

export type AuthInitializationState = 'loading' | 'authenticated' | 'unauthenticated' | 'recoverable-failure';

export interface AuthSession {
  token: string;
  tokenType: string;
  expiresIn: number;
  userId: number;
  email: string;
  displayName: string;
  clientId: number | null;
  onboardingComplete: boolean;
  roles: WorkspaceRole[];
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

interface CurrentUserResponse {
  userId: number;
  username: string;
  email: string;
  displayName: string;
  enabled: boolean;
  roles: string[];
  clientId: number | null;
}

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly platformId = inject(PLATFORM_ID);
  private readonly userService = inject(UserService);

  private readonly session = signal<AuthSession | null>(this.readStoredSession());
  private readonly initializationState = signal<AuthInitializationState>('loading');
  private initializationPromise: Promise<void> | null = null;

  readonly state = this.initializationState.asReadonly();
  readonly isAuthenticated = computed(() => this.initializationState() === 'authenticated' && this.session() !== null);
  readonly hasCompletedOnboarding = computed(() => this.session()?.onboardingComplete ?? false);
  readonly roles = computed<WorkspaceRole[]>(() => this.session()?.roles ?? []);
  readonly clientId = computed(() => this.session()?.clientId ?? null);

  constructor() {
    this.initializationPromise = this.bootstrap();
  }

  async ensureInitialized(): Promise<void> {
    if (!this.initializationPromise) {
      this.initializationPromise = this.bootstrap();
    }
    await this.initializationPromise;
  }

  async login(usernameOrEmail: string, password: string): Promise<void> {
    const response = await firstValueFrom(
      this.http.post<LoginResponse>(buildApiUrl('/auth/login', this.platformId), {
        username: this.normalizeLoginIdentifier(usernameOrEmail),
        password,
      }),
    );

    this.applySession({
      ...response,
      roles: [],
    });
    await this.bootstrapIdentity();
  }

  async signup(request: SignupRequest): Promise<void> {
    const response = await firstValueFrom(
      this.http.post<SignupResponse>(buildApiUrl('/auth/signup', this.platformId), {
        email: request.email.trim().toLowerCase(),
        password: request.password,
        displayName: request.displayName.trim(),
      }),
    );

    this.applySession({
      ...response,
      roles: ['CLIENT'],
    });
    this.userService.updateProfile({
      userId: response.userId,
      clientId: response.clientId,
      email: response.email,
      displayName: response.displayName,
      onboardingComplete: response.onboardingComplete,
    });
    this.initializationState.set('authenticated');
  }

  logout(): void {
    if (typeof localStorage !== 'undefined') {
      localStorage.removeItem(AUTH_STORAGE_KEY);
      localStorage.removeItem(AUTH_TOKEN_KEY);
    }

    this.session.set(null);
    this.initializationState.set('unauthenticated');
    this.userService.clearProfile();
  }

  getAccessToken(): string | null {
    return this.session()?.token ?? null;
  }

  hasAnyRole(requiredRoles: WorkspaceRole[]): boolean {
    const assignedRoles = this.roles();
    return requiredRoles.some((role) => assignedRoles.includes(role));
  }

  resolveLandingRoute(): string {
    return resolveWorkspaceLandingRoute(this.roles()) ?? '/unauthorized';
  }

  updateOnboardingStatus(onboardingComplete: boolean, clientId: number | null): void {
    const currentSession = this.session();
    if (!currentSession) {
      return;
    }

    const nextSession: AuthSession = {
      ...currentSession,
      onboardingComplete,
      clientId,
    };
    this.persistSession(nextSession);
  }

  private async bootstrap(): Promise<void> {
    if (!this.session()) {
      this.initializationState.set('unauthenticated');
      return;
    }

    try {
      await this.bootstrapIdentity();
    } catch {
      this.clearStoredSession();
      this.initializationState.set('recoverable-failure');
    }
  }

  private async bootstrapIdentity(): Promise<void> {
    const currentSession = this.session();
    if (!currentSession) {
      this.initializationState.set('unauthenticated');
      return;
    }

    const identity = await firstValueFrom(
      this.http.get<CurrentUserResponse>(buildApiUrl('/api/users/me', this.platformId)),
    );

    const roles = toWorkspaceRoles(identity.roles ?? []);
    if (!identity.enabled || roles.length === 0) {
      this.clearStoredSession();
      this.initializationState.set('unauthenticated');
      return;
    }

    const isClientUser = roles.includes('CLIENT');
    const nextSession: AuthSession = {
      ...currentSession,
      userId: identity.userId,
      email: identity.email,
      displayName: identity.displayName,
      clientId: identity.clientId,
      roles,
    };
    this.persistSession(nextSession);

    this.userService.updateProfile({
      userId: identity.userId,
      clientId: identity.clientId,
      email: identity.email,
      displayName: identity.displayName,
    });

    if (isClientUser) {
      const profile = await this.userService.loadCurrentProfile();
      this.updateOnboardingStatus(profile.onboardingComplete, profile.clientId);
    } else {
      this.userService.clearProfile();
      this.userService.updateProfile({
        userId: identity.userId,
        clientId: identity.clientId,
        email: identity.email,
        displayName: identity.displayName,
        onboardingComplete: true,
      });
      this.updateOnboardingStatus(true, identity.clientId);
    }

    this.initializationState.set('authenticated');
  }

  private applySession(response: LoginResponse & { roles?: WorkspaceRole[] }): void {
    const session: AuthSession = {
      token: response.token,
      tokenType: response.tokenType,
      expiresIn: response.expiresIn,
      userId: response.userId,
      email: response.email,
      displayName: response.displayName,
      clientId: response.clientId,
      onboardingComplete: response.onboardingComplete,
      roles: response.roles ?? [],
    };
    this.persistSession(session);
  }

  private persistSession(session: AuthSession): void {
    if (typeof localStorage !== 'undefined') {
      localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(session));
      localStorage.setItem(AUTH_TOKEN_KEY, session.token);
    }
    this.session.set(session);
  }

  private clearStoredSession(): void {
    if (typeof localStorage !== 'undefined') {
      localStorage.removeItem(AUTH_STORAGE_KEY);
      localStorage.removeItem(AUTH_TOKEN_KEY);
    }
    this.session.set(null);
    this.userService.clearProfile();
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
      const parsed = JSON.parse(rawSession) as Partial<AuthSession>;
      if (!parsed.token || !parsed.userId || !parsed.email || !parsed.displayName) {
        this.clearStoredSession();
        return null;
      }
      const roles = Array.isArray(parsed.roles) ? toWorkspaceRoles(parsed.roles.map(String)) : [];
      return {
        token: parsed.token,
        tokenType: parsed.tokenType ?? 'Bearer',
        expiresIn: parsed.expiresIn ?? 3600,
        userId: parsed.userId,
        email: parsed.email,
        displayName: parsed.displayName,
        clientId: parsed.clientId ?? null,
        onboardingComplete: parsed.onboardingComplete ?? false,
        roles,
      };
    } catch {
      this.clearStoredSession();
      return null;
    }
  }

  private normalizeLoginIdentifier(value: string): string {
    const trimmed = value.trim();
    return trimmed.includes('@') ? trimmed.toLowerCase() : trimmed;
  }
}

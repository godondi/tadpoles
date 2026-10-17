import { DestroyRef, Injectable, PLATFORM_ID, computed, inject, signal } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpBackend } from '@angular/common/http';
import { Subject, firstValueFrom, takeUntil, timeout } from 'rxjs';
import { UserService } from '../../user.service';
import { buildApiUrl } from '../api/api-url';
import { WorkspaceRole, landingPage, normalizeRoles, safeReturnUrl } from './workspace-policy';

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
  expiresAt?: number;
}
export interface SignupRequest {
  email: string;
  password: string;
  displayName: string;
}
export interface Identity {
  userId: number;
  username: string;
  email: string;
  displayName: string;
  enabled: boolean;
  clientId: number | null;
  roles: string[];
}
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly logoutHttp = new HttpClient(inject(HttpBackend));
  readonly logoutNotice = signal('');
  private readonly platformId = inject(PLATFORM_ID);
  private readonly userService = inject(UserService);
  private readonly session = signal<AuthSession | null>(this.readStoredSession());
  readonly identity = signal<Identity | null>(null);
  readonly status = signal<'loading' | 'authenticated' | 'unauthenticated' | 'error'>(
    'unauthenticated',
  );
  readonly roles = computed<WorkspaceRole[]>(() => normalizeRoles(this.identity()?.roles));
  readonly isAuthenticated = computed(() => this.status() === 'authenticated');
  readonly hasCompletedOnboarding = computed(() => this.session()?.onboardingComplete ?? false);
  readonly sessionChanged = new Subject<void>();
  private generation = 0;
  private initialization?: Promise<boolean>;
  private expiryTimer?: ReturnType<typeof setTimeout>;
  private profileLoaded = false;

  async login(identifier: string, password: string): Promise<void> {
    this.logout();
    const generation = this.generation;
    const username = identifier.trim();
    const response = await firstValueFrom(
      this.http
        .post<AuthSession>(buildApiUrl('/auth/login', this.platformId), {
          username: username.includes('@') ? username.toLowerCase() : username,
          password,
        })
        .pipe(takeUntil(this.sessionChanged)),
    );
    if (generation !== this.generation) throw new Error('Sign-in cancelled');
    this.applySession(response);
    if (!(await this.ensureInitialized()))
      throw new Error('Unable to verify your account. Please try again.');
  }

  async signup(request: SignupRequest): Promise<void> {
    this.logout();
    const generation = this.generation;
    const response = await firstValueFrom(
      this.http
        .post<AuthSession>(buildApiUrl('/auth/signup', this.platformId), {
          ...request,
          email: request.email.trim().toLowerCase(),
          displayName: request.displayName.trim(),
        })
        .pipe(takeUntil(this.sessionChanged)),
    );
    if (generation !== this.generation) throw new Error('Sign-up cancelled');
    this.applySession(response);
    if (!(await this.ensureInitialized()))
      throw new Error('Unable to verify your account. Please sign in again.');
  }

  async ensureInitialized(): Promise<boolean> {
    if (this.isAuthenticated()) return true;
    if (!this.session()) return false;
    if (this.initialization) return this.initialization;
    this.status.set('loading');
    const generation = this.generation;
    this.initialization = this.bootstrap(generation);
    try {
      return await this.initialization;
    } finally {
      if (generation === this.generation) this.initialization = undefined;
    }
  }

  private async bootstrap(generation: number): Promise<boolean> {
    try {
      const identity = await firstValueFrom(
        this.http
          .get<Identity>(buildApiUrl('/api/users/me', this.platformId), {
            headers: { Authorization: `Bearer ${this.getAccessToken()}` },
          })
          .pipe(takeUntil(this.sessionChanged)),
      );
      if (generation !== this.generation) return false;
      if (
        !identity.enabled ||
        identity.userId !== this.session()?.userId ||
        !Array.isArray(identity.roles)
      ) {
        this.logout(false);
        return false;
      }
      this.identity.set(identity);
      this.userService.updateProfile({
        userId: identity.userId,
        clientId: identity.clientId,
        email: identity.email,
        displayName: identity.displayName,
      });
      this.status.set('authenticated');
      return true;
    } catch (error) {
      if (generation !== this.generation) return false;
      this.identity.set(null);
      this.userService.clearProfile();
      if (error instanceof HttpErrorResponse && (error.status === 401 || error.status === 403))
        this.logout(false);
      else this.status.set('error');
      return false;
    }
  }

  async ensureClientProfile(): Promise<boolean> {
    if (!this.roles().includes('CLIENT')) return false;
    if (this.profileLoaded) return true;
    const generation = this.generation;
    try {
      const profile = await this.userService.loadCurrentProfile();
      if (generation !== this.generation) return false;
      this.updateOnboardingStatus(profile.onboardingComplete, profile.clientId);
      this.profileLoaded = true;
      return true;
    } catch {
      return false;
    }
  }

  home(): string {
    return landingPage(this.roles());
  }

  returnUrl(value: string | null): string {
    return safeReturnUrl(value, this.roles());
  }

  logout(revoke = true): void {
    const token = this.session()?.token;
    if (revoke && token) {
      this.logoutNotice.set('Signing out on the server…');
      this.logoutHttp
        .post(
          buildApiUrl('/api/auth/logout', this.platformId),
          {},
          { headers: { Authorization: `Bearer ${token}` } },
        )
        .pipe(timeout(5000))
        .subscribe({
          next: () => this.logoutNotice.set('Signed out.'),
          error: () =>
            this.logoutNotice.set(
              'Signed out on this device. Server revocation could not be confirmed; the session will expire automatically.',
            ),
        });
    }
    this.generation++;
    this.sessionChanged.next();
    clearTimeout(this.expiryTimer);
    this.initialization = undefined;
    this.profileLoaded = false;
    this.session.set(null);
    this.identity.set(null);
    this.status.set('unauthenticated');
    this.userService.clearProfile();
    if (typeof localStorage !== 'undefined') {
      localStorage.removeItem(AUTH_STORAGE_KEY);
      localStorage.removeItem(AUTH_TOKEN_KEY);
    }
  }

  getAccessToken(): string | null {
    return this.session()?.token ?? null;
  }

  updateOnboardingStatus(onboardingComplete: boolean, clientId: number | null): void {
    if (this.session()) this.applySession({ ...this.session()!, clientId, onboardingComplete });
  }

  private applySession(response: AuthSession): void {
    const session = {
      ...response,
      expiresAt: response.expiresAt ?? Date.now() + response.expiresIn * 1000,
    };
    this.session.set(session);
    if (typeof localStorage !== 'undefined') {
      localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(session));
      localStorage.setItem(AUTH_TOKEN_KEY, session.token);
    }
    clearTimeout(this.expiryTimer);
    this.expiryTimer = setTimeout(
      () => this.logout(false),
      Math.max(0, session.expiresAt - Date.now()),
    );
  }

  private readStoredSession(): AuthSession | null {
    if (typeof localStorage === 'undefined') return null;
    this.userService.clearProfile();
    try {
      const session = JSON.parse(localStorage.getItem(AUTH_STORAGE_KEY) ?? 'null');
      if (
        !session ||
        typeof session.token !== 'string' ||
        !Number.isInteger(session.userId) ||
        !Number.isFinite(session.expiresAt) ||
        session.expiresAt <= Date.now()
      )
        throw new Error('Expired session');
      return session;
    } catch {
      localStorage.removeItem(AUTH_STORAGE_KEY);
      localStorage.removeItem(AUTH_TOKEN_KEY);
      return null;
    }
  }

  constructor() {
    if (typeof window !== 'undefined') {
      const onStorage = (event: StorageEvent) => {
        if (
          event.key === AUTH_STORAGE_KEY &&
          this.session() &&
          event.newValue !== JSON.stringify(this.session())
        )
          this.logout(false);
      };
      window.addEventListener('storage', onStorage);
      inject(DestroyRef).onDestroy(() => window.removeEventListener('storage', onStorage));
    }
    if (this.session()) this.applySession(this.session()!);
  }
}

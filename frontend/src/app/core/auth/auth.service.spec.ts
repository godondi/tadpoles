import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AuthService } from './auth.service';
import { UserService } from '../../user.service';
const session = {
  token: 'test-token',
  tokenType: 'Bearer',
  expiresIn: 3600,
  userId: 24,
  email: 'alex@example.test',
  displayName: 'Alex',
  clientId: 7,
  onboardingComplete: true,
};
const identity = {
  userId: 24,
  username: 'Alex',
  email: 'alex@example.test',
  displayName: 'Alex',
  clientId: 7,
  enabled: true,
  roles: ['CLIENT'],
};
describe('AuthService authoritative initialization', () => {
  let http: HttpTestingController;
  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => {
    TestBed.inject(AuthService).logout(false);
    http.verify();
    localStorage.clear();
  });
  const request = (path: string) => http.expectOne((r) => r.url.endsWith(path));
  async function login(roles = ['CLIENT']) {
    const auth = TestBed.inject(AuthService);
    const pending = auth.login('Alex', 'short');
    const req = request('/auth/login');
    expect(req.request.body).toEqual({ username: 'Alex', password: 'short' });
    req.flush(session);
    await Promise.resolve();
    request('/api/users/me').flush({ ...identity, roles });
    await pending;
    return auth;
  }
  it('starts unauthenticated without a session', () =>
    expect(TestBed.inject(AuthService).isAuthenticated()).toBe(false));
  it('bootstraps staff identity without a client profile', async () => {
    const auth = await login(['TRADING_OPS']);
    expect(auth.roles()).toEqual(['TRADING_OPS']);
    expect(auth.home()).toBe('/operations/overview');
    http.expectNone((r) => r.url.endsWith('/profile'));
  });
  it('loads client profile only on entering client context', async () => {
    const auth = await login();
    const pending = auth.ensureClientProfile();
    request('/api/users/me/profile').flush({ ...identity, onboardingComplete: false });
    await pending;
    expect(auth.hasCompletedOnboarding()).toBe(false);
    expect(TestBed.inject(UserService).getProfileValue().email).toBe(identity.email);
  });
  it('preserves multiple roles and deterministic landing', async () => {
    const auth = await login(['FINANCE', 'CLIENT']);
    expect(auth.roles()).toEqual(['CLIENT', 'FINANCE']);
    expect(auth.home()).toBe('/app/overview');
  });
  it('unsupported roles receive no workspace', async () => {
    const auth = await login(['OPERATIONS']);
    expect(auth.home()).toBe('/unauthorized');
  });
  it('signup also validates authoritative identity', async () => {
    const auth = TestBed.inject(AuthService);
    const pending = auth.signup({
      email: 'ALEX@EXAMPLE.TEST',
      password: 'strong',
      displayName: 'Alex',
    });
    request('/auth/signup').flush({ ...session, clientId: null, onboardingComplete: false });
    await Promise.resolve();
    request('/api/users/me').flush({ ...identity, clientId: null });
    await pending;
    expect(auth.isAuthenticated()).toBe(true);
    expect(auth.hasCompletedOnboarding()).toBe(false);
  });
  it('network bootstrap failure fails closed and can retry', async () => {
    const auth = TestBed.inject(AuthService);
    const pending = auth.login('Alex', 'short').catch(() => null);
    request('/auth/login').flush(session);
    await Promise.resolve();
    request('/api/users/me').flush('offline', { status: 503, statusText: 'Unavailable' });
    await pending;
    expect(auth.isAuthenticated()).toBe(false);
    expect(auth.status()).toBe('error');
    const retry = auth.ensureInitialized();
    request('/api/users/me').flush(identity);
    expect(await retry).toBe(true);
  });
  it('logout cancels identity resolution and clears profile', async () => {
    const auth = TestBed.inject(AuthService);
    const pending = auth.login('Alex', 'short').catch(() => null);
    request('/auth/login').flush(session);
    await Promise.resolve();
    const req = request('/api/users/me');
    auth.logout(false);
    expect(req.cancelled).toBe(true);
    await pending;
    expect(auth.identity()).toBeNull();
    expect(localStorage.getItem('tadpoles.auth.token')).toBeNull();
  });
  it.each(['{broken', JSON.stringify(session), JSON.stringify({ ...session, expiresAt: 1 })])(
    'rejects malformed or old storage %s',
    (value) => {
      localStorage.setItem('tadpoles.auth.session', value);
      const auth = TestBed.inject(AuthService);
      expect(auth.isAuthenticated()).toBe(false);
      expect(auth.getAccessToken()).toBeNull();
    },
  );
  it('restored sessions render nothing until /me succeeds', async () => {
    localStorage.setItem(
      'tadpoles.auth.session',
      JSON.stringify({ ...session, expiresAt: Date.now() + 60000, roles: ['ADMIN'] }),
    );
    const auth = TestBed.inject(AuthService);
    expect(auth.isAuthenticated()).toBe(false);
    expect(auth.roles()).toEqual([]);
    const pending = auth.ensureInitialized();
    request('/api/users/me').flush({ ...identity, roles: ['FINANCE'] });
    expect(await pending).toBe(true);
    expect(auth.roles()).toEqual(['FINANCE']);
  });
  it('revokes the server session with the current token before clearing local state', async () => {
    const auth = await login();
    auth.logout();
    const req = request('/api/auth/logout');
    expect(req.request.headers.get('Authorization')).toBe('Bearer test-token');
    expect(auth.identity()).toBeNull();
    req.flush(null, { status: 204, statusText: 'No Content' });
    expect(auth.logoutNotice()).toBe('Signed out.');
  });
  it('clears local state when offline without claiming server revocation', async () => {
    const auth = await login();
    auth.logout();
    request('/api/auth/logout').error(new ProgressEvent('offline'));
    expect(auth.identity()).toBeNull();
    expect(auth.logoutNotice()).toContain('could not be confirmed');
  });
  it('clears the current identity on another tab logout', async () => {
    const auth = await login();
    window.dispatchEvent(
      new StorageEvent('storage', { key: 'tadpoles.auth.session', newValue: null }),
    );
    expect(auth.identity()).toBeNull();
    expect(auth.getAccessToken()).toBeNull();
  });
});

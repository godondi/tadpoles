import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AuthService } from './auth.service';
import { UserService } from '../../user.service';

describe('AuthService', () => {
  let httpTestingController: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
    localStorage.clear();
  });

  it('starts unauthenticated when no session exists', () => {
    const service = TestBed.inject(AuthService);

    expect(service.isAuthenticated()).toBe(false);
  });

  it('stores login state and updates the active profile email', async () => {
    const service = TestBed.inject(AuthService);
    const userService = TestBed.inject(UserService);

    const loginPromise = service.login('alex@example.com', 'StrongPassword123!');

    const loginRequest = httpTestingController.expectOne((request) => request.url.endsWith('/auth/login'));
    expect(loginRequest.request.method).toBe('POST');
    expect(loginRequest.request.body).toEqual({
      username: 'alex@example.com',
      password: 'StrongPassword123!',
    });
    loginRequest.flush({
      token: 'jwt-token',
      tokenType: 'Bearer',
      expiresIn: 3600,
      userId: 24,
      email: 'alex@example.com',
      displayName: 'Alex Example',
      clientId: 7,
      onboardingComplete: true,
    });
    await Promise.resolve();

    const profileRequest = httpTestingController.expectOne((request) => request.url.endsWith('/api/users/me/profile'));
    expect(profileRequest.request.headers.get('Authorization')).toBe('Bearer jwt-token');
    profileRequest.flush({
      userId: 24,
      clientId: 7,
      email: 'alex@example.com',
      displayName: 'Alex Example',
      clientName: 'Alex Example Household',
      phone: '+1-555-555-0101',
      dateOfBirth: '1990-01-01',
      addressLine1: '100 Main Street',
      addressLine2: '',
      city: 'New York',
      state: 'NY',
      postalCode: '10001',
      country: 'United States',
      employmentStatus: 'Employed',
      netWorth: 250000,
      riskTolerance: 'Moderate',
      investmentObjective: 'Long-term growth',
      preferredContactMethod: 'Email',
      paperlessStatements: true,
      marketingOptIn: false,
      onboardingComplete: true,
    });

    await loginPromise;

    expect(service.isAuthenticated()).toBe(true);
    expect(service.hasCompletedOnboarding()).toBe(true);
    expect(localStorage.getItem('tadpoles.auth.token')).toBe('jwt-token');
    expect(userService.getProfileValue().email).toBe('alex@example.com');
  });

  it('stores signup state for onboarding', async () => {
    const service = TestBed.inject(AuthService);

    const signupPromise = service.signup({
      email: 'alex@example.com',
      password: 'StrongPassword123!',
      displayName: 'Alex Example',
    });

    const signupRequest = httpTestingController.expectOne((request) => request.url.endsWith('/auth/signup'));
    expect(signupRequest.request.method).toBe('POST');
    signupRequest.flush({
      token: 'signup-token',
      tokenType: 'Bearer',
      expiresIn: 3600,
      userId: 24,
      email: 'alex@example.com',
      displayName: 'Alex Example',
      clientId: null,
      onboardingComplete: false,
      role: 'CLIENT',
    });

    await signupPromise;

    expect(service.isAuthenticated()).toBe(true);
    expect(service.hasCompletedOnboarding()).toBe(false);
    expect(localStorage.getItem('tadpoles.auth.token')).toBe('signup-token');
  });

  it('clears persisted session state on logout', async () => {
    const service = TestBed.inject(AuthService);

    const loginPromise = service.login('alex@example.com', 'StrongPassword123!');
    httpTestingController.expectOne((request) => request.url.endsWith('/auth/login')).flush({
      token: 'jwt-token',
      tokenType: 'Bearer',
      expiresIn: 3600,
      userId: 24,
      email: 'alex@example.com',
      displayName: 'Alex Example',
      clientId: null,
      onboardingComplete: false,
    });
    await Promise.resolve();
    httpTestingController.expectOne((request) => request.url.endsWith('/api/users/me/profile')).flush({
      userId: 24,
      clientId: null,
      email: 'alex@example.com',
      displayName: 'Alex Example',
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
    });
    await loginPromise;

    service.logout();

    expect(service.isAuthenticated()).toBe(false);
    expect(localStorage.getItem('tadpoles.auth.session')).toBeNull();
    expect(localStorage.getItem('tadpoles.auth.token')).toBeNull();
  });
});


import { TestBed } from '@angular/core/testing';
import { AuthService } from './auth.service';
import { UserService } from '../../user.service';

describe('AuthService', () => {
  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({});
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('starts unauthenticated when no session exists', () => {
    const service = TestBed.inject(AuthService);

    expect(service.isAuthenticated()).toBe(false);
  });

  it('stores login state and updates the active profile email', () => {
    const service = TestBed.inject(AuthService);
    const userService = TestBed.inject(UserService);

    service.login('alex@example.com');

    expect(service.isAuthenticated()).toBe(true);
    expect(localStorage.getItem('tadpoles.authenticated')).toBe('true');
    expect(localStorage.getItem('tadpoles.auth.email')).toBe('alex@example.com');
    expect(userService.getProfileValue().email).toBe('alex@example.com');
  });

  it('clears persisted session state on logout', () => {
    const service = TestBed.inject(AuthService);

    service.login('alex@example.com');
    service.logout();

    expect(service.isAuthenticated()).toBe(false);
    expect(localStorage.getItem('tadpoles.authenticated')).toBeNull();
    expect(localStorage.getItem('tadpoles.auth.email')).toBeNull();
  });
});


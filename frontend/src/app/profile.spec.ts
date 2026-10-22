import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { Subject } from 'rxjs';
import { AuthService } from './core/auth/auth.service';
import { UserProfile, UserService } from './user.service';
import { ProfileComponent } from './profile';

const profile: UserProfile = {
  userId: 7,
  clientId: 7,
  email: 'client@example.test',
  displayName: 'Demo Client',
  clientName: 'Demo Client',
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
  onboardingComplete: true,
};

describe('Profile advisor assignment', () => {
  function setup() {
    TestBed.configureTestingModule({
      imports: [ProfileComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: { sessionChanged: new Subject<void>() } },
        {
          provide: UserService,
          useValue: {
            getProfile: () => signal(profile).asReadonly(),
            loadCurrentProfile: vi.fn().mockResolvedValue(profile),
          },
        },
      ],
    });
    const fixture = TestBed.createComponent(ProfileComponent);
    fixture.detectChanges();
    const http = TestBed.inject(HttpTestingController);
    const request = http.expectOne('/api/workspace/client/advisor');
    return { fixture, http, request };
  }

  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('shows an assigned advisor independently of cached profile data', () => {
    const { fixture, request } = setup();
    request.flush({
      assignmentStatus: 'ASSIGNED',
      advisor: { advisorId: 4, name: 'Demo Advisor' },
      assignedAt: '2026-10-22T18:00:00Z',
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Demo Advisor');
    expect(fixture.nativeElement.textContent).toContain('Assigned advisor');
  });

  it('distinguishes pending assignment from unavailable assignment', () => {
    const { fixture, request } = setup();
    request.flush({ assignmentStatus: 'PENDING', advisor: null, assignedAt: null });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Assignment pending');

    fixture.componentInstance.loadAssignment();
    const retry = TestBed.inject(HttpTestingController).expectOne('/api/workspace/client/advisor');
    retry.flush({}, { status: 503, statusText: 'Unavailable' });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Assignment unavailable');
  });
});

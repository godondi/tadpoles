import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { TopNavComponent } from './top-nav.component';

describe('TopNavComponent', () => {
  beforeEach(async () => {
    localStorage.clear();
    localStorage.setItem('tadpoles.auth.session', JSON.stringify({
      token: 'stored-token',
      tokenType: 'Bearer',
      expiresIn: 3600,
      userId: 24,
      email: 'nav.tester@example.com',
      displayName: 'Nav Tester',
      clientId: 7,
      onboardingComplete: true,
    }));
    localStorage.setItem('tadpoles.auth.token', 'stored-token');
    localStorage.setItem('tadpoles.user.profile', JSON.stringify({
      userId: 24,
      clientId: 7,
      email: 'nav.tester@example.com',
      displayName: 'Nav Tester',
      clientName: 'Nav Tester Household',
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
    }));

    await TestBed.configureTestingModule({
      imports: [TopNavComponent],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('renders the Tadpoles brand, search stub, and account button', () => {
    const fixture = TestBed.createComponent(TopNavComponent);
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;

    expect(host.textContent).toContain('Tadpoles');
    expect(host.querySelector('.search-input')?.getAttribute('placeholder')).toContain('Search');
    expect(host.querySelector('.search-button')?.textContent).toContain('Search');
    expect(host.querySelector('.profile-btn')?.textContent).toContain('Account');
    expect(host.textContent).not.toContain('Trades');
  });

  it('opens and closes the account menu', () => {
    const fixture = TestBed.createComponent(TopNavComponent);
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    const menuToggle = host.querySelector('.profile-btn') as HTMLButtonElement;

    menuToggle.click();
    fixture.detectChanges();

    expect(host.querySelector('.profile-menu')).toBeTruthy();

    document.body.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    fixture.detectChanges();

    expect(host.querySelector('.profile-menu')).toBeNull();
  });
});



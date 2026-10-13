import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { TopNavComponent } from './top-nav.component';

@Component({ template: '' })
class DummyComponent {}

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
      roles: ['CLIENT'],
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
      providers: [
        provideRouter([
          { path: 'app/overview', component: DummyComponent },
          { path: 'trade/:symbol', component: DummyComponent },
          { path: 'profile', component: DummyComponent },
          { path: 'onboarding', component: DummyComponent },
          { path: 'login', component: DummyComponent },
          { path: 'unauthorized', component: DummyComponent },
        ]),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    }).compileComponents();
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('renders the Tadpoles brand, ticker search, and account button', () => {
    const fixture = TestBed.createComponent(TopNavComponent);
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.textContent).toContain('Tadpoles');
    expect(host.querySelector('.search-input')?.getAttribute('placeholder')).toContain('Search');
    expect(host.querySelector('.search-button')?.textContent).toContain('Search');
    expect(host.querySelector('.profile-btn')?.textContent).toContain('Account');
  });

  it.each([' aapl ', 'brk.b', 'BRK-B'])('submits ticker %s through the search form', (query) => {
    const fixture = TestBed.createComponent(TopNavComponent);
    fixture.detectChanges();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const host = fixture.nativeElement as HTMLElement;
    const input = host.querySelector('input') as HTMLInputElement;
    input.value = query;
    input.dispatchEvent(new Event('input'));
    const event = new Event('submit', { cancelable: true });
    (host.querySelector('form') as HTMLFormElement).dispatchEvent(event);
    expect(event.defaultPrevented).toBe(true);
    expect(navigate).toHaveBeenCalledWith(['/trade', query.trim().toUpperCase()]);
  });

  it('ignores empty and whitespace-only searches', () => {
    const fixture = TestBed.createComponent(TopNavComponent);
    fixture.detectChanges();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    for (const query of ['', '   ']) {
      fixture.componentInstance.updateSearchQuery(query);
      (fixture.nativeElement.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    }
    expect(navigate).not.toHaveBeenCalled();
  });

  it('navigates to the default workspace when the brand is clicked', () => {
    const fixture = TestBed.createComponent(TopNavComponent);
    fixture.detectChanges();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    (fixture.nativeElement.querySelector('.brand') as HTMLButtonElement).click();
    expect(navigate).toHaveBeenCalledWith('/app/overview');
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

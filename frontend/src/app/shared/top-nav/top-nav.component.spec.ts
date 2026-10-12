import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
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

  it('renders the Tadpoles brand, ticker search, and account button', () => {
    const fixture = TestBed.createComponent(TopNavComponent);
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;

    expect(host.textContent).toContain('Tadpoles');
    expect(host.querySelector('.search-input')?.getAttribute('placeholder')).toContain('Search');
    expect(host.querySelector('.search-button')?.textContent).toContain('Search');
    expect(host.querySelector('.profile-btn')?.textContent).toContain('Account');
    expect(host.textContent).not.toContain('Trades');
  });

  it.each([' aapl ', 'brk.b', 'BRK-B'])('submits ticker %s through the search form', (query) => {
    const fixture = TestBed.createComponent(TopNavComponent);
    fixture.detectChanges();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const host = fixture.nativeElement as HTMLElement;
    const input = host.querySelector('input')!;
    input.value = query;
    input.dispatchEvent(new Event('input'));
    const event = new Event('submit', { cancelable: true });
    host.querySelector('form')!.dispatchEvent(event);
    expect(event.defaultPrevented).toBe(true);
    expect(navigate).toHaveBeenCalledWith(['/trade', query.trim().toUpperCase()]);
  });

  it('ignores empty and whitespace-only searches', () => {
    const fixture = TestBed.createComponent(TopNavComponent);
    fixture.detectChanges();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    for (const query of ['', '   ']) {
      fixture.componentInstance.updateSearchQuery(query);
      fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));
    }
    expect(navigate).not.toHaveBeenCalled();
  });

  it('returns to the dashboard when the brand is clicked away from it', () => {
    const fixture = TestBed.createComponent(TopNavComponent);
    fixture.detectChanges();
    const router = TestBed.inject(Router);
    vi.spyOn(router, 'url', 'get').mockReturnValue('/trade/AAPL');
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    fixture.nativeElement.querySelector('.brand').click();
    expect(navigate).toHaveBeenCalledWith(['/']);
  });

  it.each(['/', '/?view=holdings', '/#holdings'])('does nothing when the brand is clicked on dashboard %s', (url) => {
    const fixture = TestBed.createComponent(TopNavComponent);
    fixture.detectChanges();
    const router = TestBed.inject(Router);
    vi.spyOn(router, 'url', 'get').mockReturnValue(url);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    fixture.nativeElement.querySelector('.brand').click();
    expect(navigate).not.toHaveBeenCalled();
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


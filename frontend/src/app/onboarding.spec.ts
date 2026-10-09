import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { OnboardingComponent } from './onboarding';
import { AuthService } from './core/auth/auth.service';
import { UserService } from './user.service';

const choices = {
  riskTolerance: ['Conservative', 'Moderate', 'Aggressive'],
  employmentStatus: ['Unemployed', 'Intern', 'Student', 'Employed (Part-Time)', 'Employed (Full-Time)', 'Retired'],
  investmentObjective: ['Preservation', 'Income', 'Growth', 'Speculation'],
  preferredContactMethod: ['Email', 'Phone'],
  netWorth: ['$0 - $24,999', '$25,000 - $49,999', '$50,000 - $99,999', '$100,000 - $249,999', '$250,000 - $499,999', '$500,000 - $999,999', '$1,000,000+'],
};

describe('Onboarding dropdowns', () => {
  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      imports: [OnboardingComponent],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
  });
  afterEach(() => localStorage.clear());

  async function setup(netWorth: number | null = null, riskTolerance = '') {
    const users = TestBed.inject(UserService);
    const profile = { ...users.getProfileValue(), netWorth, riskTolerance, onboardingComplete: false };
    vi.spyOn(users, 'loadCurrentProfile').mockResolvedValue(profile);
    const fixture = TestBed.createComponent(OnboardingComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return { fixture, users, profile };
  }

  for (const [field, options] of Object.entries(choices)) {
    it(`renders the required ${field} dropdown with exactly the requested choices`, async () => {
      const { fixture } = await setup();
      const select = fixture.nativeElement.querySelector(`select[name="${field}"]`) as HTMLSelectElement;
      expect(select).toBeTruthy();
      expect(select.required).toBe(true);
      expect(select.value).toBe('');
      expect(select.checkValidity()).toBe(false);
      expect(Array.from(select.options).slice(1).map(o => o.textContent?.trim())).toEqual(options);
    });
  }

  it.each([0, 25000, 50000, 100000, 250000, 500000, 1000000])('saves the selected net worth range %s as a number', async (value) => {
    const { fixture } = await setup();
    const select = fixture.nativeElement.querySelector('select[name="netWorth"]') as HTMLSelectElement;
    expect(select).toBeTruthy();
    select.value = String(value);
    select.dispatchEvent(new Event('change'));
    expect(fixture.componentInstance.form().netWorth).toBe(value);
    select.value = '';
    select.dispatchEvent(new Event('change'));
    expect(fixture.componentInstance.form().netWorth).toBeNull();
  });

  it.each([[24999, '0'], [25000, '25000'], [99999, '50000'], [249999, '100000'], [999999, '500000'], [2000000, '1000000']])('shows saved amount %s in range %s without changing it', async (amount, range) => {
    const { fixture } = await setup(Number(amount));
    expect(fixture.nativeElement.querySelector('select[name="netWorth"]')?.value).toBe(range);
    expect(fixture.componentInstance.form().netWorth).toBe(amount);
  });

  it('submits dropdown values through the existing onboarding service and returns to dashboard', async () => {
    const { fixture, users, profile } = await setup();
    fixture.componentInstance.form.update(current => ({ ...current,
      clientName: ' Client ', phone: '1234567890', dateOfBirth: '1990-01-01',
      addressLine1: '1 Main St', city: 'New York', state: 'NY', postalCode: '10001',
      country: 'US', riskTolerance: 'Moderate',
    }));
    for (const [field, value] of Object.entries({ employmentStatus: 'Student', investmentObjective: 'Growth', preferredContactMethod: 'Email', netWorth: '25000' })) {
      const select = fixture.nativeElement.querySelector(`select[name="${field}"]`) as HTMLSelectElement;
      expect(select).toBeTruthy();
      select.value = value;
      select.dispatchEvent(new Event('change'));
    }
    const save = vi.spyOn(users, 'completeOnboarding').mockResolvedValue({ ...profile, clientId: 7, onboardingComplete: true });
    const status = vi.spyOn(TestBed.inject(AuthService), 'updateOnboardingStatus');
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    await fixture.componentInstance.submit();
    expect(save).toHaveBeenCalledWith(expect.objectContaining({clientName: 'Client', employmentStatus: 'Student', investmentObjective: 'Growth', preferredContactMethod: 'Email', netWorth: 25000, riskTolerance: 'Moderate'}));
    expect(status).toHaveBeenCalledWith(true, 7);
    expect(navigate).toHaveBeenCalledWith('/');
  });

  it('rejects unsupported dropdown values even on direct submission', async () => {
    const { fixture, users } = await setup();
    fixture.componentInstance.form.update(current => ({...current, clientName: 'Client', addressLine1: '1 Main', city: 'NY', country: 'US', dateOfBirth: '1990-01-01', netWorth: 0, employmentStatus: 'Other', investmentObjective: 'Growth', preferredContactMethod: 'Email'}));
    const save = vi.spyOn(users, 'completeOnboarding');
    await fixture.componentInstance.submit();
    expect(save).not.toHaveBeenCalled();
    expect(fixture.componentInstance.errorMessage()).toContain('select');
  });

  it('sends the numeric range and contact choice in the real HTTP onboarding request', async () => {
    const { fixture, profile } = await setup();
    fixture.componentInstance.form.update(current => ({ ...current,
      clientName: 'Client', phone: '1234567890', dateOfBirth: '1990-01-01',
      addressLine1: '1 Main St', city: 'New York', state: 'NY', postalCode: '10001',
      country: 'US', riskTolerance: 'Moderate', employmentStatus: 'Retired',
      investmentObjective: 'Income', preferredContactMethod: 'Phone', netWorth: 1000000,
    }));
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const saving = fixture.componentInstance.submit();
    const http = TestBed.inject(HttpTestingController);
    const request = http.expectOne(r => r.url.endsWith('/api/users/me/onboarding'));
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual(fixture.componentInstance.form());
    expect(typeof request.request.body.netWorth).toBe('number');
    request.flush({ ...profile, ...request.request.body, clientId: 7, onboardingComplete: true });
    await saving;
    expect(navigate).toHaveBeenCalledWith('/');
    expect(fixture.componentInstance.isSubmitting()).toBe(false);
    http.verify();
  });

  it('keeps dropdown selections and allows a retry after an HTTP save failure', async () => {
    const { fixture } = await setup();
    fixture.componentInstance.form.update(current => ({ ...current,
      clientName: 'Client', addressLine1: '1 Main', city: 'NY', country: 'US',
      dateOfBirth: '1990-01-01', netWorth: 0, employmentStatus: 'Unemployed',
      investmentObjective: 'Preservation', preferredContactMethod: 'Email', riskTolerance: 'Conservative',
    }));
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const saving = fixture.componentInstance.submit();
    const http = TestBed.inject(HttpTestingController);
    http.expectOne(r => r.url.endsWith('/api/users/me/onboarding')).flush(
      { message: 'Unable to save profile' }, { status: 500, statusText: 'Server Error' },
    );
    await saving;
    expect(fixture.componentInstance.errorMessage()).toBe('Unable to save profile');
    expect(fixture.componentInstance.isSubmitting()).toBe(false);
    expect(fixture.componentInstance.form().netWorth).toBe(0);
    expect(fixture.componentInstance.form().preferredContactMethod).toBe('Email');
    expect(navigate).not.toHaveBeenCalled();
    http.verify();
  });

  it.each(['Conservative', 'Moderate', 'Aggressive'])('submits selected risk tolerance %s through HTTP', async (risk) => {
    const { fixture, profile } = await setup();
    fixture.componentInstance.form.update(current => ({ ...current,
      clientName: 'Client', phone: '1234567890', dateOfBirth: '1990-01-01',
      addressLine1: '1 Main St', city: 'New York', state: 'NY', postalCode: '10001',
      country: 'US', employmentStatus: 'Student', investmentObjective: 'Growth',
      preferredContactMethod: 'Email', netWorth: 25000,
    }));
    const select = fixture.nativeElement.querySelector('select[name="riskTolerance"]') as HTMLSelectElement;
    expect(select).toBeTruthy();
    select.value = risk;
    select.dispatchEvent(new Event('change'));
    expect(fixture.componentInstance.form().riskTolerance).toBe(risk);
    vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const saving = fixture.componentInstance.submit();
    const http = TestBed.inject(HttpTestingController);
    const request = http.expectOne(r => r.url.endsWith('/api/users/me/onboarding'));
    expect(request.request.body.riskTolerance).toBe(risk);
    request.flush({ ...profile, ...request.request.body, clientId: 7, onboardingComplete: true });
    await saving;
    http.verify();
  });

  it.each(['', 'Extreme'])('rejects unsupported risk tolerance "%s" before saving', async (riskTolerance) => {
    const { fixture, users } = await setup();
    fixture.componentInstance.form.update(current => ({ ...current,
      clientName: 'Client', dateOfBirth: '1990-01-01', addressLine1: '1 Main',
      city: 'NY', country: 'US', netWorth: 0, employmentStatus: 'Student',
      investmentObjective: 'Growth', preferredContactMethod: 'Email', riskTolerance,
    }));
    const save = vi.spyOn(users, 'completeOnboarding').mockResolvedValue(users.getProfileValue());
    vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    await fixture.componentInstance.submit();
    expect(save).not.toHaveBeenCalled();
    expect(fixture.componentInstance.errorMessage()).toContain('risk tolerance');
  });

  it('restores an existing risk tolerance selection with accessible selection guidance', async () => {
    const { fixture } = await setup(null, 'Moderate');
    const select = fixture.nativeElement.querySelector('select[name="riskTolerance"]') as HTMLSelectElement;
    expect(select).toBeTruthy();
    expect(select.value).toBe('Moderate');
    const description = select.getAttribute('aria-describedby');
    expect(description).toBeTruthy();
    expect(fixture.nativeElement.querySelector(`#${description}`)?.textContent).toContain('willing and able');
  });

});

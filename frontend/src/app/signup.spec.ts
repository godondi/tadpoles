import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { SignupComponent } from './signup';
import { AuthService } from './core/auth/auth.service';

describe('Signup form UI', () => {
  let auth: { signup: ReturnType<typeof vi.fn> };
  beforeEach(() => {
    auth = { signup: vi.fn().mockResolvedValue({}) };
    TestBed.configureTestingModule({
      imports: [SignupComponent],
      providers: [provideRouter([]), { provide: AuthService, useValue: auth }],
    });
  });

  function enter(
    fixture: ReturnType<typeof TestBed.createComponent>,
    selector: string,
    value: string,
  ) {
    const input = fixture.nativeElement.querySelector(selector) as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  it('shows missing-field and password validation next to the form action', async () => {
    const fixture = TestBed.createComponent(SignupComponent);
    fixture.detectChanges();
    fixture.nativeElement.querySelector('button[type="submit"]').click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain(
      'required fields',
    );
    expect(fixture.nativeElement.querySelector('form').lastElementChild?.tagName).toBe('BUTTON');
    expect(auth.signup).not.toHaveBeenCalled();
    enter(fixture, 'input[type="email"]', 'client@example.test');
    enter(fixture, 'input[type="text"]', 'Client42');
    enter(fixture, 'input[type="password"]', 'WeakPass');
    fixture.nativeElement.querySelectorAll('input[type="password"]')[1].value = 'WeakPass';
    fixture.nativeElement
      .querySelectorAll('input[type="password"]')[1]
      .dispatchEvent(new Event('input'));
    fixture.nativeElement.querySelector('button[type="submit"]').click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain(
      'special character',
    );
    expect(auth.signup).not.toHaveBeenCalled();
  });

  it('submits normalized email and navigates to onboarding for valid credentials', async () => {
    const fixture = TestBed.createComponent(SignupComponent);
    fixture.detectChanges();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    enter(fixture, 'input[type="email"]', ' CLIENT@EXAMPLE.TEST ');
    enter(fixture, 'input[type="text"]', 'Client Name');
    const passwords = fixture.nativeElement.querySelectorAll('input[type="password"]');
    for (const input of passwords) {
      input.value = 'StrongPass1!';
      input.dispatchEvent(new Event('input'));
    }
    fixture.nativeElement.querySelector('button[type="submit"]').click();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(auth.signup).toHaveBeenCalledWith({
      email: 'client@example.test',
      password: 'StrongPass1!',
      displayName: 'ClientName',
    });
    expect(navigate).toHaveBeenCalledWith('/onboarding');
  });

  it('rejects a display name with spaces or special characters before signup', async () => {
    const fixture = TestBed.createComponent(SignupComponent);
    fixture.detectChanges();
    enter(fixture, 'input[type="email"]', 'client@example.test');
    enter(fixture, 'input[type="text"]', 'Client Name!');
    for (const input of fixture.nativeElement.querySelectorAll('input[type="password"]')) {
      input.value = 'StrongPass1!';
      input.dispatchEvent(new Event('input'));
    }
    fixture.componentInstance.form.update((form) => ({ ...form, displayName: 'Client Name!' }));
    await fixture.componentInstance.handleSignup();
    expect(fixture.componentInstance.errorMessage()).toContain('letters and numbers');
    expect(auth.signup).not.toHaveBeenCalled();
  });

  it('strips spaces and special characters as the display name is typed', () => {
    const fixture = TestBed.createComponent(SignupComponent);
    fixture.detectChanges();
    enter(fixture, 'input[type="text"]', 'Cl!ent Name-42');
    expect(fixture.componentInstance.form().displayName).toBe('ClentName42');
  });
});

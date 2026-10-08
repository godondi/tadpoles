import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TopNavComponent } from './top-nav.component';
import { AuthService } from '../../core/auth/auth.service';

describe('TopNavComponent', () => {
  beforeEach(async () => {
    localStorage.clear();

    await TestBed.configureTestingModule({
      imports: [TopNavComponent],
      providers: [provideRouter([])],
    }).compileComponents();

    TestBed.inject(AuthService).login('nav.tester@example.com');
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



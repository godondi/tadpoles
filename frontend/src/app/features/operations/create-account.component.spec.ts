import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { OperationsCreateAccountComponent } from './create-account.component';
import { OperationsUsersApi } from '../../core/api/operations-users-api.service';

describe('Operations create account UI', () => {
  let api: { createStaff: ReturnType<typeof vi.fn> };
  beforeEach(() => {
    api = { createStaff: vi.fn().mockReturnValue(of({})) };
    TestBed.configureTestingModule({
      imports: [OperationsCreateAccountComponent],
      providers: [{ provide: OperationsUsersApi, useValue: api }],
    });
  });
  it('creates the selected supported staff role and resets the form', async () => {
    const fixture = TestBed.createComponent(OperationsCreateAccountComponent);
    fixture.componentInstance.staff = {
      username: ' analyst ',
      displayName: ' Analyst One ',
      email: 'A@EXAMPLE.TEST',
      password: 'SecurePass1!',
      roleType: 'ANALYST',
      advisorName: '',
    };
    fixture.componentInstance.createAccount();
    expect(api.createStaff).toHaveBeenCalledWith(
      expect.objectContaining({
        username: 'analyst',
        displayName: 'Analyst One',
        email: 'a@example.test',
        enabled: true,
        roleType: 'ANALYST',
      }),
    );
    expect(fixture.componentInstance.message()).toContain('ANALYST account created');
    expect(fixture.componentInstance.staff.username).toBe('');
  });
  it.each(['ANALYST', 'ADVISOR', 'COMPLIANCE', 'FINANCE'])(
    'offers and submits the %s role',
    async (roleType) => {
      const fixture = TestBed.createComponent(OperationsCreateAccountComponent);
      fixture.componentInstance.staff = {
        username: 'role.user',
        displayName: 'Role User',
        email: 'role@example.test',
        password: 'SecurePass1!',
        roleType,
        advisorName: roleType === 'ADVISOR' ? 'Advisor Name' : '',
      };
      fixture.componentInstance.createAccount();
      expect(api.createStaff).toHaveBeenCalledWith(
        expect.objectContaining({ roleType, enabled: true }),
      );
    },
  );
  it('requires valid email, strong password, and advisor name without calling the API', () => {
    const fixture = TestBed.createComponent(OperationsCreateAccountComponent);
    const component = fixture.componentInstance;
    component.staff = {
      username: 'user1',
      displayName: 'User One',
      email: 'bad',
      password: 'weak',
      roleType: 'ADVISOR',
      advisorName: '',
    };
    component.createAccount();
    expect(component.error()).toContain('valid email');
    component.staff.email = 'u@example.test';
    component.createAccount();
    expect(component.error()).toContain('8 characters');
    component.staff.password = 'SecurePass1!';
    component.createAccount();
    expect(component.error()).toContain('Advisor name');
    expect(api.createStaff).not.toHaveBeenCalled();
  });
  it('surfaces duplicate or backend account creation errors', () => {
    const fixture = TestBed.createComponent(OperationsCreateAccountComponent);
    fixture.componentInstance.staff = {
      username: 'user1',
      displayName: 'User One',
      email: 'u@example.test',
      password: 'SecurePass1!',
      roleType: 'FINANCE',
      advisorName: '',
    };
    api.createStaff.mockReturnValueOnce(
      throwError(() => ({ error: { message: 'Email already exists' } })),
    );
    fixture.componentInstance.createAccount();
    expect(fixture.componentInstance.error()).toBe('Email already exists');
  });
});

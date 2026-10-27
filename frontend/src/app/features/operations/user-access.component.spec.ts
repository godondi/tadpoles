import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { OperationsUserAccessComponent } from './user-access.component';
import { OperationsUsersApi, OperationsUser } from '../../core/api/operations-users-api.service';

describe('Operations user access UI', () => {
  const records: OperationsUser[] = [
    {
      id: 3,
      username: 'alice',
      name: 'Alice Example',
      email: 'alice@example.test',
      client_id: 30,
      balance: '120.00',
      revoked: false,
      revoked_until: null,
      roles: 'CLIENT',
    },
    {
      id: 4,
      username: 'bob',
      name: 'Bob Example',
      email: 'bob@example.test',
      client_id: 40,
      balance: '50.00',
      revoked: true,
      revoked_until: null,
      roles: 'CLIENT',
    },
    {
      id: 5,
      username: 'carol',
      name: 'Carol Pending',
      email: 'carol@example.test',
      client_id: null,
      balance: 0,
      revoked: false,
      revoked_until: null,
      roles: 'CLIENT',
    },
  ];
  let api: {
    clients: ReturnType<typeof vi.fn>;
    changeAccess: ReturnType<typeof vi.fn>;
    createStaff: ReturnType<typeof vi.fn>;
  };
  beforeEach(() => {
    api = {
      clients: vi.fn().mockReturnValue(of(records)),
      changeAccess: vi.fn().mockReturnValue(of({})),
      createStaff: vi.fn(),
    };
    TestBed.configureTestingModule({
      imports: [OperationsUserAccessComponent],
      providers: [{ provide: OperationsUsersApi, useValue: api }],
    });
  });

  async function render() {
    const fixture = TestBed.createComponent(OperationsUserAccessComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }

  it('shows alphabetical client rows with balance and onboarding state, without account tabs', async () => {
    const fixture = await render();
    expect(
      [...fixture.nativeElement.querySelectorAll('tbody th')].map((e: HTMLElement) =>
        e.textContent.trim(),
      ),
    ).toEqual(['alice', 'bob', 'carol']);
    expect(fixture.nativeElement.textContent).toContain('$120.00');
    expect(fixture.nativeElement.textContent).toContain('Onboarding');
    expect(fixture.nativeElement.querySelector('[role="tablist"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('th:last-child')?.textContent.trim()).toBe('Access');
    expect(fixture.nativeElement.querySelectorAll('tbody button').length).toBe(0);
  });

  it('places bulk revoke controls before the account list and keeps the reason field aligned', async () => {
    const fixture = await render();
    fixture.componentInstance.selected.set(new Set([30]));
    fixture.detectChanges();
    const controls = fixture.nativeElement.querySelector('.bulk-controls');
    const table = fixture.nativeElement.querySelector('.table-wrapper');
    expect(controls.compareDocumentPosition(table) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(fixture.nativeElement.querySelector('.reason-field input')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('.reason-field')?.textContent).toContain(
      'Reason (optional)',
    );
  });

  it('searches through the API and filters revoked rows', async () => {
    const fixture = await render();
    fixture.componentInstance.search = 'bob';
    fixture.componentInstance.filter.set('REVOKED');
    fixture.componentInstance.load();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(api.clients).toHaveBeenLastCalledWith('bob');
    expect(
      [...fixture.nativeElement.querySelectorAll('tbody th')].map((e: HTMLElement) =>
        e.textContent.trim(),
      ),
    ).toEqual(['bob']);
    expect(fixture.nativeElement.textContent).toContain('Revoked indefinitely');
  });

  it('displays load errors and retries so users are not left with a silent empty table', async () => {
    api.clients
      .mockReturnValueOnce(throwError(() => ({ error: { message: 'connection refused' } })))
      .mockReturnValueOnce(of(records));
    const fixture = await render();
    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain(
      'connection refused',
    );
    fixture.nativeElement.querySelector('[role="alert"] button').click();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.componentInstance.error()).toBe('');
    expect(fixture.nativeElement.querySelectorAll('tbody tr').length).toBe(3);
  });

  it('explains the incomplete-onboarding parse error without offering a retry', async () => {
    api.clients.mockReturnValueOnce(
      throwError(() => ({ status: 500, error: { message: 'For input string: "undefined"' } })),
    );
    const fixture = await render();
    const alert = fixture.nativeElement.querySelector('[role="alert"]');
    expect(alert?.textContent).toContain('has not completed onboarding');
    expect(alert?.textContent).not.toContain('For input string');
    expect(alert?.querySelector('button')).toBeNull();
  });

  it('shows the same friendly message when an access change returns the incomplete-onboarding error', async () => {
    api.changeAccess.mockReturnValueOnce(
      throwError(() => ({ status: 500, error: { message: 'For input string: "undefined"' } })),
    );
    const fixture = await render();
    fixture.componentInstance.selected.set(new Set([30]));
    fixture.componentInstance.revokeSelected();
    fixture.detectChanges();
    const alert = fixture.nativeElement.querySelector('[role="alert"]');
    expect(alert?.textContent).toContain('has not completed onboarding');
    expect(alert?.textContent).not.toContain('For input string');
    expect(alert?.querySelector('button')).toBeNull();
  });

  it('supports indefinite multi-select revoke then manual restore', async () => {
    const fixture = await render();
    fixture.componentInstance.selected.set(new Set([30, 40]));
    fixture.componentInstance.revokeSelected();
    expect(api.changeAccess).toHaveBeenNthCalledWith(1, 30, { revoke: true });
    expect(api.changeAccess).toHaveBeenNthCalledWith(2, 40, { revoke: true });
    fixture.componentInstance.selected.set(new Set([40]));
    fixture.componentInstance.restoreSelected();
    expect(api.changeAccess).toHaveBeenNthCalledWith(3, 40, { revoke: false });
  });

  it.each([
    ['HOURS', 3],
    ['DAYS', 2],
    ['MONTHS', 1],
  ])('sends timed %s revocation with an end duration', async (unit, amount) => {
    const fixture = await render();
    fixture.componentInstance.selected.set(new Set([30]));
    fixture.componentInstance.duration = 'TIMED';
    fixture.componentInstance.amount = amount as number;
    fixture.componentInstance.unit = unit as 'HOURS' | 'DAYS' | 'MONTHS';
    fixture.componentInstance.revokeSelected();
    expect(api.changeAccess).toHaveBeenCalledWith(30, { revoke: true, amount, unit });
  });

  it('shows timed end dates and disables selecting clients without an active client profile', async () => {
    const timed = { ...records[1], revoked_until: '2027-01-02T03:04:00Z' };
    api.clients.mockReturnValue(of([records[0], timed, records[2]]));
    const fixture = await render();
    expect(fixture.componentInstance.accessLabel(timed)).toContain('Revoked until');
    expect(fixture.nativeElement.querySelector('input[aria-label="Select carol"]').disabled).toBe(
      true,
    );
  });
});

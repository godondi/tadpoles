import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Subject } from 'rxjs';
import { SupportWorkbenchComponent } from './support-workbench.component';
import { AuthService } from '../../core/auth/auth.service';
import { Workbench } from '../../core/api/case-workflow-api.service';
const reference = 'CASE-11111111-1111-4111-8111-111111111111';
const workbench: Workbench = {
  reference,
  publicVersion: 1,
  workflowVersion: 1,
  checklistRevision: 1,
  status: 'IN_REVIEW',
  originalCategory: 'OTHER',
  effectiveCategory: 'OTHER',
  assignee: null,
  priority: 'NORMAL',
  nextAction: null,
  dueAt: null,
  responseTarget: 'Response target not configured',
  intake: { reason: 'GENERAL' },
  tasks: [],
  definitions: { resolutions: ['GENERAL_GUIDANCE_PROVIDED'] },
  actions: {
    reply: true,
    note: true,
    transitions: ['AWAITING_CLIENT'],
    workflow: true,
    resolve: true,
  },
  diagnosticsAllowed: { account: false, historical: false, order: false },
  mandatoryReferralPending: false,
  referrals: [],
  history: [],
  historyPage: 0,
};
describe('private Operations workbench', () => {
  beforeEach(() => sessionStorage.clear());
  function setup() {
    const changed = new Subject<void>();
    TestBed.configureTestingModule({
      imports: [SupportWorkbenchComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: AuthService,
          useValue: {
            identity: signal({ userId: 17, roles: ['TRADING_OPS'] }),
            sessionChanged: changed,
          },
        },
      ],
    });
    const fixture = TestBed.createComponent(SupportWorkbenchComponent);
    fixture.componentRef.setInput('reference', reference);
    fixture.detectChanges();
    return { fixture, http: TestBed.inject(HttpTestingController), changed };
  }
  function flush(http: HttpTestingController, value = workbench) {
    http.expectOne((r) => r.url.endsWith('/workbench')).flush(structuredClone(value));
    http
      .expectOne((r) => r.url.endsWith('/exports'))
      .flush({ items: [], page: 0, hasMore: false, note: 'Metadata is not download authority.' });
    if (value.actions.workflow) {
      http
        .expectOne((r) => r.url.endsWith('/assignees'))
        .flush({ items: [{ id: 17, username: 'Approved Operations owner' }] });
      http
        .expectOne((r) => r.url.endsWith('/reviewers'))
        .flush({ items: [{ id: 23, username: 'Independent Compliance reviewer' }] });
    }
  }

  it('uses the opaque current case reference for independently authorized account evidence', async () => {
    const { fixture: f, http } = setup();
    flush(http, {
      ...workbench,
      tasks: [{ code: 'ACCOUNT_RESOURCES', label: 'Current account resources', state: 'PENDING' }],
    });
    f.detectChanges();
    await f.whenStable();
    const kind = f.nativeElement.querySelectorAll('article form select')[1] as HTMLSelectElement;
    kind.value = 'ACCOUNT';
    kind.dispatchEvent(new Event('change'));
    await f.whenStable();
    f.detectChanges();
    expect(f.componentInstance.data()?.tasks[0].evidenceReference).toBe(reference);
    expect(f.nativeElement.querySelector('article input').value).toBe(reference);
    expect(f.nativeElement.textContent).not.toContain('(ngModelChange)');
    http.verify();
  });
  it('retains the original command after unknown outcome and missing recovery receipt', () => {
    const { fixture: f, http } = setup();
    flush(http);
    f.detectChanges();
    f.componentInstance.assignee = 17;
    f.componentInstance.send('assignment', { assignee: 17 });
    const first = http.expectOne((r) => r.url.endsWith('/workflow/assignment'));
    const key = first.request.headers.get('Idempotency-Key');
    first.flush({ message: 'Unavailable' }, { status: 503, statusText: 'Unavailable' });
    expect(f.componentInstance.pending()).toBe(true);
    f.componentInstance.recover();
    http
      .expectOne((r) => r.url.endsWith('/workflow/requests/' + key))
      .flush({}, { status: 404, statusText: 'Unconfirmed' });
    expect(f.componentInstance.pending()).toBe(true);
    f.componentInstance.send('priority', { priority: 'HIGH', reason: 'A different new action' });
    http.expectNone((r) => r.method === 'POST');
    f.componentInstance.retry();
    const retry = http.expectOne((r) => r.url.endsWith('/workflow/assignment'));
    expect(retry.request.headers.get('Idempotency-Key')).toBe(key);
    expect(retry.request.body.expectedWorkflowVersion).toBe(1);
    retry.flush({ workflowVersion: 2 });
    flush(http, { ...workbench, workflowVersion: 2, assignee: 17 });
    expect(f.componentInstance.pending()).toBe(false);
    http.verify();
  });
  it('cancels an old route read and never publishes its protected values after identity change', () => {
    const { fixture: f, http, changed } = setup();
    const old = http.expectOne((r) => r.url.endsWith('/workbench'));
    f.componentRef.setInput('reference', reference.replace('11111111', '22222222'));
    f.detectChanges();
    expect(old.cancelled).toBe(true);
    flush(http);
    f.detectChanges();
    expect(f.nativeElement.textContent).toContain('Original structured intake');
    changed.next();
    f.detectChanges();
    expect(f.nativeElement.textContent).not.toContain('Original structured intake');
    expect(f.componentInstance.assignees()).toEqual([]);
    http.verify();
  });
  it('renders only read actions and no owner, classification or resolution controls for read-only staff', () => {
    const { fixture: f, http } = setup();
    flush(http, {
      ...workbench,
      actions: { reply: false, note: false, transitions: [], workflow: false, resolve: false },
    });
    f.detectChanges();
    expect(f.nativeElement.textContent).not.toContain('Save owner');
    expect(f.nativeElement.textContent).not.toContain('Reclassify with fresh checklist');
    expect(f.nativeElement.textContent).not.toContain('Publish explanation and resolve atomically');
    expect(f.nativeElement.textContent).toContain('No diagnostic permission is available');
    f.componentInstance.diagnose();
    http.expectNone((r) => r.url.endsWith('/diagnostics'));
    http.verify();
  });
});

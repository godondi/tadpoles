import { TestBed } from '@angular/core/testing';
import { signal, Type } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Route } from '@angular/router';
import { of, Subject } from 'rxjs';
import { CatalogRefreshService } from '../../core/api/catalog-refresh.service';
import { routes } from '../../app.routes';
import { AuthService } from '../../core/auth/auth.service';

function flat(items: Route[]): Route[] {
  return items.flatMap((r) => [r, ...flat(r.children ?? [])]);
}
const reference = 'CASE-11111111-1111-4111-8111-111111111111';
const receipt = {
  reference,
  orderId: 12,
  subject: 'Receipt question',
  category: 'ORDER_QUESTION',
  status: 'OPEN',
  version: 1,
  events: [],
  totalEvents: 1,
};
describe('owned support journey', () => {
  beforeEach(() => sessionStorage.clear());
  async function setup(path: string, params = {}) {
    const route = flat(routes).find((r) => r.path === path);
    expect(route, 'support route must exist').toBeDefined();
    const component = (await route!.loadComponent!()) as Type<unknown>;
    const changed = new Subject<void>();
    const refresh = new Subject<void>();
    TestBed.configureTestingModule({
      imports: [component],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: CatalogRefreshService, useValue: { watch: () => refresh } },
        {
          provide: AuthService,
          useValue: {
            sessionChanged: changed,
            identity: signal({ userId: 17, roles: ['CLIENT'] }),
          },
        },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              data: route!.data ?? {},
              paramMap: convertToParamMap(params),
              queryParamMap: convertToParamMap({ orderId: '12' }),
            },
            paramMap: of(convertToParamMap(params)),
          },
        },
      ],
    });
    const fixture = TestBed.createComponent(component);
    fixture.detectChanges();
    return { fixture, http: TestBed.inject(HttpTestingController), changed, refresh };
  }
  it('places new before reference and recovers the same key after an uncertain response and missing lookup', async () => {
    const paths = flat(routes).map((r) => r.path);
    expect(paths.indexOf('app/support/new')).toBeGreaterThan(-1);
    expect(paths.indexOf('app/support/new')).toBeLessThan(paths.indexOf('app/support/:reference'));
    const { fixture: f, http } = await setup('app/support/new');
    http.expectOne((r) => r.url.endsWith('/intake-policy')).flush({intakeEnabled:false,schemaVersion:1,categories:[]});
    f.detectChanges();
    http
      .expectOne((r) => r.url.endsWith('/orders/12'))
      .flush({ order: { orderId: 12, ticker: 'TEST', status: 'ACCEPTED' } });
    const enter = (selector: string, value: string) => {
      const el = f.nativeElement.querySelector(selector);
      el.value = value;
      el.dispatchEvent(new Event('input'));
    };
    enter('[name=subject]', 'Receipt question');
    enter('[name=description]', 'Please explain this retained receipt.');
    f.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));
    f.detectChanges();
    const first = http.expectOne((r) => r.url.endsWith('/support/cases') && r.method === 'POST');
    const key = first.request.headers.get('Idempotency-Key');
    expect(key).toBeTruthy();
    expect(first.request.body.orderId).toBe(12);
    expect(first.request.body.clientId).toBeUndefined();
    first.flush({}, { status: 503, statusText: 'Unavailable' });
    f.detectChanges();
    const button = [...f.nativeElement.querySelectorAll('button')].find((b: any) =>
      b.textContent.includes('Recover'),
    ) as HTMLButtonElement;
    button.click();
    http
      .expectOne((r) => r.url.endsWith('/by-request/' + key))
      .flush({}, { status: 404, statusText: 'Missing' });
    f.detectChanges();
    expect(f.nativeElement.textContent).toContain('same request key');
    f.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));
    const retry = http.expectOne((r) => r.url.endsWith('/support/cases') && r.method === 'POST');
    expect(retry.request.headers.get('Idempotency-Key')).toBe(key);
    retry.flush(receipt);
    f.detectChanges();
    expect(f.nativeElement.textContent).toContain(reference);
    expect(JSON.stringify(sessionStorage)).not.toContain('Please explain');
    http.verify();
  });
  it('renders public prose inert and clears protected detail when identity changes', async () => {
    const { fixture: f, http, changed } = await setup('app/support/:reference', { reference });
    http
      .expectOne((r) => r.url.endsWith('/support/cases/' + reference))
      .flush({
        ...receipt,
        events: [
          {
            version: 1,
            body: '<img src=x onerror=alert(1)>',
            authorRole: 'CLIENT',
            kind: 'CREATED',
            status: 'OPEN',
          },
        ],
      });
    f.detectChanges();
    expect(f.nativeElement.textContent).toContain('<img src=x');
    expect(f.nativeElement.querySelector('img')).toBeNull();
    changed.next();
    f.detectChanges();
    expect(f.nativeElement.textContent).not.toContain('<img src=x');
    http.verify();
  });
  it('offers an explicit public reply with expected version and the same retry key', async () => {
    const { fixture: f, http } = await setup('app/support/:reference', { reference });
    http
      .expectOne((r) => r.url.endsWith('/support/cases/' + reference))
      .flush({ ...receipt, events: [] });
    f.detectChanges();
    const input = f.nativeElement.querySelector('[name=reply]');
    expect(input).not.toBeNull();
    input.value = 'Additional information for this case.';
    input.dispatchEvent(new Event('input'));
    const send = [...f.nativeElement.querySelectorAll('button')].find((b: any) =>
      b.textContent.includes('Send public reply'),
    ) as HTMLButtonElement;
    send.click();
    const req = http.expectOne((r) => r.url.endsWith('/replies'));
    expect(req.request.body.expectedVersion).toBe(1);
    expect(req.request.headers.get('Idempotency-Key')).toBeTruthy();
    req.flush({}, { status: 503, statusText: 'Unavailable' });
    f.detectChanges();
    send.click();
    const retry = http.expectOne((r) => r.url.endsWith('/replies'));
    expect(retry.request.headers.get('Idempotency-Key')).toBe(
      req.request.headers.get('Idempotency-Key'),
    );
    retry.flush({ reference, version: 2, status: 'OPEN' });
    http
      .expectOne((r) => r.url.endsWith('/support/cases/' + reference))
      .flush({ ...receipt, version: 2, events: [] });
    http.verify();
  });

  it('shows durable unread updates and marks them read only after an explicit action', async () => {
    const { fixture: f, http } = await setup('app/support');
    http
      .expectOne((r) => r.url.endsWith('/support/cases'))
      .flush({ items: [], total: 0, page: 0, pageSize: 20 });
    const pending = http.match((r) => r.url.endsWith('/support/updates'));
    expect(pending.length).toBe(1);
    pending[0].flush({
      items: [
        { id: 7, reference, subject: 'Receipt question', kind: 'REPLY', status: 'IN_REVIEW' },
      ],
      unread: 1,
      cursor: 7,
      hasMore: false,
      reset: false,
    });
    f.detectChanges();
    expect(f.nativeElement.textContent).toContain('1 unread');
    http.expectNone((r) => r.url.endsWith('/updates/read'));
    const mark = [...f.nativeElement.querySelectorAll('button')].find((b: any) =>
      b.textContent.includes('Mark displayed updates read'),
    ) as HTMLButtonElement;
    expect(mark).toBeTruthy();
    mark.click();
    const request = http.expectOne((r) => r.url.endsWith('/updates/read'));
    expect(request.request.body).toEqual({ cursor: 7 });
    request.flush({ unread: 0 });
    f.detectChanges();
    expect(f.nativeElement.textContent).toContain('0 unread');
    http.verify();
  });
  it('allows a corrected message after a definitive validation rejection', async () => {
    const { fixture: f, http } = await setup('app/support/:reference', { reference });
    http.expectOne((r) => r.url.endsWith('/support/cases/' + reference)).flush(receipt);
    f.detectChanges();
    const component = f.componentInstance as any;
    component.reply = 'The original public message.';
    component.send('replies');
    http
      .expectOne((r) => r.url.endsWith('/replies'))
      .flush({ message: 'Invalid body' }, { status: 400, statusText: 'Bad Request' });
    expect(component.writePending()).toBe(false);
    component.reply = 'The corrected public message.';
    component.send('replies');
    const retry = http.expectOne((r) => r.url.endsWith('/replies'));
    expect(retry.request.body.body).toBe('The corrected public message.');
    retry.flush({ reference, version: 2, status: 'OPEN' });
    http
      .expectOne((r) => r.url.endsWith('/support/cases/' + reference))
      .flush({ ...receipt, version: 2 });
    http.verify();
  });
  it('cancels other protected requests when one support endpoint denies access', async () => {
    const { fixture: f, http } = await setup('app/support');
    const list = http.expectOne((r) => r.url.endsWith('/support/cases'));
    http
      .expectOne((r) => r.url.endsWith('/support/updates'))
      .flush({}, { status: 403, statusText: 'Forbidden' });
    expect(list.cancelled).toBe(true);
    f.detectChanges();
    expect(f.nativeElement.textContent).not.toContain(reference);
    http.verify();
  });
  it('groups case navigation in the shared spaced wrapping actions layout', async () => {
    const { fixture: f, http } = await setup('app/support/:reference', { reference });
    http.expectOne((r) => r.url.endsWith('/support/cases/' + reference)).flush(receipt);
    f.detectChanges();
    const navigation = f.nativeElement.querySelector('[aria-label="Case navigation"]');
    expect(navigation).not.toBeNull();
    expect(navigation?.classList.contains('actions')).toBe(true);
    expect(navigation?.querySelector('a')?.textContent).toContain('Back to cases');
    expect(navigation?.querySelector('button')?.textContent).toContain('Refresh conversation');
    http.verify();
  });
  it('loads every report category by default and refreshes the Operations queue without searching', async () => {
    const { fixture: f, http, refresh } = await setup('operations/cases');
    http
      .expectOne(
        (r) =>
          r.url.endsWith('/operations/cases') &&
          r.params.get('search') === '' &&
          !r.params.has('status') &&
          !r.params.has('category'),
      )
      .flush({
        items: [{ ...receipt, category: 'ACCOUNT_REVIEW' }],
        total: 1,
        page: 0,
        pageSize: 20,
      });
    f.detectChanges();
    expect(f.nativeElement.textContent).toContain('Account review');
    refresh.next();
    const request = http.expectOne((r) => r.url.endsWith('/operations/cases'));
    expect(request.request.params.get('search')).toBe('');
    request.flush({
      items: [
        { ...receipt, category: 'ACCOUNT_REVIEW' },
        {
          ...receipt,
          reference: reference.replace('11111111', '22222222'),
          category: 'TRADING_AVAILABILITY',
        },
      ],
      total: 2,
      page: 0,
      pageSize: 20,
    });
    f.detectChanges();
    expect(f.nativeElement.textContent).toContain('Trading availability');
    http.verify();
  });
  it('offers server-enabled attention views without sending private selectors to a legacy queue', async () => {
    const { fixture: f, http, refresh } = await setup('operations/cases');
    const initial = http.expectOne((r) => r.url.endsWith('/operations/cases'));
    expect(initial.request.params.has('view')).toBe(false);
    initial.flush({ items: [], total: 0, page: 0, pageSize: 20, workflowEnabled: true });
    f.detectChanges();
    await f.whenStable();
    const selector = f.nativeElement.querySelector('select[name=queueView]') as HTMLSelectElement;
    expect(selector.options.length).toBe(7);
    selector.value = 'AWAITING_REVIEW';
    selector.dispatchEvent(new Event('change'));
    f.detectChanges();
    f.nativeElement.querySelector('form.filters').dispatchEvent(new Event('submit'));
    const selected = http.expectOne((r) => r.url.endsWith('/operations/cases'));
    expect(selected.request.params.get('view')).toBe('AWAITING_REVIEW');
    selected.flush({ items: [], total: 0, page: 0, pageSize: 20, workflowEnabled: true });
    refresh.next();
    const refreshed = http.expectOne((r) => r.url.endsWith('/operations/cases'));
    expect(refreshed.request.params.get('view')).toBe('AWAITING_REVIEW');
    refreshed.flush({ items: [], total: 0, page: 0, pageSize: 20, workflowEnabled: false });
    f.detectChanges();
    expect(f.nativeElement.querySelector('select[name=queueView]')).toBeNull();
    refresh.next();
    const legacy = http.expectOne((r) => r.url.endsWith('/operations/cases'));
    expect(legacy.request.params.has('view')).toBe(false);
    legacy.flush({ items: [], total: 0, page: 0, pageSize: 20, workflowEnabled: false });
    http.verify();
  });
});

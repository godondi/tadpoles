import { RouterTestingHarness } from '@angular/router/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { InstrumentPolicyEditorComponent } from './instrument-policy-editor.component';
describe('Operations instrument controls', () => {
  it('shows provider-test progress and failures next to the symbol instead of the save button', async () => {
    TestBed.configureTestingModule({
      imports: [InstrumentPolicyEditorComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { paramMap: of(convertToParamMap({ id: 'new' })) } },
      ],
    });
    const fixture = TestBed.createComponent(InstrumentPolicyEditorComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.componentInstance.form.providerSymbol = 'SPY';
    fixture.detectChanges();
    fixture.nativeElement.querySelector('[data-test-symbol]').click();
    fixture.detectChanges();
    expect(
      fixture.nativeElement.querySelector('#instrument-symbol').closest('.form-field').textContent,
    ).toContain('Checking the symbol');
    const http = TestBed.inject(HttpTestingController);
    http
      .expectOne((request) => request.url.endsWith('/test'))
      .flush(
        { message: 'Provider is unavailable; no changes saved' },
        { status: 503, statusText: 'Unavailable' },
      );
    fixture.detectChanges();
    const inline = fixture.nativeElement.querySelector('[data-symbol-error]');
    expect(inline.textContent).toContain('Provider is unavailable');
    expect(inline.closest('.form-field').querySelector('#instrument-symbol')).not.toBeNull();
    expect(fixture.componentInstance.message()).toBe('');
    expect(fixture.componentInstance.testing()).toBe(false);
    fixture.componentInstance.invalidate();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-symbol-error]')).toBeNull();
    http.verify();
  });

  it('provides a symbol test beside the input and invalidates results when the symbol changes', async () => {
    TestBed.configureTestingModule({
      imports: [InstrumentPolicyEditorComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { paramMap: of(convertToParamMap({ id: 'new' })) } },
      ],
    });
    const f = TestBed.createComponent(InstrumentPolicyEditorComponent);
    f.detectChanges();
    await f.whenStable();
    const http = TestBed.inject(HttpTestingController);
    http.match(() => true).forEach((r) => r.flush([]));
    const input = f.nativeElement.querySelector('#instrument-symbol') as HTMLInputElement;
    expect(input).not.toBeNull();
    input.value = 'AAPL';
    input.dispatchEvent(new Event('input'));
    await f.whenStable();
    f.detectChanges();
    f.nativeElement.querySelector('[data-test-symbol]').click();
    const old = http.expectOne((r) => r.url.endsWith('/test'));
    expect(old.request.params.get('symbol')).toBe('AAPL');
    input.value = 'MSFT';
    input.dispatchEvent(new Event('input'));
    await f.whenStable();
    f.detectChanges();
    expect(old.cancelled).toBe(true);
    expect(f.nativeElement.textContent).not.toContain('Recognized: AAPL');
    http
      .match((r) => r.url.endsWith('/history'))
      .forEach((r) => r.flush({ items: [], page: 0, hasMore: false }));
    http.verify();
  });
});

describe('Operations form structure', () => {
  it('groups identity, limits and permissions with associated labels and separate checkbox controls', async () => {
    TestBed.configureTestingModule({
      imports: [InstrumentPolicyEditorComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { paramMap: of(convertToParamMap({ id: 'new' })) } },
      ],
    });
    const fixture = TestBed.createComponent(InstrumentPolicyEditorComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    const http = TestBed.inject(HttpTestingController);

    const form = fixture.nativeElement.querySelector('form.workspace-form');
    expect(form).not.toBeNull();
    const legends = Array.from(form.querySelectorAll('legend')).map((el: any) =>
      el.textContent.trim(),
    );
    expect(legends).toEqual([
      'Identity and provider validation',
      'Trading limits',
      'Trading permissions',
    ]);
    for (const control of Array.from(
      form.querySelectorAll('input:not([type="checkbox"]),textarea'),
    ) as HTMLInputElement[]) {
      expect(form.querySelector(`label[for="${control.id}"]`)).not.toBeNull();
      expect(control.closest('.form-field')).not.toBeNull();
    }
    expect(form.querySelectorAll('.checkbox-field input[type="checkbox"]').length).toBe(3);
    http
      .match((r) => r.url.endsWith('/history'))
      .forEach((r) => r.flush({ items: [], page: 0, hasMore: false }));
    http.verify();
  });
});

describe('routed instrument saves', () => {
  async function setup(path = '/operations/instruments/new') {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([
          { path: 'operations/instruments/:id', component: InstrumentPolicyEditorComponent },
        ]),
      ],
    });
    const harness = await RouterTestingHarness.create();
    const editor = await harness.navigateByUrl(path, InstrumentPolicyEditorComponent);
    return { harness, editor, http: TestBed.inject(HttpTestingController) };
  }

  const saved = {
    instrumentId: 42,
    ticker: 'SPY',
    name: 'SPDR S&P 500 ETF Trust',
    providerSymbol: 'SPY',
    venue: 'NYSE Arca',
    quantityStep: '1',
    maxQuantity: '1000',
    maxNotional: '100000',
    quoteMaxAgeSeconds: 1200,
    active: true,
    buyAllowed: true,
    sellAllowed: true,
    revision: 1,
    currency: 'USD',
    validatedAt: null,
  };

  async function fill(harness: RouterTestingHarness, editor: InstrumentPolicyEditorComponent) {
    editor.form = {
      ...editor.form,
      ...saved,
      revision: 0,
      reason: 'Enable verified provider listing',
    };
    harness.detectChanges();
    await harness.fixture.whenStable();
  }

  it('explains the limits, saves once, and keeps the editor mounted when the new ID arrives', async () => {
    const { harness, editor, http } = await setup();
    await fill(harness, editor);
    const form = harness.routeNativeElement!.querySelector('form')!;
    const step = form.querySelector('#policy-step')!;
    expect(form.querySelector('#' + step.getAttribute('aria-describedby'))!.textContent).toContain(
      'multiple',
    );
    expect(form.querySelector('#instrument-symbol')!.getAttribute('placeholder')).toBe('SPY');
    form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    harness.detectChanges();
    const request = http.expectOne((r) => r.method === 'POST');
    expect(request.request.body.reason).toBe('Enable verified provider listing');
    request.flush(saved);
    await harness.fixture.whenStable();
    harness.detectChanges();
    expect(TestBed.inject(Router).url).toBe('/operations/instruments/42');
    expect(harness.routeNativeElement!.querySelector('form')).toBe(form);
    expect(harness.routeNativeElement!.textContent).toContain('SPY saved. Revision 1.');
    expect(form.querySelector('a[href="/operations/instruments?search=SPY"]')).not.toBeNull();
    http
      .expectOne((r) => r.url.endsWith('/42/history'))
      .flush({ items: [], page: 0, hasMore: false });
    http.expectNone((r) => r.method === 'GET');
    http
      .match((r) => r.url.endsWith('/history'))
      .forEach((r) => r.flush({ items: [], page: 0, hasMore: false }));
    http.verify();
  });

  it('retains entered values and displays rejected-save errors beside the action', async () => {
    const { harness, editor, http } = await setup();
    await fill(harness, editor);
    const form = harness.routeNativeElement!.querySelector('form')!;
    form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    http
      .expectOne((r) => r.method === 'POST')
      .flush(
        { message: 'Cannot enable: provider explicitly marked the quote stale' },
        { status: 409, statusText: 'Conflict' },
      );
    harness.detectChanges();
    expect(form.querySelector('[role="alert"]')!.textContent).toContain('marked the quote stale');
    expect(editor.form.reason).toBe('Enable verified provider listing');
    expect(TestBed.inject(Router).url).toBe('/operations/instruments/new');
    expect(form.querySelector('[type="submit"]')!.hasAttribute('disabled')).toBe(false);
    http
      .match((r) => r.url.endsWith('/history'))
      .forEach((r) => r.flush({ items: [], page: 0, hasMore: false }));
    http.verify();
  });

  it('loads a deep link with its revision and saves an edit with PUT', async () => {
    const { harness, editor, http } = await setup('/operations/instruments/42');
    http.expectOne((r) => r.url.endsWith('/instruments/42')).flush(saved);
    harness.detectChanges();
    await harness.fixture.whenStable();
    editor.form.reason = 'Review the per-order limit';
    editor.form.maxQuantity = '500';
    harness.detectChanges();
    await harness.fixture.whenStable();
    harness
      .routeNativeElement!.querySelector('form')!
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    const request = http.expectOne((r) => r.method === 'PUT');
    expect(request.request.body.revision).toBe(1);
    expect(request.request.body.maxQuantity).toBe('500');
    request.flush({ ...saved, maxQuantity: '500', revision: 2 });
    await harness.fixture.whenStable();
    harness.detectChanges();
    expect(harness.routeNativeElement!.textContent).toContain('Revision 2');
    http
      .match((r) => r.url.endsWith('/history'))
      .forEach((r) => r.flush({ items: [], page: 0, hasMore: false }));
    http.verify();
  });

  it('does not let a late save response overwrite another editor opened through the sidebar', async () => {
    const { harness, editor, http } = await setup();
    await fill(harness, editor);
    harness
      .routeNativeElement!.querySelector('form')!
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    const pending = http.expectOne((r) => r.method === 'POST');
    await harness.navigateByUrl('/operations/instruments/9', InstrumentPolicyEditorComponent);
    http
      .expectOne((r) => r.url.endsWith('/instruments/9'))
      .flush({ ...saved, instrumentId: 9, ticker: 'MSFT', providerSymbol: 'MSFT' });
    pending.flush(saved);
    await harness.fixture.whenStable();
    harness.detectChanges();
    expect(TestBed.inject(Router).url).toBe('/operations/instruments/9');
    expect(editor.form.ticker).toBe('MSFT');
    expect(harness.routeNativeElement!.textContent).not.toContain('SPY saved');
    http
      .match((r) => r.url.endsWith('/history'))
      .forEach((r) => r.flush({ items: [], page: 0, hasMore: false }));
    http.verify();
  });

  it('does not submit a blank required form', async () => {
    const { harness, http } = await setup();
    harness.detectChanges();
    await harness.fixture.whenStable();
    harness
      .routeNativeElement!.querySelector('form')!
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    harness.detectChanges();
    expect(harness.routeNativeElement!.querySelector('[role="alert"]')!.textContent).toContain(
      'required fields',
    );
    http.expectNone((r) => r.method === 'POST');
    http
      .match((r) => r.url.endsWith('/history'))
      .forEach((r) => r.flush({ items: [], page: 0, hasMore: false }));
    http.verify();
  });
});

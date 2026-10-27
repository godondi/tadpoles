import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { Subject } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { OrderTicketComponent } from './order-ticket.component';
import { PendingOrderService } from './pending-order.service';
const preview = {
  eligible: true,
  currency: 'USD',
  price: '50',
  gross: '500',
  fee: '0',
  availableCash: '1000',
  availableQuantity: '0',
  projectedCash: '500',
  projectedQuantity: '10',
  quantityUnit: 'shares',
  venue: 'TEST-US',
  quoteTime: new Date().toISOString(),
};
describe('Internal order ticket', () => {
  let http: HttpTestingController;
  let changed: Subject<void>;
  const identity = signal<any>({ userId: 7 });
  beforeEach(() => {
    sessionStorage.clear();
    changed = new Subject<void>();
    identity.set({ userId: 7 });
    TestBed.configureTestingModule({
      imports: [OrderTicketComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: { identity, sessionChanged: changed } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  });
  afterEach(() => {
    http.verify();
    sessionStorage.clear();
  });
  function create() {
    const f = TestBed.createComponent(OrderTicketComponent);
    f.componentRef.setInput('instrumentId', 1);
    f.componentRef.setInput('ticker', 'AAPL');
    f.detectChanges();
    return f;
  }
  function review(c: OrderTicketComponent) {
    c.quantity = '10';
    c.review();
    http
      .expectOne((r) => r.url.endsWith('/preview'))
      .flush({ ...preview, expiresAt: new Date(Date.now() + 30000).toISOString() });
  }
  it('requires a preview and invalidates it when the instruction changes', () => {
    const c = create().componentInstance;
    c.quantity = '10';
    c.confirm();
    http.expectNone((r) => r.method === 'POST');
    review(c);
    c.quantity = '11';
    c.invalidate();
    c.confirm();
    http.expectNone((r) => r.url.endsWith('/orders'));
    expect(c.preview()).toBeNull();
  });
  it('blocks duplicate clicks and preserves exact decimal input', () => {
    const c = create().componentInstance;
    review(c);
    c.confirm();
    c.confirm();
    const req = http.expectOne((r) => r.url.endsWith('/orders'));
    expect(req.request.body.quantity).toBe('10');
    expect(req.request.headers.get('Idempotency-Key')).toBeTruthy();
    req.flush({
      orderId: 51,
      status: 'ACCEPTED',
      acceptedAt: new Date().toISOString(),
      message: 'Accepted',
    });
    expect(c.receipt()?.orderId).toBe(51);
    expect(TestBed.inject(PendingOrderService).read()).toBeNull();
  });
  it('recovers a lost response and retries the identical key and payload after 404', () => {
    const c = create().componentInstance;
    review(c);
    c.confirm();
    const first = http.expectOne((r) => r.url.endsWith('/orders'));
    const key = first.request.headers.get('Idempotency-Key');
    const body = first.request.body;
    first.error(new ProgressEvent('offline'));
    http
      .expectOne((r) => r.url.includes('/by-request/'))
      .flush({}, { status: 404, statusText: 'Not found' });
    expect(c.pending()?.key).toBe(key);
    c.send();
    const retry = http.expectOne((r) => r.url.endsWith('/orders'));
    expect(retry.request.headers.get('Idempotency-Key')).toBe(key);
    expect(retry.request.body).toEqual(body);
    retry.flush({
      orderId: 52,
      status: 'ACCEPTED',
      acceptedAt: new Date().toISOString(),
      message: 'Accepted',
    });
  });
  it('restores the saved request after reload', () => {
    TestBed.inject(PendingOrderService).save({
      key: 'saved-request-key-123',
      command: { instrumentId: 1, side: 'BUY', quantity: '1.00000001', orderType: 'MARKET' },
    });
    const c = create().componentInstance;
    http
      .expectOne((r) => r.url.endsWith('/by-request/saved-request-key-123'))
      .flush({
        orderId: 53,
        status: 'FILLED',
        acceptedAt: new Date().toISOString(),
        message: 'Filled',
      });
    expect(c.receipt()?.orderId).toBe(53);
  });
  it('shows durable rejection without misclassifying it as unknown', () => {
    const c = create().componentInstance;
    review(c);
    c.confirm();
    http
      .expectOne((r) => r.url.endsWith('/orders'))
      .flush(
        { orderId: 54, status: 'REJECTED', acceptedAt: null, message: 'Insufficient cash' },
        { status: 422, statusText: 'Unprocessable Entity' },
      );
    expect(c.receipt()?.status).toBe('REJECTED');
    expect(c.pending()).toBeNull();
  });
  it('cancels previews and clears private state on identity change', () => {
    const c = create().componentInstance;
    c.quantity = '10';
    c.review();
    const req = http.expectOne((r) => r.url.endsWith('/preview'));
    changed.next();
    identity.set({ userId: 8 });
    expect(req.cancelled).toBe(true);
    expect(c.quantity).toBe('');
    expect(c.preview()).toBeNull();
    expect(TestBed.inject(PendingOrderService).read()).toBeNull();
  });
  it('will not submit an expired estimate', () => {
    const c = create().componentInstance;
    c.quantity = '10';
    c.review();
    http
      .expectOne((r) => r.url.endsWith('/preview'))
      .flush({ ...preview, expiresAt: '2000-01-01T00:00:00Z' });
    c.confirm();
    http.expectNone((r) => r.url.endsWith('/orders'));
    expect(c.expired()).toBe(true);
  });
  it('restores the bank-return draft for its owner and requires a new review', () => {
    const original = create();
    original.componentInstance.side = 'SELL';
    original.componentInstance.quantity = '1.00000001';
    original.componentInstance.saveDraft();
    original.destroy();
    const restored = create();
    expect(restored.componentInstance.side).toBe('SELL');
    expect(restored.componentInstance.quantity).toBe('1.00000001');
    expect(restored.componentInstance.preview()).toBeNull();
    restored.componentInstance.confirm();
    http.expectNone((r) => r.method === 'POST');
    restored.destroy();
    identity.set({ userId: 8 });
    const other = create();
    expect(other.componentInstance.quantity).toBe('');
  });
  it('offers an exact sell-all draft that still needs a fresh review', () => {
    const f = create(),
      c = f.componentInstance;
    c.side = 'SELL';
    c.quantity = '1';
    c.review();
    http
      .expectOne((r) => r.url.endsWith('/preview'))
      .flush({
        ...preview,
        availableQuantity: '1.00000001',
        expiresAt: new Date(Date.now() + 30000).toISOString(),
      });
    f.detectChanges();
    const button = f.nativeElement.querySelector('[data-sell-all]');
    expect(button).not.toBeNull();
    button.click();
    expect(c.quantity).toBe('1.00000001');
    expect(c.preview()).toBeNull();
    c.confirm();
    http.expectNone((r) => r.url.endsWith('/orders'));
  });
  it('uses a bank-return draft once so a later buy cannot reopen the old sell', () => {
    const original = create();
    original.componentInstance.side = 'SELL';
    original.componentInstance.quantity = '2';
    original.componentInstance.saveDraft();
    original.destroy();
    const restored = create();
    expect(restored.componentInstance.side).toBe('SELL');
    restored.destroy();
    const next = create();
    expect(next.componentInstance.side).toBe('BUY');
    expect(next.componentInstance.quantity).toBe('');
    next.componentInstance.confirm();
    http.expectNone((r) => r.method === 'POST');
  });
  it('blocks a newly halted side while retaining pending receipt recovery',()=>{
    const f=create();review(f.componentInstance);
    f.componentRef.setInput('buyAllowed',false);f.detectChanges();
    const buy=f.nativeElement.querySelector('input[value="BUY"]');expect(buy.disabled).toBe(true);
    f.componentInstance.confirm();http.expectNone(r=>r.url.endsWith('/orders'));
  });

});

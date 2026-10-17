import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { BankingPageComponent } from './banking-page.component';
import { AuthService } from '../../core/auth/auth.service';
import { signal } from '@angular/core';
import { Subject } from 'rxjs';
describe('BankingPageComponent', () => {
  beforeEach(() =>
    TestBed.configureTestingModule({
      imports: [BankingPageComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: AuthService,
          useValue: { identity: signal({ userId: 91 }), sessionChanged: new Subject<void>() },
        },
      ],
    }),
  );
  afterEach(() => {
    TestBed.inject(HttpTestingController).verify();
    sessionStorage.clear();
  });
  it('explains internal funding and provides labelled bank and amount controls', async () => {
    const fixture = TestBed.createComponent(BankingPageComponent);
    fixture.detectChanges();
    const http = TestBed.inject(HttpTestingController);
    http
      .match(() => true)
      .forEach((r) =>
        r.flush(
          r.request.url.includes('portfolio')
            ? { balances: [{ currency: 'USD', posted: '100', reserved: '25', available: '75' }] }
            : [],
        ),
      );
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No money moves through a real bank');
    expect(fixture.nativeElement.querySelector('label[for="bank-routing"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('label[for="movement-amount"]')).not.toBeNull();
    expect(fixture.nativeElement.textContent).toContain('75');
  });
  it('returns to the exact order route with its side query intact', () => {
    TestBed.overrideProvider(ActivatedRoute, {
      useValue: {
        snapshot: { queryParamMap: convertToParamMap({ returnTo: '/app/trade/7?side=SELL' }) },
      },
    });
    const fixture = TestBed.createComponent(BankingPageComponent);
    fixture.detectChanges();
    TestBed.inject(HttpTestingController)
      .match(() => true)
      .forEach((r) => r.flush(r.request.url.includes('portfolio') ? { balances: [] } : []));
    expect(fixture.nativeElement.querySelector('a').getAttribute('href')).toBe(
      '/app/trade/7?side=SELL',
    );
  });
  it('explains invalid bank fields without truncating or submitting them', () => {
    const fixture = TestBed.createComponent(BankingPageComponent);
    fixture.detectChanges();
    const http = TestBed.inject(HttpTestingController);
    http
      .match(() => true)
      .forEach((r) => r.flush(r.request.url.includes('portfolio') ? { balances: [] } : []));
    const c = fixture.componentInstance;
    c.nickname = '<invalid>';
    c.routing = '0000000010';
    c.account = '12';
    c.saveBank();
    fixture.detectChanges();
    expect(
      fixture.nativeElement.querySelector('#bank-routing').getAttribute('maxlength'),
    ).toBeNull();
    expect(fixture.nativeElement.textContent).toContain(
      'Routing number must contain exactly 9 digits',
    );
    expect(fixture.nativeElement.textContent).toContain('Account number must contain 4–17 digits');
    http.expectNone((r) => r.method === 'POST');
  });
  it('shows the exact projected available USD effect before confirmation', () => {
    const fixture = TestBed.createComponent(BankingPageComponent);
    fixture.detectChanges();
    const http = TestBed.inject(HttpTestingController);
    http
      .match(() => true)
      .forEach((r) =>
        r.flush(
          r.request.url.includes('portfolio')
            ? {
                balances: [
                  {
                    currency: 'USD',
                    posted: '10000000000000.01',
                    available: '10000000000000.01',
                    reserved: '0',
                  },
                ],
              }
            : [],
        ),
      );
    const c = fixture.componentInstance;
    c.selectedBank = 'test';
    c.direction = 'DEPOSIT';
    c.amount = '0.01';
    c.review();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain(
      'Estimated available after movement: 10000000000000.02 USD',
    );
    c.direction = 'WITHDRAW';
    c.amount = '0.01';
    c.review();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain(
      'Estimated available after movement: 10000000000000.00 USD',
    );
    http.expectNone((r) => r.method === 'POST');
  });
  it('recovers after reload and retries with the same request reference', () => {
    const saved = {
      key: 'movement-request-0001',
      command: {
        bankId: '9caa161b-5a76-41b8-9044-bd12d18f23a5',
        direction: 'WITHDRAW',
        amount: '25.00',
      },
    };
    sessionStorage.setItem('tadpoles.pending-movement.91', JSON.stringify(saved));
    const fixture = TestBed.createComponent(BankingPageComponent);
    fixture.detectChanges();
    const http = TestBed.inject(HttpTestingController);
    http
      .expectOne((r) => r.url.includes('/cash-movements/by-request/'))
      .flush({}, { status: 404, statusText: 'Not found' });
    http
      .match(() => true)
      .forEach((r) => r.flush(r.request.url.includes('portfolio') ? { balances: [] } : []));
    fixture.detectChanges();
    fixture.nativeElement.querySelector('[data-testid="retry-movement"]').click();
    const retry = http.expectOne((r) => r.method === 'POST' && r.url.endsWith('/cash-movements'));
    expect(retry.request.headers.get('Idempotency-Key')).toBe(saved.key);
    expect(retry.request.body).toEqual(saved.command);
    retry.flush({
      movementId: 'receipt',
      ...saved.command,
      currency: 'USD',
      status: 'SETTLED',
      bankSnapshot: 'Test •••• 1234',
    });
    http
      .match(() => true)
      .forEach((r) => r.flush(r.request.url.includes('portfolio') ? { balances: [] } : []));
    fixture.detectChanges();
    expect(sessionStorage.getItem('tadpoles.pending-movement.91')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('SETTLED');
  });

  it('allows correction after a definitive rejected POST, while unknown responses remain recoverable', () => {
    const saved = {
      key: 'movement-request-0002',
      command: {
        bankId: '9caa161b-5a76-41b8-9044-bd12d18f23a5',
        direction: 'WITHDRAW',
        amount: '25.00',
      },
    };
    sessionStorage.setItem('tadpoles.pending-movement.91', JSON.stringify(saved));
    const fixture = TestBed.createComponent(BankingPageComponent);
    fixture.detectChanges();
    const http = TestBed.inject(HttpTestingController);
    http
      .expectOne((r) => r.url.includes('/cash-movements/by-request/'))
      .flush({}, { status: 404, statusText: 'Not found' });
    http
      .match(() => true)
      .forEach((r) => r.flush(r.request.url.includes('portfolio') ? { balances: [] } : []));
    fixture.componentInstance.send();
    http
      .expectOne((r) => r.method === 'POST')
      .flush({ message: 'Bank reference not found' }, { status: 404, statusText: 'Not found' });
    fixture.detectChanges();
    expect(fixture.componentInstance.pending()).toBeNull();
    expect(sessionStorage.getItem('tadpoles.pending-movement.91')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('not accepted');
  });
});

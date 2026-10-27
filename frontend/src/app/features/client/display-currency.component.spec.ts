import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Subject } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { DisplayCurrencyComponent } from './display-currency.component';
describe('display currency estimates', () => {
  it('cancels obsolete requests and clears estimates on session changes without changing USD', () => {
    const sessionChanged = new Subject<void>();
    TestBed.configureTestingModule({
      imports: [DisplayCurrencyComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { sessionChanged } },
      ],
    });
    const f = TestBed.createComponent(DisplayCurrencyComponent);
    const http = TestBed.inject(HttpTestingController);
    f.componentRef.setInput('availableUsd', '100.01');
    f.detectChanges();
    expect(f.nativeElement.textContent).toContain('100.01 USD');
    expect(f.nativeElement.textContent).not.toContain('Informational cash estimate only');
    const select = f.nativeElement.querySelector('select') as HTMLSelectElement;
    select.value = 'GBP';
    select.dispatchEvent(new Event('change'));
    const old = http.expectOne((r) => r.params.get('currency') === 'GBP');
    select.value = 'EUR';
    select.dispatchEvent(new Event('change'));
    expect(old.cancelled).toBe(true);
    http
      .expectOne((r) => r.params.get('currency') === 'EUR')
      .flush({
        currency: 'EUR',
        usd: '100.01',
        estimatedValue: '80.01',
        rateDate: '2026-10-15',
        targetPerUsd: '0.8',
        source: 'ECB euro foreign exchange reference rates',
      });
    f.detectChanges();
    expect(f.nativeElement.textContent).toContain('80.01 EUR');
    expect(f.nativeElement.textContent).toContain('100.01 USD');
    expect(f.nativeElement.textContent).toContain('2026-10-15');
    sessionChanged.next();
    f.detectChanges();
    expect(f.nativeElement.textContent).not.toContain('80.01 EUR');
    http.verify();
  });
  it('shows rate errors with retry and never substitutes a made-up estimate', () => {
    TestBed.configureTestingModule({
      imports: [DisplayCurrencyComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { sessionChanged: new Subject<void>() } },
      ],
    });
    const f = TestBed.createComponent(DisplayCurrencyComponent);
    f.componentRef.setInput('availableUsd', '10');
    f.detectChanges();
    const select = f.nativeElement.querySelector('select') as HTMLSelectElement;
    select.value = 'GBP';
    select.dispatchEvent(new Event('change'));
    const http = TestBed.inject(HttpTestingController);
    http
      .expectOne((r) => r.params.get('currency') === 'GBP')
      .flush({}, { status: 503, statusText: 'Unavailable' });
    f.detectChanges();
    expect(f.nativeElement.textContent).toContain('Estimate unavailable');
    f.nativeElement.querySelector('button').click();
    http
      .expectOne((r) => r.params.get('currency') === 'GBP')
      .flush({
        currency: 'GBP',
        usd: '10',
        estimatedValue: '8',
        rateDate: '2026-10-15',
        targetPerUsd: '0.8',
        source: 'ECB',
      });
    f.detectChanges();
    expect(f.nativeElement.textContent).toContain('8.00 GBP');
    http.verify();
  });
});

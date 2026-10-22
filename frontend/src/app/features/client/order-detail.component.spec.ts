import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { EMPTY, of, Subject } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { OrderStatusService } from '../../core/api/order-status.service';
import { OrderDetailComponent } from './order-detail.component';
describe('committed order evidence', () => {
  it('does not label retained internal evidence as incomplete historical evidence', () => {
    TestBed.configureTestingModule({
      imports: [OrderDetailComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { paramMap: of(convertToParamMap({ id: '1' })) } },
        { provide: AuthService, useValue: { sessionChanged: new Subject<void>() } },
        {
          provide: OrderStatusService,
          useValue: { health: () => 'IDLE', lastSuccess: () => null, watch: () => EMPTY },
        },
      ],
    });
    const f = TestBed.createComponent(OrderDetailComponent);
    const http = TestBed.inject(HttpTestingController);
    http
      .expectOne((r) => r.url.endsWith('/orders/1'))
      .flush({
        order: {
          orderId: 1,
          ticker: 'AAPL',
          status: 'FILLED',
          side: 'BUY',
          exactQuantity: '1',
          executionMode: 'INTERNAL',
        },
        fills: [],
        events: [],
        evidenceCompleteness: 'RETAINED_INTERNAL_EVIDENCE',
        explanation: 'Retained evidence',
      });
    f.detectChanges();
    expect(f.nativeElement.textContent).not.toContain('Incomplete historical evidence');
    expect(f.nativeElement.querySelector('a[href="/app/support/new?orderId=1"]')).not.toBeNull();
    http.verify();
  });
  it('shows retained execution quotes and pages beyond the first 100 events', () => {
    TestBed.configureTestingModule({
      imports: [OrderDetailComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { paramMap: of(convertToParamMap({ id: '1' })) } },
        { provide: AuthService, useValue: { sessionChanged: new Subject<void>() } },
        {
          provide: OrderStatusService,
          useValue: { watch: () => EMPTY, health: () => 'IDLE', lastSuccess: () => null },
        },
      ],
    });
    const f = TestBed.createComponent(OrderDetailComponent);
    const http = TestBed.inject(HttpTestingController);
    http
      .expectOne((r) => r.url.endsWith('/orders/1'))
      .flush({
        order: { orderId: 1, status: 'FILLED', side: 'BUY', ticker: 'TEST' },
        fills: [],
        events: [],
        eventTotal: 101,
        evidenceCompleteness: 'RETAINED_INTERNAL_EVIDENCE',
        quotes: [
          {
            quoteId: 'retained-quote',
            stage: 'EXECUTION',
            applicableSide: 'ASK',
            price: '50.125',
            currency: 'USD',
            usedForFill: true,
            source: 'Retained test source',
          },
        ],
      });
    f.detectChanges();
    expect(f.nativeElement.textContent).toContain('50.125');
    expect(f.nativeElement.textContent).toContain('Retained test source');
    const next = [...f.nativeElement.querySelectorAll('button')].find((b: any) =>
      b.textContent.includes('Next events'),
    ) as HTMLButtonElement;
    expect(next).toBeTruthy();
    next.click();
    const page = http.expectOne((r) => r.url.endsWith('/orders/1/events'));
    expect(page.request.params.get('page')).toBe('1');
    page.flush({
      items: [
        { eventId: 'event-101', sequence: 101, status: 'FILLED', reason: 'Final retained event' },
      ],
      page: 1,
      pageSize: 100,
      total: 101,
    });
    f.detectChanges();
    expect(f.nativeElement.textContent).toContain('Final retained event');
    http.verify();
  });
});

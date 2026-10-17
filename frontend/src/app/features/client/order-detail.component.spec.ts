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
        { provide: OrderStatusService, useValue: { watch: () => EMPTY } },
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
    http.verify();
  });
});

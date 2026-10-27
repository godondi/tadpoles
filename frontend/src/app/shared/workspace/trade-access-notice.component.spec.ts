import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TradeAccessNoticeComponent } from './trade-access-notice.component';

describe('client trade access notice', () => {
  beforeEach(() =>
    TestBed.configureTestingModule({
      imports: [TradeAccessNoticeComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }),
  );
  it('shows a persistent contact-support message for an indefinite revocation', () => {
    const fixture = TestBed.createComponent(TradeAccessNoticeComponent);
    fixture.detectChanges();
    TestBed.inject(HttpTestingController)
      .expectOne((r) => r.url.endsWith('/api/workspace/client/trade-access'))
      .flush({ revoked: true, revokedUntil: '' });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain(
      'Trade access revoked. Contact support for additional details',
    );
    expect(fixture.nativeElement.textContent).toContain('This restriction is indefinite.');
  });
  it('shows the scheduled end date for a timed revocation', () => {
    const fixture = TestBed.createComponent(TradeAccessNoticeComponent);
    fixture.detectChanges();
    TestBed.inject(HttpTestingController)
      .expectOne((r) => r.url.endsWith('/api/workspace/client/trade-access'))
      .flush({ revoked: true, revokedUntil: '2026-12-01T00:00:00Z' });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('November 30, 2026');
  });
});

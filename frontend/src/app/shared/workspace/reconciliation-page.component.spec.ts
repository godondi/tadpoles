import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of, Subject } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { ReconciliationPageComponent } from './reconciliation-page.component';
describe('finance reconciliation', () => {
  it('shows a persisted snapshot and explicitly runs a new reconciliation', () => {
    TestBed.configureTestingModule({
      imports: [ReconciliationPageComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: { sessionChanged: new Subject<void>() } },
        {
          provide: ActivatedRoute,
          useValue: {
            data: of({
              title: 'Reconciliation',
              section: 'Finance',
              endpoint: '/api/finance/reconciliations',
            }),
            paramMap: of(convertToParamMap({})),
          },
        },
      ],
    });
    const f = TestBed.createComponent(ReconciliationPageComponent),
      http = TestBed.inject(HttpTestingController);
    http
      .expectOne((r) => r.method === 'GET' && r.url.endsWith('/reconciliations'))
      .flush({
        runId: 'prior',
        matched: true,
        checkedAt: '2026-10-15T20:00:00Z',
        exceptions: [],
        historicalExceptions: 2,
      });
    f.detectChanges();
    expect(f.nativeElement.textContent).toContain('No unexplained differences');
    const button = [...f.nativeElement.querySelectorAll('button')].find((b: any) =>
      b.textContent.includes('Run reconciliation'),
    ) as HTMLButtonElement;
    expect(button).toBeTruthy();
    button.click();
    http
      .expectOne((r) => r.method === 'POST' && r.url.endsWith('/reconciliations'))
      .flush({
        runId: 'new',
        matched: false,
        checkedAt: '2026-10-16T20:00:00Z',
        exceptions: [{ kind: 'CASH', reference: '7:USD' }],
        historicalExceptions: 2,
      });
    f.detectChanges();
    expect(f.nativeElement.textContent).toContain('Differences require review');
    expect(f.nativeElement.textContent).toContain('7:USD');
    http.verify();
  });
});

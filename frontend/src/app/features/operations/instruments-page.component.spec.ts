import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { Subject } from 'rxjs';
import { CatalogRefreshService } from '../../core/api/catalog-refresh.service';
import { AuthService } from '../../core/auth/auth.service';
import { OperationsInstrumentsComponent } from './instruments-page.component';
describe('Operations catalog navigation', () => {
  it('opens Add instrument as a route instead of embedding an off-screen editor', async () => {
    TestBed.configureTestingModule({
      imports: [OperationsInstrumentsComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: { sessionChanged: new Subject<void>() } },
      ],
    });
    const fixture = TestBed.createComponent(OperationsInstrumentsComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    TestBed.inject(HttpTestingController)
      .expectOne((r) => r.url.endsWith('/instruments'))
      .flush([]);
    fixture.detectChanges();
    expect(
      fixture.nativeElement.querySelector('a[href="/operations/instruments/new"]'),
    ).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.workspace-form')).toBeNull();
  });
});

describe('Operations live catalog', () => {
  it('updates saved policies without replacing the table, changing draft filters, or clearing rows on failure', () => {
    const changes = new Subject<void>();
    TestBed.configureTestingModule({
      imports: [OperationsInstrumentsComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: { sessionChanged: new Subject<void>() } },
        { provide: CatalogRefreshService, useValue: { watch: () => changes } },
      ],
    });
    const fixture = TestBed.createComponent(OperationsInstrumentsComponent);
    const http = TestBed.inject(HttpTestingController);
    http
      .expectOne((r) => r.url.endsWith('/instruments'))
      .flush([{ instrumentId: 42, ticker: 'SPY', name: 'Fund', active: true, revision: 1 }]);
    fixture.detectChanges();
    const table = fixture.nativeElement.querySelector('table');
    fixture.componentInstance.search = 'unfinished draft';
    changes.next();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('table')).toBe(table);
    const request = http.expectOne((r) => r.url.endsWith('/instruments'));
    expect(request.request.params.get('search')).toBe('');
    request.flush([
      { instrumentId: 42, ticker: 'SPY', name: 'Updated fund', active: true, revision: 2 },
    ]);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Updated fund');
    expect(fixture.componentInstance.search).toBe('unfinished draft');
    changes.next();
    http
      .expectOne((r) => r.url.endsWith('/instruments'))
      .flush({}, { status: 503, statusText: 'Unavailable' });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('table')).toBe(table);
    expect(fixture.nativeElement.textContent).toContain('Updated fund');
    fixture.destroy();
    changes.next();
    http.verify();
  });
});

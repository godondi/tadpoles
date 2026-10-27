import { convertToParamMap, ParamMap, ActivatedRoute } from '@angular/router';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { BehaviorSubject, Subject } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { AdvisorClientDetailPageComponent } from './advisor-client-detail-page.component';

const position = { instrumentId: 42, ticker: 'TST', name: 'Test Company', currency: 'USD', owned: '0.0001', reserved: '0.0001', available: '0', updatedAt: '2026-10-22T18:00:00Z' };
const recommendation = { recommendationId: 9, instrumentId: 43, ticker: 'NEW', instrumentName: 'New Company', reasoning: 'Diversification', version: 3, updatedAt: position.updatedAt };

describe('AdvisorClientDetailPageComponent', () => {
  function setup() {
    const routeParams = new BehaviorSubject<ParamMap>(convertToParamMap({ id: '7' }));
    const sessionChanged = new Subject<void>();
    TestBed.configureTestingModule({
      imports: [AdvisorClientDetailPageComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { paramMap: routeParams.asObservable() } },
        { provide: AuthService, useValue: { sessionChanged } },
      ],
    });
    const fixture = TestBed.createComponent(AdvisorClientDetailPageComponent);
    const http = TestBed.inject(HttpTestingController);
    return { fixture, http, routeParams, sessionChanged };
  }

  function populated() {
    const context = setup();
    const { http, fixture } = context;
    http.expectOne('/api/workspace/advisor/clients/7/portfolio').flush({ clientId: 7, clientName: 'Demo Client', clientReference: 'CLIENT-7', currentPositions: [position], retrievedAt: position.updatedAt });
    http.expectOne('/api/workspace/advisor/clients/7/guidance').flush({ items: [{ instrumentId: 42, status: 'HOLD', reasoning: 'Existing', authorName: 'Demo Advisor', version: 3, updatedAt: position.updatedAt }], asOf: position.updatedAt });
    http.expectOne('/api/workspace/advisor/clients/7/recommendations').flush({ items: [recommendation], asOf: position.updatedAt });
    http.expectOne((r) => r.url.endsWith('/advisor/instruments')).flush({ items: [42, 43, 44].map((instrumentId) => ({ instrumentId, ticker: `T${instrumentId}`, instrumentName: 'Catalog instrument', currency: 'USD', isActive: true })), page: 0, pageSize: 100, total: 3 });
    fixture.detectChanges();
    return context;
  }

  afterEach(() => {
    try { TestBed.inject(HttpTestingController).verify({ ignoreCancelled: true }); }
    finally { TestBed.resetTestingModule(); }
  });

  it('preserves a conflicted guidance draft and explicitly rebases before a NONE clear', () => {
    const { fixture, http } = populated();
    const page = fixture.componentInstance;
    page.updateGuidanceStatus(42, { target: { value: 'NONE' } } as unknown as Event);
    page.updateGuidanceReasoning(42, { target: { value: 'My note' } } as unknown as Event);
    page.saveGuidance(position);
    http.expectOne('/api/workspace/advisor/clients/7/guidance/42').flush({}, { status: 409, statusText: 'Conflict' });
    http.expectOne('/api/workspace/advisor/clients/7/guidance').flush({ items: [{ instrumentId: 42, status: 'SELL', reasoning: 'Other note', version: 4, authorName: 'Demo Advisor', updatedAt: position.updatedAt }], asOf: position.updatedAt });
    expect(page.guidanceDraft(42).reasoning).toBe('My note');
    page.saveGuidance(position);
    http.expectNone('/api/workspace/advisor/clients/7/guidance/42');
    page.rebaseGuidance(42);
    page.saveGuidance(position);
    const save = http.expectOne('/api/workspace/advisor/clients/7/guidance/42');
    expect(save.request.body).toEqual({ status: 'NONE', reasoning: 'My note', expectedVersion: 4 });
    save.flush({ instrumentId: 42, status: 'NONE', reasoning: null, version: 5, authorName: 'Demo Advisor', updatedAt: position.updatedAt });
    expect(page.guidanceFor(42)?.version).toBe(5);
    expect(page.guidanceDraft(42).status).toBe('NONE');
  });

  it('validates recommendation drafts, preserves conflicts, and saves confirmed versions', () => {
    const { fixture, http } = populated();
    const page = fixture.componentInstance;
    for (const text of ['   ', 'x'.repeat(2001)]) {
      page.updateRecommendationReasoning(9, { target: { value: text } } as unknown as Event);
      page.saveRecommendation(recommendation);
      http.expectNone('/api/workspace/advisor/clients/7/recommendations/9');
    }
    page.updateRecommendationReasoning(9, { target: { value: ' My draft ' } } as unknown as Event);
    page.saveRecommendation(recommendation);
    page.saveRecommendation(recommendation);
    const save = http.expectOne('/api/workspace/advisor/clients/7/recommendations/9');
    expect(save.request.body).toEqual({ reasoning: 'My draft', expectedVersion: 3 });
    save.flush({}, { status: 409, statusText: 'Conflict' });
    http.expectOne('/api/workspace/advisor/clients/7/recommendations').flush({ items: [{ ...recommendation, version: 4 }], asOf: position.updatedAt });
    http.expectOne('/api/workspace/advisor/clients/7/portfolio').flush({ currentPositions: [position] });
    page.rebaseRecommendation(9);
    page.saveRecommendation({ ...recommendation, version: 4 });
    const retry = http.expectOne('/api/workspace/advisor/clients/7/recommendations/9');
    expect(retry.request.body.expectedVersion).toBe(4);
    retry.flush({ ...recommendation, reasoning: 'My draft', version: 5 });
    expect(page.recommendations.data()?.items[0].version).toBe(5);
    expect(page.recommendationDrafts()).toEqual({});
  });

  it('creates a trimmed recommendation once and refreshes eligibility after confirmation', () => {
    const { fixture, http } = populated();
    const page = fixture.componentInstance;
    page.newRecommendationInstrument.set(44);
    page.newRecommendationReasoning = '  Review diversification  ';
    page.createRecommendation();
    page.createRecommendation();
    const request = http.expectOne('/api/workspace/advisor/clients/7/recommendations');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ instrumentId: 44, reasoning: 'Review diversification' });
    request.flush({ ...recommendation, instrumentId: 44, recommendationId: 10 });
    http.expectOne('/api/workspace/advisor/clients/7/recommendations').flush({ items: [recommendation, { ...recommendation, instrumentId: 44, recommendationId: 10 }], asOf: position.updatedAt });
    http.expectOne('/api/workspace/advisor/clients/7/portfolio').flush({ currentPositions: [position] });
    expect(page.eligibleInstruments(page.instruments.data()!)).toEqual([]);
    expect(page.newRecommendationReasoning).toBe('');
    http.expectNone((r) => /orders|trade-suggestions/.test(r.url));
  });

  it('archives only after confirmation and preserves the row until success', () => {
    const { fixture, http } = populated();
    const page = fixture.componentInstance;
    page.requestArchive(recommendation);
    http.expectNone('/api/workspace/advisor/clients/7/recommendations/9');
    page.confirmArchive();
    expect(page.recommendations.data()?.items).toHaveLength(1);
    const request = http.expectOne('/api/workspace/advisor/clients/7/recommendations/9');
    expect(request.request.method).toBe('DELETE');
    request.flush({ recommendationId: 9, version: 4 });
    http.expectOne('/api/workspace/advisor/clients/7/recommendations').flush({ items: [], asOf: position.updatedAt });
    http.expectOne('/api/workspace/advisor/clients/7/portfolio').flush({ currentPositions: [position] });
    expect(page.pendingArchive()).toBeNull();
    expect(page.recommendations.data()?.items).toEqual([]);
  });

  it('shows guidance author and server timestamp in UTC', () => {
    const { fixture } = populated();
    expect(fixture.nativeElement.textContent).toContain('Demo Advisor');
    expect(fixture.nativeElement.textContent).toContain('UTC');
  });

  it('locks editable controls while saving so confirmation cannot overwrite a newer draft', () => {
    const { fixture, http } = populated();
    fixture.componentInstance.saveGuidance(position);
    fixture.detectChanges();
    expect((fixture.nativeElement.querySelector('#guidance-status-42') as HTMLSelectElement).disabled).toBe(true);
    expect((fixture.nativeElement.querySelector('#guidance-reasoning-42') as HTMLTextAreaElement).disabled).toBe(true);
    http.expectOne('/api/workspace/advisor/clients/7/guidance/42').flush({}, { status: 503, statusText: 'Unavailable' });
  });

  it('clears drafts, pending confirmation and in-flight writes on session change', () => {
    const { fixture, http, sessionChanged } = populated();
    const page = fixture.componentInstance;
    page.newRecommendationReasoning = 'Private draft';
    page.updateGuidanceReasoning(42, { target: { value: 'Private note' } } as unknown as Event);
    page.requestArchive(recommendation);
    page.saveGuidance(position);
    const request = http.expectOne('/api/workspace/advisor/clients/7/guidance/42');
    sessionChanged.next();
    fixture.detectChanges();
    expect(request.cancelled).toBe(true);
    expect(page.guidanceDrafts()).toEqual({});
    expect(page.pendingArchive()).toBeNull();
    expect(page.newRecommendationReasoning).toBe('');
    expect(page.selectedClientId()).toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain('Demo Client');
  });

  it('clears all protected client content after a permission failure', () => {
    const { fixture, http } = populated();
    fixture.componentInstance.loadGuidance();
    http.expectOne('/api/workspace/advisor/clients/7/guidance').flush({}, { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).not.toContain('Demo Client');
    expect(fixture.componentInstance.portfolio.data()).toBeNull();
    expect(fixture.componentInstance.recommendations.data()).toBeNull();
  });

  it('cancels previous reads on client switch and clears state for invalid routes', () => {
    const { fixture, http, routeParams } = setup();
    const old = http.match((r) => r.url.includes('/clients/7/'));
    routeParams.next(convertToParamMap({ id: '8' }));
    expect(old.every((r) => r.cancelled)).toBe(true);
    http.expectOne('/api/workspace/advisor/clients/8/portfolio').flush({ clientId: 8, clientName: 'Next Client', currentPositions: [], retrievedAt: position.updatedAt });
    routeParams.next(convertToParamMap({ id: 'bad' }));
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).not.toContain('Next Client');
    expect(fixture.componentInstance.portfolio.data()).toBeNull();
    http.match(() => true);
  });

  it('does not send duplicate guidance saves and validates rationale length', () => {
    const { fixture, http } = populated();
    const page = fixture.componentInstance;
    page.saveGuidance(position);
    page.saveGuidance(position);
    const requests = http.match('/api/workspace/advisor/clients/7/guidance/42');
    expect(requests.length).toBe(1);
    requests.forEach((r) => r.flush({}, { status: 503, statusText: 'Unavailable' }));
    page.updateGuidanceReasoning(42, { target: { value: 'x'.repeat(2001) } } as unknown as Event);
    page.saveGuidance(position);
    http.expectNone('/api/workspace/advisor/clients/7/guidance/42');
    expect(page.guidanceSaveState(42).status).toBe(400);
  });

  it('fails eligibility closed during a recommendation outage and rechecks submit', () => {
    const { fixture, http } = populated();
    const page = fixture.componentInstance;
    expect(page.eligibleInstruments(page.instruments.data()!).map((i) => i.instrumentId)).toEqual([44]);
    page.newRecommendationInstrument.set(42);
    page.newRecommendationReasoning = 'Not eligible';
    page.createRecommendation();
    http.expectNone('/api/workspace/advisor/clients/7/recommendations');
    page.loadRecommendations();
    http.expectOne('/api/workspace/advisor/clients/7/recommendations').flush({}, { status: 503, statusText: 'Unavailable' });
    expect(page.eligibleInstruments(page.instruments.data()!)).toEqual([]);
    page.newRecommendationInstrument.set(44);
    page.createRecommendation();
    http.expectNone('/api/workspace/advisor/clients/7/recommendations');
  });

  it('omits recommendations for instruments acquired on portfolio refresh', () => {
    const { fixture, http } = populated();
    fixture.componentInstance.loadPortfolio();
    http.expectOne('/api/workspace/advisor/clients/7/portfolio').flush({ clientId: 7, clientName: 'Demo Client', currentPositions: [{ ...position, instrumentId: 43 }], retrievedAt: position.updatedAt });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('#recommendation-reasoning-9')).toBeNull();
  });

  it('preserves the archive row on failure and gives confirmation keyboard focus', () => {
    const { fixture, http } = populated();
    const archive = [...fixture.nativeElement.querySelectorAll('button')].find((b: any) => b.textContent.trim() === 'Archive') as HTMLButtonElement;
    archive.click();
    fixture.detectChanges();
    const confirmation = fixture.nativeElement.querySelector('[aria-labelledby="archive-heading"]');
    expect(confirmation.contains(document.activeElement)).toBe(true);
    fixture.componentInstance.confirmArchive();
    fixture.componentInstance.confirmArchive();
    const requests = http.match('/api/workspace/advisor/clients/7/recommendations/9');
    expect(requests.length).toBe(1);
    expect(requests[0].request.body).toEqual({ expectedVersion: 3 });
    requests[0].flush({}, { status: 503, statusText: 'Unavailable' });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('#recommendation-reasoning-9')).not.toBeNull();
    expect(confirmation.textContent).toContain('temporarily unavailable');
    confirmation.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    fixture.detectChanges();
    expect(fixture.componentInstance.pendingArchive()).toBeNull();
    expect(document.activeElement).toBe(archive);
  });

  it('keeps holdings visible and distinguishes unavailable guidance from N/A', () => {
    const { fixture, http } = setup();
    http.expectOne('/api/workspace/advisor/clients/7/portfolio').flush({
      clientId: 7,
      clientName: 'Demo Client',
      clientReference: 'CLIENT-7',
      currentPositions: [
        {
          instrumentId: 42,
          ticker: 'TST',
          name: 'Test Company',
          currency: 'USD',
          owned: '1.25',
          reserved: '1.25',
          available: '0',
          updatedAt: '2026-10-22T18:00:00Z',
        },
      ],
      retrievedAt: '2026-10-22T18:00:00Z',
    });
    http.expectOne('/api/workspace/advisor/clients/7/guidance').flush(
      { detail: 'Guidance service unavailable.' },
      { status: 503, statusText: 'Unavailable' },
    );
    http.expectOne('/api/workspace/advisor/clients/7/recommendations').flush({ items: [], asOf: null });
    http
      .expectOne((request) => request.url.endsWith('/api/workspace/advisor/instruments'))
      .flush({ items: [], page: 0, pageSize: 100, total: 0 });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Test Company');
    expect(fixture.nativeElement.textContent).toContain('Unavailable');
    expect(fixture.nativeElement.textContent).not.toContain('N/A');
    expect(fixture.nativeElement.textContent).toContain('No recommendations are recorded');
  });

  it('preserves a failed guidance draft and never submits an order', () => {
    const { fixture, http } = setup();
    const position = {
      instrumentId: 42,
      ticker: 'TST',
      name: 'Test Company',
      currency: 'USD',
      owned: '1.25',
      reserved: '0',
      available: '1.25',
      updatedAt: '2026-10-22T18:00:00Z',
    };
    http.expectOne('/api/workspace/advisor/clients/7/portfolio').flush({
      clientId: 7,
      clientName: 'Demo Client',
      clientReference: 'CLIENT-7',
      currentPositions: [position],
      retrievedAt: '2026-10-22T18:00:00Z',
    });
    http.expectOne('/api/workspace/advisor/clients/7/guidance').flush({
      items: [{ instrumentId: 42, status: 'HOLD', reasoning: 'Existing note', authorName: 'Advisor', version: 3, updatedAt: '2026-10-22T18:00:00Z' }],
      asOf: '2026-10-22T18:00:00Z',
    });
    http.expectOne('/api/workspace/advisor/clients/7/recommendations').flush({ items: [], asOf: null });
    http.expectOne((request) => request.url.endsWith('/api/workspace/advisor/instruments')).flush({ items: [], page: 0, pageSize: 100, total: 0 });
    fixture.detectChanges();

    fixture.componentInstance.updateGuidanceReasoning(42, {
      target: { value: 'Keep while reviewing concentration.' },
    } as unknown as Event);
    fixture.componentInstance.saveGuidance(position);
    const save = http.expectOne('/api/workspace/advisor/clients/7/guidance/42');
    expect(save.request.body.expectedVersion).toBe(3);
    expect(save.request.url).not.toContain('/orders');
    save.flush({ detail: 'Temporary advisory failure.' }, { status: 503, statusText: 'Unavailable' });
    fixture.detectChanges();

    expect(fixture.componentInstance.guidanceDraft(42).reasoning).toBe('Keep while reviewing concentration.');
    expect(fixture.nativeElement.textContent).toContain('Temporary advisory failure.');
    expect(fixture.nativeElement.textContent).not.toContain('/orders');
  });
});

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AnalyticsApiService, HoldingAllocation, ReportMetadata } from '../../core/api/analytics-api.service';
import { AnalyticsPageComponent } from './analytics-page.component';

describe('AnalyticsPageComponent', () => {
  let fixture: ComponentFixture<AnalyticsPageComponent>;
  const api = { summary: vi.fn(), trends: vi.fn(), instruments: vi.fn(), freshness: vi.fn(), portfolioPerformance: vi.fn(), portfolioHoldings: vi.fn() };
  beforeEach(async () => {
    api.summary.mockReturnValue(throwError(() => ({ status: 503, error: { detail: 'Reporting source unavailable.' } })));
    api.freshness.mockReturnValue(throwError(() => ({ status: 503, error: { detail: 'Reporting source unavailable.' } })));
    api.instruments.mockReturnValue(throwError(() => ({ status: 503, error: { detail: 'Reporting source unavailable.' } })));
    api.portfolioPerformance.mockReturnValue(throwError(() => ({ status: 503, error: { detail: 'Reporting source unavailable.' } })));
    api.portfolioHoldings.mockReturnValue(throwError(() => ({ status: 503, error: { detail: 'Reporting source unavailable.' } })));
    vi.clearAllMocks();
    await TestBed.configureTestingModule({ imports: [AnalyticsPageComponent], providers: [provideRouter([]), { provide: AnalyticsApiService, useValue: api }, { provide: ActivatedRoute, useValue: { data: of({ analyticsView: 'overview', title: 'Analytics overview', description: 'Aggregate activity.' }), queryParamMap: of(convertToParamMap({})) } }] }).compileComponents();
    fixture = TestBed.createComponent(AnalyticsPageComponent);
    fixture.detectChanges();
  });
  it('keeps filters visible and labels an unavailable report without invented values', () => {
    expect(fixture.nativeElement.textContent).toContain('Historical reporting is not connected');
    expect(fixture.nativeElement.textContent).toContain('No figures are shown');
    expect(fixture.nativeElement.querySelector('input[name="from"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('.metric-tile')).toBeFalsy();
    // Explanatory copy (for example, "Indexed to 100") is not a reported value.
    expect(fixture.nativeElement.querySelector('[aria-label="Summary metrics"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('.metric-tile')).toBeNull();
    const visualizations = fixture.nativeElement.querySelector('[aria-label="Portfolio visualizations"]');
    expect(visualizations).not.toBeNull();
    expect(visualizations.querySelector('canvas, table')).toBeNull();
    expect(visualizations.textContent).toContain('Performance data is unavailable');
    expect(visualizations.textContent).toContain('Holdings data is unavailable');
  });

  it('exposes all approved benchmark periods without inventing a chart result', () => {
    const buttons = Array.from(fixture.nativeElement.querySelectorAll('.period-controls button')) as HTMLButtonElement[];
    expect(buttons.map((button) => button.textContent?.trim())).toEqual(['1M', '3M', '12M']);
    buttons[2].click();
    expect(api.portfolioPerformance).toHaveBeenCalledWith(expect.objectContaining({ timezone: 'UTC' }), '12m');
  });

  it('condenses holdings after the fifth largest position into Other', () => {
    const holdings: HoldingAllocation[] = Array.from({ length: 7 }, (_, index) => ({
      symbol: `SYM${index}`,
      display_name: `Holding ${index}`,
      market_value: index + 1,
      percentage: index + 1,
      currency: 'USD',
    }));

    const result = fixture.componentInstance.topHoldings(holdings);
    expect(result).toHaveLength(6);
    expect(result.at(-1)).toMatchObject({ display_name: 'Other', market_value: 3, percentage: 3 });
  });

  it('keeps Other holdings separate when remainder currencies differ', () => {
    const holdings: HoldingAllocation[] = [
      ...Array.from({ length: 5 }, (_, index) => ({ symbol: `USD${index}`, display_name: `USD ${index}`, market_value: 10 - index, percentage: 10 - index, currency: 'USD' })),
      { symbol: 'USD-REMAINDER', display_name: 'USD remainder', market_value: 2, percentage: 2, currency: 'USD' },
      { symbol: 'GBP-REMAINDER', display_name: 'GBP remainder', market_value: 1, percentage: 1, currency: 'GBP' },
    ];

    const result = fixture.componentInstance.topHoldings(holdings);
    expect(result).toHaveLength(7);
    expect(result.slice(-2)).toEqual([
      expect.objectContaining({ display_name: 'Other (USD)', currency: 'USD', market_value: 2 }),
      expect.objectContaining({ display_name: 'Other (GBP)', currency: 'GBP', market_value: 1 }),
    ]);
  });

  it('renders populated metrics with provenance and freshness metadata', () => {
    const metadata: ReportMetadata = {
      reporting_status: 'CURRENT',
      data_mode: 'DEMONSTRATION',
      data_as_of: '2026-10-20T17:00:00Z',
      last_successful_load_at: '2026-10-20T17:05:00Z',
      period_start: '2026-10-01',
      period_end: '2026-10-20',
      reporting_timezone: 'UTC',
      definition_version: '2026-10-01',
      source_watermark: 'event-42',
      warnings: [],
    };
    api.summary.mockReturnValue(of({ filters: fixture.componentInstance.draftFilters, metadata, metrics: [{ key: 'fills_booked', label: 'Booked fills', value: 12, unit: 'count', currency: null, definition: 'Distinct durable fill events.', definition_pending: false, event_date: 'Fill event time', definition_version: '2026-10-01' }] }));
    api.portfolioPerformance.mockReturnValue(of({ filters: fixture.componentInstance.draftFilters, metadata, period: '1m', points: [] }));
    api.portfolioHoldings.mockReturnValue(of({ filters: fixture.componentInstance.draftFilters, metadata, holdings: [], total_market_value: '0', currency: 'USD' }));
    api.instruments.mockReturnValue(of({ filters: fixture.componentInstance.draftFilters, metadata, measure: 'fills_booked', page: 1, page_size: 5, total: 0, rows: [] }));
    api.freshness.mockReturnValue(of({ metadata, reconciliation_status: 'OK', quality_exceptions: 0 }));

    fixture.componentInstance.load();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Current');
    expect(fixture.nativeElement.textContent).toContain('event-42');
    expect(fixture.nativeElement.textContent).toContain('Booked fills');
    expect(fixture.nativeElement.textContent).toContain('Fill event time');
  });

  it('keeps delayed and partial statuses visible without converting them to current', () => {
    const metadata: ReportMetadata = {
      reporting_status: 'DATA_DELAYED',
      data_mode: 'DEMONSTRATION',
      data_as_of: '2026-10-18T17:00:00Z',
      last_successful_load_at: '2026-10-18T17:05:00Z',
      period_start: '2026-10-01',
      period_end: '2026-10-20',
      reporting_timezone: 'UTC',
      definition_version: '2026-10-01',
      source_watermark: 'event-40',
      warnings: ['Reporting data is two days behind.'],
    };
    api.summary.mockReturnValue(of({ filters: fixture.componentInstance.draftFilters, metadata, metrics: [] }));
    api.portfolioPerformance.mockReturnValue(of({ filters: fixture.componentInstance.draftFilters, metadata, period: '1m', points: [] }));
    api.portfolioHoldings.mockReturnValue(of({ filters: fixture.componentInstance.draftFilters, metadata, holdings: [], total_market_value: '0', currency: 'USD' }));
    api.instruments.mockReturnValue(of({ filters: fixture.componentInstance.draftFilters, metadata, measure: 'fills_booked', page: 1, page_size: 5, total: 0, rows: [] }));
    api.freshness.mockReturnValue(of({ metadata: { ...metadata, reporting_status: 'PARTIAL' }, reconciliation_status: 'PARTIAL', quality_exceptions: 1 }));

    fixture.componentInstance.load();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Partial report');
    expect(fixture.nativeElement.textContent).toContain('Reporting data is two days behind.');
    expect(fixture.nativeElement.textContent).toContain('No activity for these filters.');
  });
});

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AnalyticsApiService } from '../../core/api/analytics-api.service';
import { HoldingAllocation } from '../../core/api/analytics-api.service';
import { AnalyticsPageComponent } from './analytics-page.component';

describe('AnalyticsPageComponent', () => {
  let fixture: ComponentFixture<AnalyticsPageComponent>;
  const api = { summary: vi.fn(), trends: vi.fn(), instruments: vi.fn(), portfolioPerformance: vi.fn(), portfolioHoldings: vi.fn() };
  beforeEach(async () => {
    api.summary.mockReturnValue(throwError(() => ({ status: 503, error: { detail: 'Reporting source unavailable.' } })));
    api.portfolioPerformance.mockReturnValue(throwError(() => ({ status: 503, error: { detail: 'Reporting source unavailable.' } })));
    api.portfolioHoldings.mockReturnValue(throwError(() => ({ status: 503, error: { detail: 'Reporting source unavailable.' } })));
    await TestBed.configureTestingModule({ imports: [AnalyticsPageComponent], providers: [{ provide: AnalyticsApiService, useValue: api }, { provide: ActivatedRoute, useValue: { data: of({ analyticsView: 'overview', title: 'Analytics overview', description: 'Aggregate activity.' }), queryParamMap: of(convertToParamMap({})) } }] }).compileComponents();
    fixture = TestBed.createComponent(AnalyticsPageComponent);
    fixture.detectChanges();
  });
  it('keeps filters visible and labels an unavailable report without invented values', () => {
    expect(fixture.nativeElement.textContent).toContain('Historical reporting is not connected');
    expect(fixture.nativeElement.textContent).toContain('No figures are shown');
    expect(fixture.nativeElement.querySelector('input[name="from"]')).toBeTruthy();
    expect(fixture.nativeElement.textContent).not.toContain('0');
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
});

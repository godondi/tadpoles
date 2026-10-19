import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AnalyticsApiService } from '../../core/api/analytics-api.service';
import { AnalyticsPageComponent } from './analytics-page.component';

describe('AnalyticsPageComponent', () => {
  let fixture: ComponentFixture<AnalyticsPageComponent>;
  const api = { summary: vi.fn(), trends: vi.fn(), instruments: vi.fn() };
  beforeEach(async () => {
    api.summary.mockReturnValue(throwError(() => ({ status: 503, error: { detail: 'Reporting source unavailable.' } })));
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
});

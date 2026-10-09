import { TestBed } from '@angular/core/testing';
import { CandleChartComponent } from './candle-chart.component';

describe('Daily candle chart', () => {
  it('keeps flat prices finite and provides accessible OHLC data', () => {
    const fixture = TestBed.createComponent(CandleChartComponent);
    fixture.componentRef.setInput('symbol', 'FLAT');
    fixture.componentRef.setInput('currency', 'EUR');
    fixture.componentRef.setInput('candles', [
      {
        date: '2026-10-08',
        open: 5,
        high: 5,
        low: 5,
        close: 5,
        adjclose: 5,
        volume: null,
        synthetic: true,
      },
    ]);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.innerHTML).not.toMatch(/NaN|Infinity/);
    const body = el.querySelector('svg rect')!;
    expect(Number(body.getAttribute('height'))).toBeGreaterThan(0);
    expect(el.querySelector('svg')!.getAttribute('aria-label')).toContain('EUR');
    expect(el.querySelector('table')!.textContent).toContain('Synthetic');
    expect(el.querySelector('table')!.textContent).toContain('—');
  });
  it('maps wick and body coordinates to OHLC prices and distinguishes down days', () => {
    const fixture = TestBed.createComponent(CandleChartComponent);
    fixture.componentRef.setInput('symbol', 'TEST');
    fixture.componentRef.setInput('currency', 'USD');
    fixture.componentRef.setInput('candles', [
      {
        date: '2026-10-08',
        open: 10,
        high: 15,
        low: 5,
        close: 8,
        adjclose: 8,
        volume: 10,
        synthetic: false,
      },
    ]);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const body = el.querySelector('svg rect')!;
    const wick = el.querySelector('[data-candle] line')!;
    const top = Number(body.getAttribute('y'));
    const bottom = top + Number(body.getAttribute('height'));
    expect(Number(wick.getAttribute('y1'))).toBeLessThan(top);
    expect(Number(wick.getAttribute('y2'))).toBeGreaterThan(bottom);
    expect(el.querySelector('[data-candle]')!.classList.contains('down')).toBe(true);
  });
});

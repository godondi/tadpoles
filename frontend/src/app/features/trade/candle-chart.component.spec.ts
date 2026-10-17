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
    expect(el.querySelector('[role="img"]')!.getAttribute('aria-label')).toContain('EUR');
    expect(el.querySelector('[data-candle]')!.textContent).toContain('5.00');
    expect(el.querySelector('table')!.textContent).toContain('Synthetic');
    expect(el.querySelector('table')!.textContent).toContain('—');
  });
  it('keeps exact daily OHLC values accessible independently of canvas rendering', () => {
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
    const values = [...el.querySelectorAll('[data-candle] td')].map((cell) =>
      cell.textContent?.trim(),
    );
    expect(values.slice(0, 4)).toEqual(['10.00', '15.00', '5.00', '8.00']);
    expect(values[5]).toBe('Stored');
    fixture.destroy();
  });
});

import {
  Component,
  ElementRef,
  afterNextRender,
  effect,
  inject,
  input,
  signal,
  viewChild,
  DestroyRef,
} from '@angular/core';
import { DecimalPipe } from '@angular/common';
import type { IChartApi, ISeriesApi, CandlestickData, Time } from 'lightweight-charts';
import { Candle } from '../../core/api/market-data.service';

@Component({
  selector: 'app-candle-chart',
  imports: [DecimalPipe],
  template: `
    <div
      #canvas
      class="daily-canvas"
      role="img"
      [attr.aria-label]="
        symbol() +
        ' daily OHLC chart in ' +
        currency() +
        '. Daily values are available in the table below.'
      "
    ></div>
    @if (unavailable()) {
      <p class="notice">Interactive chart unavailable. Use the daily price table below.</p>
    }
    <p class="muted">
      Daily, unadjusted OHLC · pan, zoom and crosshair · chart by
      <a href="https://www.tradingview.com/" target="_blank" rel="noopener noreferrer"
        >TradingView</a
      >
    </p>
    <details>
      <summary>View daily price data</summary>
      <div class="table-wrapper">
        <table>
          <caption>
            {{
              symbol()
            }}
            daily OHLC prices in
            {{
              currency()
            }}
          </caption>
          <thead>
            <tr>
              <th>Date</th>
              <th>Open</th>
              <th>High</th>
              <th>Low</th>
              <th>Close</th>
              <th>Volume</th>
              <th>Data</th>
            </tr>
          </thead>
          <tbody>
            @for (candle of candles(); track candle.date) {
              <tr [attr.data-candle]="candle.date">
                <th scope="row">{{ candle.date }}</th>
                <td>{{ candle.open | number: '1.2-2' }}</td>
                <td>{{ candle.high | number: '1.2-2' }}</td>
                <td>{{ candle.low | number: '1.2-2' }}</td>
                <td>{{ candle.close | number: '1.2-2' }}</td>
                <td>{{ candle.volume === null ? '—' : (candle.volume | number) }}</td>
                <td>
                  {{
                    candle.synthetic === true
                      ? 'Synthetic'
                      : candle.synthetic === false
                        ? 'Stored'
                        : 'Unknown provenance'
                  }}
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    </details>
  `,
  styles: `
    :host {
      display: block;
    }
    .daily-canvas {
      height: 360px;
      min-width: 0;
    }
    summary {
      cursor: pointer;
      margin: 24px 0 12px;
    }
    table {
      width: 100%;
      border-collapse: collapse;
      font-size: 0.85rem;
    }
    th,
    td {
      padding: 10px;
      text-align: right;
      white-space: nowrap;
      border-bottom: 1px solid var(--border-gray);
    }
    caption {
      text-align: left;
    }
  `,
})
export class CandleChartComponent {
  readonly candles = input.required<Candle[]>();
  readonly symbol = input.required<string>();
  readonly currency = input.required<string>();
  readonly unavailable = signal(false);
  private readonly canvas = viewChild<ElementRef<HTMLElement>>('canvas');
  private chart?: IChartApi;
  private series?: ISeriesApi<'Candlestick'>;
  private destroyed = false;
  private theme?: MutationObserver;

  constructor() {
    effect(() => {
      this.candles();
      this.draw();
    });
    afterNextRender(() => {
      void this.initialize();
    });
    inject(DestroyRef).onDestroy(() => {
      this.destroyed = true;
      this.theme?.disconnect();
      this.chart?.remove();
    });
  }

  private async initialize() {
    const host = this.canvas()?.nativeElement;
    if (!host || typeof ResizeObserver === 'undefined') {
      this.unavailable.set(true);
      return;
    }
    try {
      const library = await import('lightweight-charts');
      if (this.destroyed) return;
      this.chart = library.createChart(host, {
        autoSize: true,
        layout: { attributionLogo: true },
        timeScale: { timeVisible: false },
        rightPriceScale: { borderVisible: false },
      });
      this.series = this.chart.addSeries(library.CandlestickSeries, {
        upColor: '#216b4a',
        downColor: '#a72c3d',
        wickUpColor: '#216b4a',
        wickDownColor: '#a72c3d',
        borderVisible: false,
      });
      const applyTheme = () => {
        const style = getComputedStyle(host);
        this.chart?.applyOptions({
          layout: {
            background: {
              type: library.ColorType.Solid,
              color: style.getPropertyValue('--surface').trim() || '#ffffff',
            },
            textColor: style.getPropertyValue('--text-primary').trim() || '#172b23',
          },
          grid: {
            vertLines: { visible: false },
            horzLines: { color: style.getPropertyValue('--border-gray').trim() || '#dbe1dd' },
          },
        });
      };
      applyTheme();
      this.theme = new MutationObserver(applyTheme);
      this.theme.observe(document.documentElement, {
        attributes: true,
        attributeFilter: ['class', 'data-theme', 'style'],
      });
      this.draw();
    } catch {
      this.unavailable.set(true);
      this.chart?.remove();
      this.chart = undefined;
    }
  }

  private draw() {
    if (!this.series) return;
    this.series.setData(
      this.candles().map((c) => ({
        time: c.date,
        open: c.open,
        high: c.high,
        low: c.low,
        close: c.close,
      })) as CandlestickData<Time>[],
    );
    this.chart?.timeScale().fitContent();
  }
}

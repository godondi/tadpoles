import { Component, computed, input } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { Candle } from '../../core/api/market-data.service';

@Component({
  selector: 'app-candle-chart',
  imports: [DecimalPipe],
  template: `
    <svg
      viewBox="0 0 800 360"
      role="img"
      [attr.aria-label]="
        symbol() +
        ' daily OHLC candlestick chart, ' +
        candles().length +
        ' sessions in ' +
        currency()
      "
    >
      <title>{{ symbol() }} daily prices ({{ currency() }})</title>
      <desc>
        Each candle shows the open, high, low and close. Filled candles close higher; outlined
        candles close lower. Daily values are available in the table below.
      </desc>
      @for (tick of ticks(); track tick.y) {
        <line x1="10" x2="725" [attr.y1]="tick.y" [attr.y2]="tick.y" class="grid" />
        <text x="738" [attr.y]="tick.y + 4">{{ tick.price | number: '1.2-2' }}</text>
      }
      @for (bar of bars(); track bar.candle.date) {
        <g
          [attr.data-candle]="bar.candle.date"
          [class.up]="bar.candle.close >= bar.candle.open"
          [class.down]="bar.candle.close < bar.candle.open"
        >
          <title>
            {{ bar.candle.date }} · Open {{ bar.candle.open }} · High {{ bar.candle.high }} · Low
            {{ bar.candle.low }} · Close {{ bar.candle.close }} {{ currency() }}
          </title>
          <line [attr.x1]="bar.x" [attr.x2]="bar.x" [attr.y1]="bar.high" [attr.y2]="bar.low" />
          <rect
            [attr.x]="bar.x - bar.width / 2"
            [attr.y]="bar.top"
            [attr.width]="bar.width"
            [attr.height]="bar.height"
          />
        </g>
      }
      <text x="10" y="348">{{ candles()[0]?.date }}</text>
      <text x="725" y="348" text-anchor="end">{{ candles()[candles().length - 1]?.date }}</text>
    </svg>
    <p class="legend">
      <span>● Up day</span><span>○ Down day</span><span>Prices in {{ currency() }}</span>
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
              <tr>
                <th scope="row">{{ candle.date }}</th>
                <td>{{ candle.open | number: '1.2-2' }}</td>
                <td>{{ candle.high | number: '1.2-2' }}</td>
                <td>{{ candle.low | number: '1.2-2' }}</td>
                <td>{{ candle.close | number: '1.2-2' }}</td>
                <td>{{ candle.volume === null ? '—' : (candle.volume | number) }}</td>
                <td>{{ candle.synthetic ? 'Synthetic' : 'Stored' }}</td>
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
    svg {
      display: block;
      width: 100%;
      height: auto;
    }
    svg text {
      font:
        12px Arial,
        sans-serif;
      fill: var(--text-secondary);
    }
    .grid {
      stroke: var(--border-gray);
      stroke-dasharray: 3 5;
    }
    .up {
      stroke: var(--dark-green);
      fill: var(--dark-green);
    }
    .down {
      stroke: var(--negative);
      fill: var(--surface);
    }
    g line,
    g rect {
      stroke-width: 1.5;
    }
    .legend {
      display: flex;
      flex-wrap: wrap;
      gap: 20px;
      font-size: 0.8rem;
      color: var(--text-secondary);
    }
    summary {
      cursor: pointer;
      margin: 24px 0 12px;
    }
    table {
      border-collapse: collapse;
      width: 100%;
      font-size: 0.8rem;
    }
    th,
    td {
      padding: 10px;
      text-align: right;
      border-bottom: 1px solid var(--border-gray);
      white-space: nowrap;
    }
    caption {
      text-align: left;
      padding: 10px;
    }
  `,
})
export class CandleChartComponent {
  readonly candles = input.required<Candle[]>();
  readonly symbol = input.required<string>();
  readonly currency = input.required<string>();
  private readonly scale = computed(() => {
    const rows = this.candles();
    const low = Math.min(...rows.map((c) => c.low));
    const high = Math.max(...rows.map((c) => c.high));
    const padding = Math.max((high - low) * 0.12, Math.abs(high) * 0.005, 0.01);
    return { min: low - padding, max: high + padding };
  });
  private y(price: number) {
    const { min, max } = this.scale();
    return 20 + ((max - price) / (max - min)) * 290;
  }
  readonly ticks = computed(() => {
    const { min, max } = this.scale();
    return Array.from({ length: 5 }, (_, i) => ({
      y: 20 + i * 72.5,
      price: max - ((max - min) * i) / 4,
    }));
  });
  readonly bars = computed(() =>
    this.candles().map((candle, i, rows) => ({
      candle,
      x: 10 + ((i + 0.5) * 715) / rows.length,
      width: Math.min(14, (715 / rows.length) * 0.6),
      high: this.y(candle.high),
      low: this.y(candle.low),
      top: this.y(Math.max(candle.open, candle.close)),
      height: Math.max(1, Math.abs(this.y(candle.open) - this.y(candle.close))),
    })),
  );
}

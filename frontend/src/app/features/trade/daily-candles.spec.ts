import { dailyCandles } from './daily-candles';
import { CandlesResponse, Candle } from '../../core/api/market-data.service';
const row: Candle = {
  date: '2026-10-01',
  open: 10,
  high: 12,
  low: 9,
  close: 11,
  adjclose: 5,
  volume: 100,
  synthetic: false,
};
function response(rows: Candle[]): CandlesResponse {
  return {
    data: { symbol: 'AAPL', interval: '1d', currency: 'USD', candles: rows },
    meta: {
      symbol: 'AAPL',
      source: 'cache',
      asOf: '2026-10-02T00:00:00Z',
      disclaimer: 'Educational',
    },
  };
}
describe('daily provider candle validation', () => {
  it('deduplicates identical dates and preserves unadjusted OHLC and session dates', () => {
    const result = dailyCandles(response([row, { ...row, date: '2026-09-30' }, row]), 'AAPL');
    expect(result.map((r) => r.date)).toEqual(['2026-09-30', '2026-10-01']);
    expect(result[1].close).toBe(11);
  });
  it.each([
    { ...row, date: '2026-02-30' },
    { ...row, high: 8 },
    { ...row, low: 13 },
    { ...row, close: NaN },
    { ...row, volume: -1 },
  ])('rejects invalid bars instead of drawing fabricated values', (bad) => {
    expect(() => dailyCandles(response([bad]), 'AAPL')).toThrow();
  });
  it('rejects conflicting duplicates and another symbol', () => {
    expect(() => dailyCandles(response([row, { ...row, close: 10 }]), 'AAPL')).toThrow();
    expect(() => dailyCandles(response([row]), 'MSFT')).toThrow();
  });
  it('rejects missing price currency', () => {
    const value = response([row]);
    value.data.currency = '';
    expect(() => dailyCandles(value, 'AAPL')).toThrow();
  });
  it('renders synthetic bars whose unavailable volume was omitted by the backend', () => {
    const { volume, ...withoutVolume } = row;
    const synthetic = { ...withoutVolume, synthetic: true };
    const result = dailyCandles(response([synthetic, { ...synthetic, volume: null }]), 'AAPL');
    expect(result).toEqual([{ ...synthetic, volume: null }]);
  });
});

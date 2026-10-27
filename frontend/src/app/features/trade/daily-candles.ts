import { Candle, CandlesResponse } from '../../core/api/market-data.service';
/** Chart-only numbers. These values never enter order accounting. */
export function dailyCandles(response: CandlesResponse, symbol: string): Candle[] {
  if (
    response.data?.symbol !== symbol ||
    response.meta?.symbol !== symbol ||
    response.data.interval !== '1d' ||
    typeof response.data.currency !== 'string' ||
    !/^[A-Z]{3}$/.test(response.data.currency) ||
    !Array.isArray(response.data.candles)
  )
    throw Error('Daily data does not match this instrument.');
  const dates = new Map<string, Candle>();
  for (const row of response.data.candles) {
    if (
      !/^\d{4}-\d{2}-\d{2}$/.test(row.date) ||
      !Number.isFinite(Date.parse(row.date + 'T00:00:00Z')) ||
      new Date(row.date + 'T00:00:00Z').toISOString().slice(0, 10) !== row.date ||
      ![row.open, row.high, row.low, row.close].every(
        (v) => typeof v === 'number' && Number.isFinite(v) && v > 0,
      ) ||
      row.high < Math.max(row.open, row.close, row.low) ||
      row.low > Math.min(row.open, row.close, row.high) ||
      (row.volume != null && (!Number.isFinite(row.volume) || row.volume < 0))
    )
      throw Error(
        'Provider returned an invalid daily bar. Chart withheld; quote checks are independent.',
      );
    // NON_NULL backend DTOs omit unavailable volume (e.g. synthetic daily bars).
    const candle = { ...row, volume: row.volume ?? null };
    const prior = dates.get(row.date);
    if (
      prior &&
      ['open', 'high', 'low', 'close', 'adjclose', 'volume', 'synthetic'].some(
        (k) => prior[k as keyof Candle] !== candle[k as keyof Candle],
      )
    )
      throw Error('Provider returned conflicting bars for one date.');
    dates.set(row.date, candle);
  }
  return [...dates.values()].sort((a, b) => a.date.localeCompare(b.date));
}

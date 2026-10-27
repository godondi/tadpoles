import { utcFilter, utcFilters } from './utc-filter';

describe('UTC filter controls', () => {
  it('uses UTC, including DST gaps and seconds precision', () => {
    expect(utcFilter('2026-03-08T02:30')).toBe('2026-03-08T02:30:00.000Z');
    expect(utcFilter('2026-11-01T01:30:12.123')).toBe('2026-11-01T01:30:12.123Z');
  });
  it.each([
    '2026-02-29T12:00',
    '2026-04-31T12:00',
    '2026-01-01T24:00',
    '2026-01-01T12:60',
    '2026-01-01',
    'bad',
    '2026-01-01T12:00Z',
  ])('rejects %s', (value) => {
    expect(() => utcFilter(value)).toThrow();
  });
  it('permits optional bounds and exactly 31 days but rejects one millisecond beyond', () => {
    expect(utcFilters('', '')).toEqual({});
    expect(utcFilters('2026-01-01T00:00', '')).toEqual({ from: '2026-01-01T00:00:00.000Z' });
    expect(utcFilters('', '2026-02-01T00:00')).toEqual({ to: '2026-02-01T00:00:00.000Z' });
    expect(() => utcFilters('2026-01-01T00:00', '2026-02-01T00:00')).not.toThrow();
    expect(() => utcFilters('2026-01-01T00:00', '2026-02-01T00:00:00.001')).toThrow();
    expect(() => utcFilters('2026-01-01T00:00', '2026-01-01T00:00')).toThrow();
  });
});

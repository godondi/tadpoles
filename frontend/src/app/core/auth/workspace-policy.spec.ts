import { landingPage, normalizeRoles, safeReturnUrl } from './workspace-policy';
describe('workspace routing policy', () => {
  it('recognizes exact role collections only', () => {
    expect(normalizeRoles('CLIENT')).toEqual([]);
    expect(normalizeRoles(['CLIENT', 'OPERATIONS', 'finance', null, 4])).toEqual([]);
    expect(normalizeRoles(['CLIENT', 'OPERATIONS'])).toEqual(['CLIENT']);
  });
  it('uses documented multi-role priority', () =>
    expect(landingPage(['FINANCE', 'CLIENT', 'TRADING_OPS'])).toBe('/app/overview'));
  it.each([
    '//evil.test',
    'https://evil.test',
    '/login',
    '/app/../finance/overview',
    '/app/%2e%2e/finance/overview',
    '/app/orders;admin=true',
    '/finance/overview',
    '/app/orders\\evil',
    '/app/orders/abc',
  ])('rejects unsupported return %s', (url) =>
    expect(safeReturnUrl(url, ['CLIENT'])).toBe('/app/overview'),
  );
  it('preserves an authorized filtered detail URL', () =>
    expect(safeReturnUrl('/app/orders/21?from=2026-01-01', ['CLIENT'])).toBe(
      '/app/orders/21?from=2026-01-01',
    ));
  it('unknown roles get access denied', () =>
    expect(landingPage(['OPERATIONS'])).toBe('/unauthorized'));
});

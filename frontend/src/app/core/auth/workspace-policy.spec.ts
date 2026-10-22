import { landingPage, normalizeRoles, safeReturnUrl, routeRole } from './workspace-policy';
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

describe('implemented staff landing pages', () => {
  it('opens real Operations and Finance workflows after login', () => {
    expect(landingPage(['TRADING_OPS'])).toBe('/operations/overview');
    expect(landingPage(['FINANCE'])).toBe('/finance/reconciliation');
  });
});

describe('Advisor workspace policy', () => {
  it('recognizes the Advisor role and lands at the assigned-client workspace', () => {
    expect(normalizeRoles(['ADVISOR'])).toEqual(['ADVISOR']);
    expect(landingPage(['ADVISOR'])).toBe('/advisor/clients');
  });

  it.each(['/advisor/clients', '/advisor/clients/7?tab=recommendations'])('recognizes Advisor route %s', (path) => {
    expect(routeRole(path)).toBe('ADVISOR');
    expect(safeReturnUrl(path, ['ADVISOR'])).toBe(path);
  });

  it.each(['/advisor/clients/0', '/advisor/clients/-1', '/advisor/clients/1.5', '/advisor/clients/nope'])(
    'rejects invalid Advisor client detail route %s',
    (path) => expect(routeRole(path)).toBeNull(),
  );

  it('does not authorize Advisor links for another role', () => {
    expect(safeReturnUrl('/advisor/clients', ['CLIENT'])).toBe('/app/overview');
  });
});

describe('Operations instrument editor routes', () => {
  it.each(['/operations/instruments/new', '/operations/instruments/42'])(
    'allows the editor only for an unmixed Operations identity: %s',
    (path) => {
      expect(safeReturnUrl(path, ['TRADING_OPS'])).toBe(path);
      expect(safeReturnUrl(path, ['TRADING_OPS', 'CLIENT'])).toBe('/app/overview');
      expect(safeReturnUrl(path, ['CLIENT'])).toBe('/app/overview');
    },
  );
});

it.each([
  '/operations/overview',
  '/operations/orders',
  '/operations/orders/12',
  '/operations/exceptions',
  '/operations/reconciliation',
])('denies mixed client/staff return URLs for %s', (path) => {
  expect(routeRole(path)).toBe('TRADING_OPS');
  expect(safeReturnUrl(path, ['CLIENT', 'TRADING_OPS'])).toBe('/app/overview');
  expect(safeReturnUrl(path, ['TRADING_OPS'])).toBe(path);
});

it('preserves owned case direct links and excludes mixed identities from staff cases', () => {
  const reference = 'CASE-11111111-1111-4111-8111-111111111111';
  for (const url of ['/app/support', '/app/support/new?orderId=12', '/app/support/' + reference]) {
    expect(safeReturnUrl(url, ['CLIENT'])).toBe(url);
  }
  for (const url of ['/operations/cases', '/operations/cases/' + reference]) {
    expect(safeReturnUrl(url, ['TRADING_OPS'])).toBe(url);
    expect(safeReturnUrl(url, ['TRADING_OPS', 'CLIENT'])).toBe('/app/overview');
  }
});

export const WORKSPACES = [
  {
    role: 'CLIENT',
    label: 'My account',
    home: '/app/overview',
    links: [
      ['Overview', '/app/overview'],
      ['Markets', '/app/markets'],
      ['Portfolio', '/app/portfolio'],
      ['Orders', '/app/orders'],
    ],
  },
  {
    role: 'TRADING_OPS',
    label: 'Trading operations',
    home: '/operations/orders',
    links: [['Orders & monitoring', '/operations/orders']],
  },
  {
    role: 'COMPLIANCE',
    label: 'Risk & compliance',
    home: '/compliance/investigations',
    links: [
      ['Investigations', '/compliance/investigations'],
      ['Evidence & exports', '/compliance/evidence'],
    ],
  },
  {
    role: 'ANALYST',
    label: 'Business analytics',
    home: '/analytics/overview',
    links: [
      ['Overview', '/analytics/overview'],
      ['Activity trends', '/analytics/trends'],
      ['Instrument analysis', '/analytics/instruments'],
    ],
  },
  {
    role: 'FINANCE',
    label: 'Finance',
    home: '/finance/overview',
    links: [
      ['Overview', '/finance/overview'],
      ['Reconciliation', '/finance/reconciliation'],
    ],
  },
] as const;
export type WorkspaceRole = (typeof WORKSPACES)[number]['role'];
export function normalizeRoles(value: unknown): WorkspaceRole[] {
  return Array.isArray(value) && value.every((role) => typeof role === 'string')
    ? WORKSPACES.map((w) => w.role).filter((role) => value.includes(role))
    : [];
}
export function landingPage(roles: readonly string[]): string {
  return WORKSPACES.find((w) => roles.includes(w.role))?.home ?? '/unauthorized';
}
export function routeRole(url: string): WorkspaceRole | null {
  const path = url.split(/[?#]/)[0];
  if (
    /^\/(app\/(overview|markets|portfolio|orders(?:\/\d+)?|instruments\/\d+|trade\/\d+)|profile|onboarding|trades|trade\/[A-Za-z0-9.^=-]+)$/.test(
      path,
    )
  )
    return 'CLIENT';
  if (/^\/operations\/orders(?:\/\d+)?$/.test(path)) return 'TRADING_OPS';
  if (/^\/compliance\/(investigations(?:\/\d+)?|evidence)$/.test(path)) return 'COMPLIANCE';
  if (/^\/analytics\/(overview|trends|instruments)$/.test(path)) return 'ANALYST';
  if (/^\/finance\/(overview|reconciliation)$/.test(path)) return 'FINANCE';
  return null;
}
export function safeReturnUrl(value: string | null, roles: readonly string[]): string {
  if (
    !value ||
    !value.startsWith('/') ||
    value.startsWith('//') ||
    /[\\\s%;]/.test(value.split('?')[0])
  )
    return landingPage(roles);
  const role = routeRole(value);
  return role && roles.includes(role) ? value : landingPage(roles);
}

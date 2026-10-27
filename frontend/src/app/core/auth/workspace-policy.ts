export const WORKSPACES = [
  {
    role: 'CLIENT',
    label: 'My account',
    home: '/app/overview',
    links: [
      ['Overview', '/app/overview'],
      ['Markets', '/app/markets'],
      ['Orders', '/app/orders'],
      ['Support', '/app/support'],
      ['Banking & cash', '/app/banking'],
    ],
  },
  {
    role: 'TRADING_OPS',
    label: 'Trading operations',
    home: '/operations/overview',
    links: [
      ['Overview', '/operations/overview'],
      ['Support cases', '/operations/cases'],
      ['Exception queue', '/operations/exceptions'],
      ['Reconciliation', '/operations/reconciliation'],
      ['Orders & monitoring', '/operations/orders'],
      ['Instruments', '/operations/instruments'],
      ['Add instrument', '/operations/instruments/new'],
      ['User access', '/operations/users'],
      ['Create account', '/operations/users/create'],
    ],
  },
  {
    role: 'COMPLIANCE',
    label: 'Risk & compliance',
    home: '/compliance/investigations',
    links: [
      ['Review cases', '/compliance/cases'],
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
    home: '/finance/reconciliation',
    links: [
      ['Overview', '/finance/overview'],
      ['Reconciliation', '/finance/reconciliation'],
    ],
  },
  {
    role: 'ADVISOR',
    label: 'Advisor workspace',
    home: '/advisor/clients',
    links: [['Assigned clients', '/advisor/clients']],
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
    /^\/(app\/(overview|markets|portfolio|banking|orders(?:\/\d+)?|instruments\/\d+|trade\/\d+)|profile|onboarding|trades|trade\/[A-Za-z0-9.^=-]+)$/.test(
      path,
    )
  )
    return 'CLIENT';
  if (/^\/app\/support(?:\/(?:new|CASE-[0-9a-f-]{36}))?$/.test(path)) return 'CLIENT';
  if (/^\/operations\/cases(?:\/CASE-[0-9a-f-]{36})?$/.test(path)) return 'TRADING_OPS';
  if (isInstrumentManagementRoute(path)) return 'TRADING_OPS';
  if (/^\/operations\/(orders(?:\/\d+)?|overview|exceptions|reconciliation)$/.test(path))
    return 'TRADING_OPS';
  if (/^\/operations\/users(?:\/create)?$/.test(path)) return 'TRADING_OPS';
  if (/^\/compliance\/(investigations(?:\/\d+)?|evidence)$/.test(path)) return 'COMPLIANCE';
  if (/^\/compliance\/cases(?:\/REVIEW-[0-9a-f-]{36})?$/.test(path)) return 'COMPLIANCE';
  if (/^\/analytics\/(overview|trends|instruments)$/.test(path)) return 'ANALYST';
  if (/^\/finance\/(overview|reconciliation)$/.test(path)) return 'FINANCE';
  if (/^\/advisor\/clients(?:\/[1-9]\d*)?$/.test(path)) return 'ADVISOR';
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
  if (routeRole(value) === 'TRADING_OPS' && roles.includes('CLIENT')) return landingPage(roles);
  const role = routeRole(value);
  return role && roles.includes(role) ? value : landingPage(roles);
}

export function isInstrumentManagementRoute(url: string): boolean {
  return /^\/operations\/instruments(?:\/(?:new|[1-9]\d*))?$/.test(url.split(/[?#]/)[0]);
}

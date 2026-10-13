export const WORKSPACE_ROLES = ['CLIENT', 'TRADING_OPS', 'COMPLIANCE', 'ANALYST', 'FINANCE'] as const;

export type WorkspaceRole = (typeof WORKSPACE_ROLES)[number];

const LEGACY_ROLE_ALIASES: Record<string, WorkspaceRole> = {
  OPERATIONS: 'TRADING_OPS',
  REPORTING: 'ANALYST',
};

const WORKSPACE_ROLE_SET = new Set<string>(WORKSPACE_ROLES);

const LANDING_ROUTE_BY_ROLE: Record<WorkspaceRole, string> = {
  CLIENT: '/app/overview',
  TRADING_OPS: '/operations/orders',
  COMPLIANCE: '/compliance/investigations',
  ANALYST: '/analytics/overview',
  FINANCE: '/finance/overview',
};

const ROLE_PRIORITY: WorkspaceRole[] = ['CLIENT', 'TRADING_OPS', 'COMPLIANCE', 'ANALYST', 'FINANCE'];

export function normalizeRole(role: string): string {
  const normalized = role.trim().toUpperCase();
  return LEGACY_ROLE_ALIASES[normalized] ?? normalized;
}

export function toWorkspaceRoles(roles: string[]): WorkspaceRole[] {
  const unique = new Set<WorkspaceRole>();
  roles.map(normalizeRole).forEach((role) => {
    if (WORKSPACE_ROLE_SET.has(role)) {
      unique.add(role as WorkspaceRole);
    }
  });
  return Array.from(unique);
}

export function resolveWorkspaceLandingRoute(roles: WorkspaceRole[]): string | null {
  for (const role of ROLE_PRIORITY) {
    if (roles.includes(role)) {
      return LANDING_ROUTE_BY_ROLE[role];
    }
  }
  return null;
}

export function getLandingRouteForRole(role: WorkspaceRole): string {
  return LANDING_ROUTE_BY_ROLE[role];
}

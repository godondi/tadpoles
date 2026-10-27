export interface ExportRecovery {
  actor: string;
  order: number;
  action: 'EXPORT_CREATE';
  key: string;
  reference?: string;
}

const prefix = 'tadpoles.compliance.export.v1.';
function storageKey(actor: string, order: number) {
  if (!/^[1-9]\d*$/.test(actor) || !Number.isSafeInteger(order) || order <= 0)
    throw new Error('A verified actor and order are required for export recovery.');
  return `${prefix}${actor}.${order}`;
}

export function readExportRecovery(actor: string, order: number): ExportRecovery | null {
  try {
    const stored = sessionStorage.getItem(storageKey(actor, order));
    if (stored === null) return null;
    const value = JSON.parse(stored);
    if (!(
      value?.actor === actor &&
      value?.order === order &&
      value?.action === 'EXPORT_CREATE' &&
      typeof value.key === 'string' &&
      /^[A-Za-z0-9._-]{8,80}$/.test(value.key) &&
      (value.reference === undefined || /^[a-f0-9-]{36}$/i.test(value.reference))
    ))
      throw new Error('Invalid export recovery metadata');
    return {
      actor,
      order,
      action: 'EXPORT_CREATE',
      key: value.key,
      ...(value.reference ? { reference: value.reference } : {}),
    };
  } catch {
    throw new Error(
      'Export recovery metadata is unavailable or invalid. No replacement request key will be generated.',
    );
  }
}

export function saveExportRecovery(value: ExportRecovery): void {
  sessionStorage.setItem(
    storageKey(value.actor, value.order),
    JSON.stringify({
      actor: value.actor,
      order: value.order,
      action: value.action,
      key: value.key,
      ...(value.reference ? { reference: value.reference } : {}),
    }),
  );
}

export function downloadServerArchive(blob: Blob, reference: string): void {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  try {
    link.href = url;
    link.download = `compliance-evidence-${/^[a-f0-9-]{36}$/i.test(reference) ? reference : 'export'}.zip`;
    document.body.appendChild(link);
    link.click();
  } finally {
    link.remove();
    URL.revokeObjectURL(url);
  }
}

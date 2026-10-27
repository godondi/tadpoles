export function hasPositiveDecimal(value: string | null | undefined): boolean {
  if (typeof value !== 'string') return false;
  const normalized = value.trim().replace(/^\+/, '');
  if (!/^(?:\d+(?:\.\d*)?|\.\d+)$/.test(normalized)) return false;
  return normalized.replace(/[.0]/g, '') !== '';
}

export function normalizeReasoning(value: string): string {
  return value.trim();
}

export function validReasoning(value: string): boolean {
  const normalized = normalizeReasoning(value);
  return normalized.length >= 1 && normalized.length <= 2000;
}

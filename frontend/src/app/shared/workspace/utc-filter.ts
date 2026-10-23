/** datetime-local controls labelled UTC must never use the browser's local time zone. */
export function utcFilter(value: string): string | undefined {
  if (!value) return undefined;
  const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2})(\.\d{1,3})?)?$/.exec(value);
  if (!match) throw new Error('Use a valid UTC date and time.');
  const [, year, month, day, hour, minute, seconds = '00', fraction = ''] = match;
  if (+year < 1 || +month < 1 || +month > 12 || +hour > 23 || +minute > 59 || +seconds > 59)
    throw new Error('Use a valid UTC date and time.');
  const iso = `${year}-${month}-${day}T${hour}:${minute}:${seconds}${fraction}Z`;
  const date = new Date(iso);
  if (
    !Number.isFinite(date.getTime()) ||
    date.getUTCFullYear() !== +year ||
    date.getUTCMonth() + 1 !== +month ||
    date.getUTCDate() !== +day
  )
    throw new Error('Use a valid UTC date and time.');
  return date.toISOString();
}

export function utcFilters(from: string, to: string): { from?: string; to?: string } {
  const start = utcFilter(from);
  const end = utcFilter(to);
  if (start && end) {
    const duration = Date.parse(end) - Date.parse(start);
    if (duration <= 0) throw new Error('The UTC end time must be after the start time.');
    if (duration > 31 * 24 * 60 * 60 * 1000)
      throw new Error('The date range cannot exceed 31 days.');
  }
  return { ...(start ? { from: start } : {}), ...(end ? { to: end } : {}) };
}

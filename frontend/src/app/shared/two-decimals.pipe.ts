import { Pipe, PipeTransform } from '@angular/core';

/** Display rounding only; decimal strings never pass through binary floating point. */
export function twoDecimals(value: unknown): string {
  if (value === null || value === undefined) return '—';
  const text = String(value);
  const match = /^([+-]?)(\d+)(?:\.(\d*))?(?:e([+-]?\d+))?$/i.exec(text);
  if (!match) return text;
  const exponent = Number(match[4] ?? 0);
  if (!Number.isSafeInteger(exponent) || Math.abs(exponent) > 100) return text;
  const digits = match[2] + (match[3] ?? '');
  const shift = 2 + exponent - (match[3]?.length ?? 0);
  let cents: bigint;
  if (shift >= 0) cents = BigInt(digits) * 10n ** BigInt(shift);
  else {
    const divisor = 10n ** BigInt(-shift);
    const original = BigInt(digits);
    cents = original / divisor + (original % divisor * 2n >= divisor ? 1n : 0n);
  }
  const whole = (cents / 100n).toString().replace(/\B(?=(\d{3})+(?!\d))/g, ',');
  return `${match[1] === '-' && cents !== 0n ? '-' : ''}${whole}.${String(cents % 100n).padStart(2, '0')}`;
}

/** Preserve IDs, reference strings and counts in mixed operational evidence. */
export function displayFact(value: unknown, key: string): unknown {
  if (value && typeof value === 'object') {
    if (Array.isArray(value)) return value.map(item => displayFact(item, key));
    return Object.fromEntries(Object.entries(value).map(([name, item]) => [name, displayFact(item, name)]));
  }
  const decimal = typeof value === 'number' && !Number.isInteger(value);
  const words = key.replace(/([a-z])([A-Z])/g, '$1 $2').split(/[^a-z]+/i);
  const financial = words.some(word => /^(amount|quantity|price|gross|fee|balance|available|reserved|posted|owned|notional|percentage|weight|cash|value|rate|consideration)$/i.test(word));
  return value !== null && value !== undefined && (decimal || financial) ? twoDecimals(value) : value;
}

@Pipe({ name: 'twoDecimals' })
export class TwoDecimalsPipe implements PipeTransform {
  transform(value: unknown): string { return twoDecimals(value); }
}

@Pipe({ name: 'displayFact' })
export class DisplayFactPipe implements PipeTransform {
  transform(value: unknown, key: string): unknown { return displayFact(value, key); }
}

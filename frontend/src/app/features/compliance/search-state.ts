import { Injectable, inject } from '@angular/core';
import { ParamMap } from '@angular/router';
import { ComplianceApi } from './compliance-api.service';
import { ComplianceSearchFilters } from './compliance.models';
import { utcFilters } from '../../shared/workspace/utc-filter';

export const complianceStatuses = [
  'ACCEPTED',
  'EXECUTING',
  'FILLED',
  'REJECTED',
  'PENDING',
  'APPROVED',
  'EXECUTED',
];
export const emptySearchDraft = () => ({
  orderId: '',
  from: '',
  to: '',
  clientReference: '',
  instrument: '',
  status: '',
});
export type SearchDraft = ReturnType<typeof emptySearchDraft>;

export function validateSearch(draft: SearchDraft): ComplianceSearchFilters {
  const id = draft.orderId.trim();
  if (id && (!/^[1-9]\d*$/.test(id) || Number(id) > 2147483647))
    throw new Error('Order ID must be a positive integer.');
  const reference = draft.clientReference.trim();
  const instrument = draft.instrument.trim();
  for (const value of [reference, instrument])
    if (value && !/^[A-Za-z0-9][A-Za-z0-9._-]{0,79}$/.test(value))
      throw new Error('Use a valid account reference or instrument.');
  if (draft.status && !complianceStatuses.includes(draft.status))
    throw new Error('Choose a supported lifecycle status.');
  return {
    ...utcFilters(draft.from, draft.to),
    ...(id ? { orderId: Number(id) } : {}),
    ...(reference ? { clientReference: reference } : {}),
    ...(instrument ? { instrument } : {}),
    ...(draft.status ? { status: draft.status } : {}),
    page: 0,
    pageSize: 25,
  };
}

export function safeSearchQuery(filters: ComplianceSearchFilters): Record<string, string> {
  return Object.fromEntries(
    ['from', 'to', 'instrument', 'status', 'page', 'pageSize']
      .filter((key) => filters[key as keyof ComplianceSearchFilters] !== undefined)
      .map((key) => [key, String(filters[key as keyof ComplianceSearchFilters])]),
  );
}

export function restoreSearch(params: ParamMap): ComplianceSearchFilters {
  const draft = emptySearchDraft();
  for (const key of ['instrument', 'status'] as const) draft[key] = params.get(key) ?? '';
  for (const key of ['from', 'to'] as const) {
    const value = params.get(key) ?? '';
    draft[key] = value.endsWith('Z') ? value.slice(0, -1) : value;
  }
  const filters = validateSearch(draft);
  const page = params.get('page') ?? '0';
  const size = params.get('pageSize') ?? '25';
  if (
    !/^\d{1,4}$/.test(page) ||
    +page > 1000 ||
    !/^\d{1,3}$/.test(size) ||
    +size < 1 ||
    +size > 100
  )
    throw new Error('Invalid search page.');
  return { ...filters, page: +page, pageSize: +size };
}

export function searchDraft(filters: ComplianceSearchFilters): SearchDraft {
  return {
    orderId: filters.orderId ? String(filters.orderId) : '',
    from: filters.from?.replace(/Z$/, '') ?? '',
    to: filters.to?.replace(/Z$/, '') ?? '',
    clientReference: filters.clientReference ?? '',
    instrument: filters.instrument ?? '',
    status: filters.status ?? '',
  };
}

@Injectable({ providedIn: 'root' })
export class ComplianceSearchState {
  applied: ComplianceSearchFilters | null = null;
  constructor() {
    inject(ComplianceApi).changed.subscribe(() => (this.applied = null));
  }
}

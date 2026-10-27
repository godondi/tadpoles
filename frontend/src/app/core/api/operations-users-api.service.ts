import { Injectable, PLATFORM_ID, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { buildApiUrl } from './api-url';

export interface OperationsUser {
  id: number;
  username: string;
  name: string;
  email: string;
  client_id: number | null;
  balance: number | string;
  revoked: boolean;
  revoked_until: string | null;
  roles: string;
}
@Injectable({ providedIn: 'root' })
export class OperationsUsersApi {
  private readonly http = inject(HttpClient);
  private readonly platform = inject(PLATFORM_ID);
  private url(path: string) {
    return buildApiUrl('/api/operations/users' + path, this.platform);
  }
  clients(search = '') {
    return this.http.get<OperationsUser[]>(this.url(''), { params: search ? { search } : {} });
  }
  changeAccess(
    clientId: number,
    body: { revoke: boolean; amount?: number; unit?: string; reason?: string },
  ) {
    return this.http.patch(this.url(`/${clientId}/trade-access`), body);
  }
  createStaff(body: {
    username: string;
    email: string;
    password: string;
    displayName: string;
    roleType: string;
    advisorName?: string;
    enabled: boolean;
  }) {
    return this.http.post(this.url('/staff'), body);
  }
}

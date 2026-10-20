import { Injectable, inject } from '@angular/core';
import { AuthService } from '../../core/auth/auth.service';
import { OrderCommand } from '../../core/api/workspace-api.service';
export interface PendingOrder {
  key: string;
  command: OrderCommand;
  orderId?: number;
}
@Injectable({ providedIn: 'root' })
export class PendingOrderService {
  private readonly auth = inject(AuthService);

  private storageKey() {
    const id = this.auth.identity()?.userId;
    return id == null ? null : `tadpoles.pending-order.${id}`;
  }

  draft(command?: OrderCommand): OrderCommand | null {
    const key = this.storageKey();
    if (!key || typeof sessionStorage === 'undefined') return null;
    try {
      if (command) {
        sessionStorage.setItem(key + '.draft', JSON.stringify(command));
        return command;
      }
      const raw = sessionStorage.getItem(key + '.draft');
      if (!raw || raw.length > 2048) return null;
      const value = JSON.parse(raw);
      return Number.isInteger(value.instrumentId) &&
        ['BUY', 'SELL'].includes(value.side) &&
        typeof value.quantity === 'string' &&
        value.orderType === 'MARKET'
        ? value
        : null;
    } catch {
      return null;
    }
  }

  clearDraft() {
    const key = this.storageKey();
    if (!key || typeof sessionStorage === 'undefined') return;
    try {
      sessionStorage.removeItem(key + '.draft');
    } catch {
      /* Storage may be unavailable. */
    }
  }

  read(): PendingOrder | null {
    const key = this.storageKey();
    if (!key || typeof sessionStorage === 'undefined') return null;
    try {
      const raw = sessionStorage.getItem(key);
      if (!raw || raw.length > 2048) return null;
      const value = JSON.parse(raw);
      return typeof value.key === 'string' &&
        /^[a-zA-Z0-9_-]{16,128}$/.test(value.key) &&
        Number.isInteger(value.command?.instrumentId) &&
        ['BUY', 'SELL'].includes(value.command.side) &&
        typeof value.command.quantity === 'string' &&
        value.command.orderType === 'MARKET'
        ? value
        : null;
    } catch {
      return null;
    }
  }

  save(value: PendingOrder): boolean {
    const key = this.storageKey();
    if (!key || typeof sessionStorage === 'undefined') return false;
    try {
      sessionStorage.setItem(key, JSON.stringify(value));
      return true;
    } catch {
      return false;
    }
  }

  clear() {
    const key = this.storageKey();
    if (key && typeof sessionStorage !== 'undefined') {
      try {
        sessionStorage.removeItem(key);
      } catch {
        /* Storage may be unavailable. */
      }
    }
  }
}

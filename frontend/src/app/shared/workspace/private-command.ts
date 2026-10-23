import { createIdempotencyKey } from '../../core/idempotency-key';

const actions = [
  'assignment',
  'classification',
  'priority',
  'next-action',
  'tasks',
  'resolution',
  'referral',
  'review',
];
interface Metadata {
  action: string;
  key: string;
}

export class PrivateCommand {
  private readonly slot: string;
  pending: Metadata | null = null;
  body?: Record<string, unknown>;
  constructor(actor: number, resource: string) {
    if (!Number.isSafeInteger(actor) || actor < 1) throw new Error('A verified actor is required.');
    this.slot = `workflow.pending.${actor}.${resource}`;
    const retained = sessionStorage.getItem(this.slot);
    if (retained !== null) {
      const value: unknown = JSON.parse(retained);
      if (
        !value ||
        typeof value !== 'object' ||
        !('key' in value) ||
        !('action' in value) ||
        typeof value.key !== 'string' ||
        !/^[A-Za-z0-9_-]{16,128}$/.test(value.key) ||
        typeof value.action !== 'string' ||
        !actions.includes(value.action) ||
        Object.keys(value).length !== 2
      )
        throw new Error('Recovery metadata is invalid. Do not submit a replacement command.');
      this.pending = { action: value.action, key: value.key };
    }
  }
  prepare(action: string, body: Record<string, unknown>) {
    if (!actions.includes(action)) throw new Error('Unknown workflow command.');
    if (this.pending && (!this.body || this.pending.action !== action))
      throw new Error('Recover the original command before starting different activity.');
    if (!this.pending) {
      const key = createIdempotencyKey();
      if (!key) throw new Error('Unable to create a secure recovery key.');
      const metadata = { action, key };
      sessionStorage.setItem(this.slot, JSON.stringify(metadata));
      this.pending = metadata;
      this.body = structuredClone(body);
    }
    return { ...this.pending, body: this.body! };
  }
  committed() {
    sessionStorage.removeItem(this.slot);
    this.pending = null;
    this.body = undefined;
  }
}

import { createIdempotencyKey } from './idempotency-key';

describe('createIdempotencyKey', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('uses randomUUID when the secure-context API is available', () => {
    vi.stubGlobal('crypto', { randomUUID: () => 'uuid-from-browser' });

    expect(createIdempotencyKey()).toBe('uuid-from-browser');
  });

  it('falls back to getRandomValues when randomUUID is unavailable', () => {
    vi.stubGlobal('crypto', {
      getRandomValues: (bytes: Uint8Array) => {
        bytes.fill(0);
        return bytes;
      },
    });

    expect(createIdempotencyKey()).toBe('00000000-0000-4000-8000-000000000000');
  });

  it('reports unavailable randomness instead of creating a predictable key', () => {
    vi.stubGlobal('crypto', {});

    expect(createIdempotencyKey()).toBeNull();
  });
});

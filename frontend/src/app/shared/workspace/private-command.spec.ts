import { PrivateCommand } from './private-command';

describe('private workflow original-key recovery', () => {
  beforeEach(() => sessionStorage.clear());
  it('persists only opaque metadata and retains exactly the original payload and key', () => {
    const state = new PrivateCommand(17, 'CASE-11111111-1111-4111-8111-111111111111');
    const first = state.prepare('tasks', {
      expectedWorkflowVersion: 3,
      reason: 'Private investigation prose',
      contextId: 'private-purpose-token',
    });
    const second = state.prepare('tasks', { expectedWorkflowVersion: 99, reason: 'Edited draft' });
    expect(second.key).toBe(first.key);
    expect(second.body).toEqual(first.body);
    expect(JSON.stringify(sessionStorage)).not.toContain('Private investigation prose');
    expect(JSON.stringify(sessionStorage)).not.toContain('private-purpose-token');
    const restored = new PrivateCommand(17, 'CASE-11111111-1111-4111-8111-111111111111');
    expect(restored.pending?.key).toBe(first.key);
    expect(() => restored.prepare('tasks', {})).toThrow();
    expect(new PrivateCommand(18, 'CASE-11111111-1111-4111-8111-111111111111').pending).toBeNull();
    state.committed();
    expect(new PrivateCommand(17, 'CASE-11111111-1111-4111-8111-111111111111').pending).toBeNull();
  });
  it('fails closed on corrupt metadata instead of silently replacing an uncertain key', () => {
    sessionStorage.setItem('workflow.pending.17.CASE-opaque', '{"key":"bad","action":"tasks"}');
    expect(() => new PrivateCommand(17, 'CASE-opaque')).toThrow();
    expect(sessionStorage.getItem('workflow.pending.17.CASE-opaque')).toContain('bad');
  });
});

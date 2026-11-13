import type { ProbeResult } from '../api/ops';
import { describeStatus } from './status';

const snap = (live: ProbeResult, ready: ProbeResult) => ({ live, ready, checkedAt: new Date('2026-11-13T02:00:00Z') });
const state = (v: ReturnType<typeof describeStatus>, id: string) => v.nodes.find((n) => n.id === id)!;

describe('describeStatus', () => {
  it('reports all systems ready only when readiness passes', () => {
    const v = describeStatus(snap('up', 'up'));
    expect(v).toMatchObject({ ready: true, headline: 'All systems ready' });
    expect(v.nodes.map((n) => n.state)).toEqual(['ok', 'ok', 'ok', 'ok']);
  });

  it('does not blame the database when readiness fails but the process is running', () => {
    const v = describeStatus(snap('up', 'down'));
    expect(v).toMatchObject({ ready: false, headline: 'Not ready' });
    expect(state(v, 'api')).toMatchObject({ state: 'warn', detail: 'Running, not ready' });
    // The probe cannot say which dependency failed, so the database is unconfirmed, never "down".
    expect(state(v, 'database')).toMatchObject({ state: 'unknown', detail: 'Not confirmed' });
  });

  it('marks the API down when both probes answer with a failure', () => {
    const v = describeStatus(snap('down', 'down'));
    expect(state(v, 'api').state).toBe('down');
    expect(state(v, 'proxy').state).toBe('ok');
  });

  it('treats no answer as unreachable without claiming anything about the API or database', () => {
    const v = describeStatus(snap('unreachable', 'unreachable'));
    expect(v.ready).toBe(false);
    expect(state(v, 'proxy')).toMatchObject({ state: 'down', detail: 'No response' });
    expect(state(v, 'api').state).toBe('unknown');
    expect(state(v, 'database').state).toBe('unknown');
  });

  it('is not "unreachable" when liveness answered but readiness did not connect or time out', () => {
    const v = describeStatus(snap('up', 'unreachable'));
    expect(v.ready).toBe(false);
    expect(state(v, 'proxy').state).toBe('ok');
    expect(state(v, 'api')).toMatchObject({ state: 'warn', detail: 'Running, not ready' });
  });

  it('always writes a word for every state', () => {
    for (const s of [snap('up', 'up'), snap('up', 'down'), snap('down', 'down'), snap('unreachable', 'unreachable')]) {
      for (const n of describeStatus(s).nodes) expect(n.detail.length).toBeGreaterThan(0);
    }
  });
});

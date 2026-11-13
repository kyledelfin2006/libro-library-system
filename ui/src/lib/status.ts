import type { StatusSnapshot } from '../api/ops';

export type NodeId = 'browser' | 'proxy' | 'api' | 'database';
export type NodeState = 'ok' | 'warn' | 'down' | 'unknown';

export interface PathNode {
  id: NodeId;
  label: string;
  state: NodeState;
  /** Words for the state, so color is never the only signal. */
  detail: string;
}

export interface StatusView {
  ready: boolean;
  headline: string;
  explanation: string;
  nodes: PathNode[];
}

/**
 * Turns the two probe answers into the request-path view. It claims only what the probes show:
 * a 200 from /readyz means the browser, proxy and API answered and readiness (which includes the database) passed;
 * a failed readiness probe does not say which part failed, so the database is "not confirmed", never "down".
 */
export function describeStatus(s: StatusSnapshot): StatusView {
  const browser: PathNode = { id: 'browser', label: 'Browser', state: 'ok', detail: 'This device' };

  // Only when neither probe connected is the service unreachable. One answer means something is there.
  if (s.ready === 'unreachable' && s.live === 'unreachable') {
    return {
      ready: false,
      headline: 'Not ready',
      explanation: 'The library service did not respond. Check your connection, or try again shortly.',
      nodes: [
        browser,
        { id: 'proxy', label: 'Proxy', state: 'down', detail: 'No response' },
        { id: 'api', label: 'API', state: 'unknown', detail: 'Not reached' },
        { id: 'database', label: 'Database', state: 'unknown', detail: 'Not reached' },
      ],
    };
  }

  if (s.ready === 'up') {
    return {
      ready: true,
      headline: 'All systems ready',
      explanation: 'The library service reports that it is ready, including its database.',
      nodes: [
        browser,
        { id: 'proxy', label: 'Proxy', state: 'ok', detail: 'Responding' },
        { id: 'api', label: 'API', state: 'ok', detail: 'Ready' },
        { id: 'database', label: 'Database', state: 'ok', detail: 'Ready' },
      ],
    };
  }

  // Readiness failed but something answered. Liveness tells apart "running but not ready" from "not running".
  const running = s.live === 'up';
  return {
    ready: false,
    headline: 'Not ready',
    explanation: running
      ? 'The service is running but reports that it is not ready. The readiness check includes the database; it does not say which part failed.'
      : 'The service answered but reports that it is not running.',
    nodes: [
      browser,
      { id: 'proxy', label: 'Proxy', state: 'ok', detail: 'Responding' },
      { id: 'api', label: 'API', state: running ? 'warn' : 'down', detail: running ? 'Running, not ready' : 'Not running' },
      { id: 'database', label: 'Database', state: 'unknown', detail: 'Not confirmed' },
    ],
  };
}

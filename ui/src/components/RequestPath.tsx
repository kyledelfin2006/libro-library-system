import type { NodeState, PathNode } from '../lib/status';

const STROKE: Record<NodeState, string> = {
  ok: 'var(--status-active)',
  warn: 'var(--status-overdue)',
  down: 'var(--status-lost)',
  unknown: 'var(--color-border-input)',
};
const ICON: Record<NodeState, string> = { ok: '✓', warn: '!', down: '✕', unknown: '?' };

const NODE_W = 132;
const GAP = 42;
const STEP = NODE_W + GAP;

/**
 * Request-path diagram: browser, proxy, API, database as a left-to-right chain. It draws only what the probes
 * reported, and every state is written in words with an icon next to its box, so color is never the only signal.
 */
export function RequestPath({ nodes }: { nodes: PathNode[] }) {
  const summary = nodes.map((n) => `${n.label}: ${n.detail}`).join('. ');
  const width = nodes.length * NODE_W + (nodes.length - 1) * GAP + 4;
  return (
    <figure className="diagram">
      <svg viewBox={`0 0 ${width} 104`} role="img" aria-labelledby="path-desc" className="chart-svg">
        <title id="path-desc">{`Request path from the browser to the database. ${summary}.`}</title>
        {nodes.map((n, i) => {
          const x = 2 + i * STEP;
          return (
            <g key={n.id}>
              <rect x={x} y={8} width={NODE_W} height={44} rx={6} fill="none" stroke={STROKE[n.state]} strokeWidth={2} />
              <text x={x + NODE_W / 2} y={35} textAnchor="middle" className="chart-axis">{n.label}</text>
              <text x={x + NODE_W / 2} y={78} textAnchor="middle" className="chart-axis">{`${ICON[n.state]} ${n.detail}`}</text>
              {i < nodes.length - 1 && (
                <path d={`M${x + NODE_W} 30 H${x + STEP}`} fill="none" stroke="var(--color-text-muted)" strokeWidth={1.5} />
              )}
            </g>
          );
        })}
      </svg>
      <figcaption className="muted">Only what the public health probes report is shown. They do not break out the database separately.</figcaption>
    </figure>
  );
}

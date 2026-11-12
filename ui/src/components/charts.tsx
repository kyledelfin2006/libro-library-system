import { useId, useState, type ReactNode } from 'react';

export interface Datum {
  label: string;
  value: number;
  /** CSS color, normally a token such as var(--chart-1). */
  color?: string;
}

/**
 * Figure frame shared by every chart: the caption states the computed takeaway, and "View as table" swaps the
 * graphic for the data behind it (plan section 5.8), so nothing decorative stands in for the numbers.
 */
export function ChartFigure({
  caption,
  columns,
  rows,
  children,
}: {
  caption: string;
  columns: [string, string];
  rows: [string, string | number][];
  children: ReactNode;
}) {
  const [asTable, setAsTable] = useState(false);
  const id = useId();
  return (
    <figure className="chart" aria-labelledby={`${id}-cap`}>
      <figcaption id={`${id}-cap`}>{caption}</figcaption>
      <button type="button" className="chart-toggle" aria-pressed={asTable} onClick={() => setAsTable((v) => !v)}>
        {asTable ? 'View as chart' : 'View as table'}
      </button>
      {asTable ? (
        <table>
          <thead>
            <tr>
              <th scope="col">{columns[0]}</th>
              <th scope="col">{columns[1]}</th>
            </tr>
          </thead>
          <tbody>
            {rows.map(([a, b]) => (
              <tr key={a}>
                <th scope="row">{a}</th>
                <td>{b}</td>
              </tr>
            ))}
          </tbody>
        </table>
      ) : (
        children
      )}
    </figure>
  );
}

/** Horizontal bars for category comparison, with direct value labels. Marks are keyboard-focusable. */
export function HBarChart({ data, caption, unit = 'Count' }: { data: Datum[]; caption: string; unit?: string }) {
  const max = Math.max(1, ...data.map((d) => d.value));
  const row = 28;
  const labelW = 130;
  const width = 480;
  return (
    <ChartFigure caption={caption} columns={['Category', unit]} rows={data.map((d) => [d.label, d.value])}>
      {data.length === 0 ? (
        <p className="muted">No data to show.</p>
      ) : (
        <svg viewBox={`0 0 ${width} ${data.length * row}`} role="img" aria-label={caption} className="chart-svg">
          {data.map((d, i) => {
            const w = ((width - labelW - 48) * d.value) / max;
            return (
              <g key={d.label} tabIndex={0} aria-label={`${d.label}: ${d.value}`}>
                <title>{`${d.label}: ${d.value}`}</title>
                <text x={labelW - 8} y={i * row + 18} textAnchor="end" className="chart-axis">
                  {d.label}
                </text>
                <rect x={labelW} y={i * row + 4} width={Math.max(w, 1)} height={row - 10} fill={d.color ?? 'var(--chart-1)'} />
                <text x={labelW + w + 6} y={i * row + 18} className="chart-value">
                  {d.value}
                </text>
              </g>
            );
          })}
        </svg>
      )}
    </ChartFigure>
  );
}

/** One stacked bar for part-to-whole mix (for example loan status). Segment colors carry fixed meanings. */
export function StackedBar({ data, caption }: { data: Datum[]; caption: string }) {
  const total = data.reduce((sum, d) => sum + d.value, 0);
  let x = 0;
  return (
    <ChartFigure caption={caption} columns={['Segment', 'Count']} rows={data.map((d) => [d.label, d.value])}>
      {total === 0 ? (
        <p className="muted">No data to show.</p>
      ) : (
        <>
          <svg viewBox="0 0 480 40" role="img" aria-label={caption} className="chart-svg">
            {data.map((d) => {
              const w = (480 * d.value) / total;
              const rect = (
                <rect key={d.label} x={x} y={4} width={w} height={32} fill={d.color ?? 'var(--chart-1)'} tabIndex={0} aria-label={`${d.label}: ${d.value}`}>
                  <title>{`${d.label}: ${d.value}`}</title>
                </rect>
              );
              x += w;
              return rect;
            })}
          </svg>
          <ul className="chart-legend">
            {data.map((d) => (
              <li key={d.label}>
                <span className="swatch" style={{ background: d.color ?? 'var(--chart-1)' }} aria-hidden="true" /> {d.label} {d.value}
              </li>
            ))}
          </ul>
        </>
      )}
    </ChartFigure>
  );
}

/** Single line for change over time, with a point and value label on each mark. */
export function LineChart({ data, caption, unit = 'Count' }: { data: Datum[]; caption: string; unit?: string }) {
  const max = Math.max(1, ...data.map((d) => d.value));
  const w = 480;
  const h = 160;
  const pad = 24;
  const step = data.length > 1 ? (w - pad * 2) / (data.length - 1) : 0;
  const pts = data.map((d, i) => [pad + i * step, h - pad - ((h - pad * 2) * d.value) / max, d] as const);
  return (
    <ChartFigure caption={caption} columns={['Period', unit]} rows={data.map((d) => [d.label, d.value])}>
      {data.length === 0 ? (
        <p className="muted">No data to show.</p>
      ) : (
        <svg viewBox={`0 0 ${w} ${h}`} role="img" aria-label={caption} className="chart-svg">
          <line x1={pad} x2={w - pad} y1={h - pad} y2={h - pad} stroke="var(--color-border)" />
          <polyline fill="none" stroke="var(--chart-1)" strokeWidth="2" points={pts.map(([x, y]) => `${x},${y}`).join(' ')} />
          {pts.map(([x, y, d]) => (
            <circle key={d.label} cx={x} cy={y} r={4} fill="var(--chart-1)" tabIndex={0} aria-label={`${d.label}: ${d.value}`}>
              <title>{`${d.label}: ${d.value}`}</title>
            </circle>
          ))}
        </svg>
      )}
    </ChartFigure>
  );
}

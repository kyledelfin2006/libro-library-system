import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { apiDocsAvailable, getBuildInfo } from '../api/ops';
import { Chip, ErrorState, Skeleton } from '../components/ui';
import { formatInstant } from '../lib/dates';

/** Optional dashboard link, set at build time (for example an SSH-tunnelled Grafana). Only http(s) addresses are used. */
const METRICS_URL = /^https?:\/\//.test(import.meta.env.VITE_METRICS_URL ?? '') ? import.meta.env.VITE_METRICS_URL : null;

/** Staff-only Version page: the running build from /app/info, plus links to Status and (when served) the API docs. */
export function Version() {
  const info = useQuery({ queryKey: ['buildInfo'], queryFn: getBuildInfo });
  // Swagger UI is off unless LIBRO_API_DOCS_ENABLED=true, so the link only appears when the document is served.
  const docs = useQuery({ queryKey: ['apiDocsAvailable'], queryFn: apiDocsAvailable, retry: false });

  return (
    <section>
      <h1>Version</h1>
      {info.isPending ? (
        <Skeleton height={180} />
      ) : info.isError ? (
        <ErrorState error={info.error} onRetry={() => void info.refetch()} />
      ) : (
        <dl className="catalog-card version-card">
          <div><dt>Version</dt><dd>{info.data.version}</dd></div>
          <div><dt>Built</dt><dd>{formatInstant(info.data.buildTime)}</dd></div>
          <div><dt>Commit</dt><dd><code>{info.data.commit}</code></dd></div>
          <div><dt>Environment</dt><dd><Chip>{info.data.environment}</Chip></dd></div>
        </dl>
      )}
      <ul className="link-list">
        <li><Link to="/status">Service status</Link></li>
        <li><Link to="/releases">Release history</Link></li>
        {docs.data && <li><a href="/swagger-ui.html" target="_blank" rel="noreferrer">API documentation<span className="visually-hidden"> (opens in a new tab)</span></a></li>}
      </ul>
      {METRICS_URL
        ? <p><a href={METRICS_URL} target="_blank" rel="noreferrer">Metrics dashboard<span className="visually-hidden"> (opens in a new tab)</span></a></p>
        : <p className="muted">Metrics are collected on a private management port. Set VITE_METRICS_URL when building to link a dashboard here.</p>}
    </section>
  );
}

import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { apiDocsAvailable, getBuildInfo } from '../api/ops';
import { Chip, ErrorState, Skeleton } from '../components/ui';
import { formatInstant } from '../lib/dates';

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
        {docs.data && <li><a href="/swagger-ui.html" target="_blank" rel="noreferrer">API documentation<span className="visually-hidden"> (opens in a new tab)</span></a></li>}
      </ul>
      <p className="muted">Metrics are collected on a private management port and are not linked from here.</p>
    </section>
  );
}

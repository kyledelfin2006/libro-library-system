import { useQuery } from '@tanstack/react-query';
import { checkStatus } from '../api/ops';
import { PublicHeader } from '../components/PublicHeader';
import { RequestPath } from '../components/RequestPath';
import { Button, Skeleton } from '../components/ui';
import { formatClock } from '../lib/dates';
import { describeStatus } from '../lib/status';

/** How often the page re-checks while it is visible (plan 6.11). Background tabs pause automatically. */
export const STATUS_POLL_MS = 30_000;

/**
 * Public status page. It reads /livez and /readyz, shows one overall state, the time of the last check in Manila
 * time, and the request-path diagram. A manual refresh is always available.
 */
export function Status() {
  const status = useQuery({
    queryKey: ['status'],
    queryFn: checkStatus,
    refetchInterval: STATUS_POLL_MS,
    refetchIntervalInBackground: false,
    retry: false,
  });
  const view = status.data ? describeStatus(status.data) : null;

  return (
    <div className="shell">
      <a className="skip-link" href="#main">Skip to content</a>
      <PublicHeader />
      <main id="main" tabIndex={-1} className="shell-main">
        <h1>Status</h1>
        {!view ? (
          <Skeleton height={160} />
        ) : (
          <>
            {/* The headline is announced politely when a re-check changes it. */}
            <p className={`status-headline ${view.ready ? 'status-ready' : 'status-notready'}`} role="status">
              <span aria-hidden="true">{view.ready ? '✓' : '!'}</span> {view.headline}
            </p>
            <p>{view.explanation}</p>
            <RequestPath nodes={view.nodes} />
          </>
        )}
        <div className="status-meta">
          {status.data && <span className="muted">Last checked {formatClock(status.data.checkedAt)} (Manila time). Checks again every {STATUS_POLL_MS / 1000} seconds while this page is open.</span>}
          <Button variant="secondary" onClick={() => void status.refetch()} disabled={status.isFetching}>
            {status.isFetching ? 'Checking…' : 'Check now'}
          </Button>
        </div>
      </main>
      <footer className="shell-footer">Libro · Times are shown in Manila time</footer>
    </div>
  );
}

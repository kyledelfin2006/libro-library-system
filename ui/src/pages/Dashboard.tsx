import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { getGenres } from '../api/books';
import { bookStats, listLoans, loanActivity, topTitles } from '../api/staff';
import { HBarChart, LineChart, StackedBar } from '../components/charts';
import { Button, ErrorState, Skeleton } from '../components/ui';
import { daysUntil, formatDate } from '../lib/dates';
import { genreBars, overdueBuckets, statusMix, statusTakeaway, weekLabel } from '../lib/dashboard';

/** Loan count for a filter, read from totalElements of a one-row page, so no loan rows are downloaded. */
function useLoanCount(key: string, filters: { status?: 'ACTIVE' | 'RETURNED' | 'LOST'; overdue?: boolean }) {
  return useQuery({
    queryKey: ['loanCount', key],
    queryFn: async () => (await listLoans({ ...filters, page: 0, size: 1 })).totalElements,
  });
}

function Tile({ label, value, to, pending }: { label: string; value: string | number | undefined; to?: string; pending: boolean }) {
  const body = (
    <>
      <span className="tile-label">{label}</span>
      <span className="tile-value">{pending ? <Skeleton width={48} height={28} /> : (value ?? '-')}</span>
    </>
  );
  return to ? <Link to={to} className="tile">{body}</Link> : <div className="tile">{body}</div>;
}

/**
 * Staff dashboard: KPI tiles that open the Loans page pre-filtered, loan status mix, collection by genre, overdue by
 * days late, weekly activity, most-borrowed titles, and the longest-overdue loans. Counts respect the caller's role
 * scope because the server applies it. Every chart has a "View as table" toggle.
 */
export function Dashboard() {
  const stats = useQuery({ queryKey: ['bookStats'], queryFn: bookStats });
  const active = useLoanCount('active', { status: 'ACTIVE' });
  const overdue = useLoanCount('overdue', { overdue: true });
  const lost = useLoanCount('lost', { status: 'LOST' });
  const returned = useLoanCount('returned', { status: 'RETURNED' });
  const genres = useQuery({ queryKey: ['genres'], queryFn: getGenres });
  const overdueLoans = useQuery({
    queryKey: ['overdueLoans'],
    queryFn: () => listLoans({ overdue: true, page: 0, size: 100, sort: 'dueAt,asc' }),
  });
  const activity = useQuery({ queryKey: ['loanActivity'], queryFn: () => loanActivity(12) });
  const top = useQuery({ queryKey: ['topTitles'], queryFn: () => topTitles(90, 5) });

  const counts = { active: active.data, overdue: overdue.data, lost: lost.data, returned: returned.data };
  const countsReady = Object.values(counts).every((v) => v !== undefined);
  const refresh = () => {
    for (const q of [stats, active, overdue, lost, returned, genres, overdueLoans, activity, top]) void q.refetch();
  };

  const longest = overdueLoans.data?.content.slice(0, 5) ?? [];
  const overdueTotal = overdueLoans.data?.totalElements ?? 0;
  const shown = overdueLoans.data?.content.length ?? 0;

  return (
    <section>
      <header className="page-header">
        <h1>Dashboard</h1>
        <Button variant="secondary" onClick={refresh}>Refresh</Button>
      </header>

      <div className="tiles">
        <Tile label="Copies" value={stats.data?.totalBooks} to="/inventory" pending={stats.isPending} />
        <Tile label="Active loans" value={active.data} to="/staff/loans?status=ACTIVE" pending={active.isPending} />
        <Tile label="Overdue" value={overdue.data} to="/staff/loans?overdue=true" pending={overdue.isPending} />
        <Tile label="Lost" value={lost.data} to="/staff/loans?status=LOST" pending={lost.isPending} />
        <Tile
          label="Replacement value"
          value={stats.data ? stats.data.totalReplacementCost.toFixed(2) : undefined}
          pending={stats.isPending}
        />
      </div>

      <div className="dash-grid">
        <div>
          {!countsReady ? (
            counts.active === undefined && active.isError ? <ErrorState error={active.error} onRetry={refresh} /> : <Skeleton height={140} />
          ) : (
            <StackedBar
              data={statusMix(counts as { active: number; overdue: number; returned: number; lost: number })}
              caption={statusTakeaway(counts as { active: number; overdue: number })}
            />
          )}
        </div>

        <div>
          {overdueLoans.isPending ? (
            <Skeleton height={140} />
          ) : overdueLoans.isError ? (
            <ErrorState error={overdueLoans.error} onRetry={() => void overdueLoans.refetch()} />
          ) : (
            <HBarChart
              data={overdueBuckets(overdueLoans.data.content)}
              unit="Overdue loans"
              caption={
                overdueTotal === 0
                  ? 'No overdue loans'
                  : shown < overdueTotal
                    ? `Overdue by days late (first ${shown} of ${overdueTotal})`
                    : `${overdueTotal} overdue ${overdueTotal === 1 ? 'loan' : 'loans'} by days late`
              }
            />
          )}
        </div>

        <div>
          {activity.isPending ? (
            <Skeleton height={180} />
          ) : activity.isError ? (
            <ErrorState error={activity.error} onRetry={() => void activity.refetch()} />
          ) : (
            <LineChart
              data={activity.data.map((w) => ({ label: `Week of ${weekLabel(w.weekStart)}: checkouts`, value: w.checkouts }))}
              unit="Checkouts"
              caption={`${activity.data.reduce((s, w) => s + w.checkouts, 0)} checkouts and ${activity.data.reduce((s, w) => s + w.returns, 0)} returns in the last 12 weeks`}
            />
          )}
        </div>

        <div>
          {genres.isPending ? (
            <Skeleton height={180} />
          ) : genres.isError ? (
            <ErrorState error={genres.error} onRetry={() => void genres.refetch()} />
          ) : (
            <HBarChart
              data={genreBars(genres.data)}
              unit="Copies"
              caption={`Collection by genre (${Object.keys(genres.data).length} genres)`}
            />
          )}
        </div>

        <div>
          {top.isPending ? (
            <Skeleton height={140} />
          ) : top.isError ? (
            <ErrorState error={top.error} onRetry={() => void top.refetch()} />
          ) : (
            <HBarChart
              data={top.data.map((t) => ({ label: t.title, value: t.checkouts, color: 'var(--chart-3)' }))}
              unit="Checkouts"
              caption={top.data.length === 0 ? 'No checkouts in the last 90 days' : `Most borrowed titles, last 90 days (top ${top.data.length})`}
            />
          )}
        </div>

        <div className="panel">
          <h2>Needs attention</h2>
          {overdueLoans.isPending ? (
            <Skeleton height={120} />
          ) : longest.length === 0 ? (
            <p className="muted">No overdue loans.</p>
          ) : (
            <table className="loan-table record-cards">
              <thead>
                <tr>
                  <th scope="col">Borrower</th>
                  <th scope="col">Book</th>
                  <th scope="col">Due</th>
                  <th scope="col">Days late</th>
                  <th scope="col"><span className="visually-hidden">Open</span></th>
                </tr>
              </thead>
              <tbody>
                {longest.map((l) => (
                  <tr key={l.loanId}>
                    <th scope="row" data-label="Borrower">{l.borrowerUniversityId}</th>
                    <td data-label="Book">{l.bookTitle}</td>
                    <td data-label="Due">{formatDate(l.dueAt)}</td>
                    <td data-label="Days late">{Math.max(1, -daysUntil(l.dueAt))}</td>
                    <td><Link to={`/staff/loans?status=ACTIVE&overdue=true&open=${l.loanId}`}>Open<span className="visually-hidden"> loan {l.loanId}</span></Link></td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>
    </section>
  );
}

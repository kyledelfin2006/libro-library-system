import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { getReleaseHistory } from '../api/ops';
import { Chip, EmptyState, Skeleton } from '../components/ui';
import { formatInstant } from '../lib/dates';

/**
 * Staff-only release history. It reads a static file generated when the UI was built (GitHub Releases and the CD runs
 * that published an image), so it shows what was true at build time and says when that was.
 */
export function Releases() {
  const history = useQuery({ queryKey: ['releaseHistory'], queryFn: getReleaseHistory, retry: false });
  const data = history.data;
  const empty = !data || (data.releases.length === 0 && data.publishedImages.length === 0);

  return (
    <section>
      <h1>Release history</h1>
      <p className="muted">Generated when this version of the app was built, not live. Times are Manila time.</p>
      {history.isPending ? (
        <Skeleton height={180} />
      ) : empty ? (
        <EmptyState title="No release history in this build.">
          The history is written during the release build. It is empty in local development, or when GitHub could not be reached.
        </EmptyState>
      ) : (
        <>
          <p className="muted">Generated {formatInstant(data.generatedAt)}.</p>
          <h2>Releases</h2>
          {data.releases.length === 0 ? (
            <p>No GitHub Releases have been published.</p>
          ) : (
            <table className="loan-table record-cards">
              <thead><tr><th scope="col">Release</th><th scope="col">Tag</th><th scope="col">Published</th></tr></thead>
              <tbody>
                {data.releases.map((r) => (
                  <tr key={r.tag}>
                    <th scope="row" data-label="Release">
                      <a href={r.url} target="_blank" rel="noreferrer">{r.name}<span className="visually-hidden"> (opens in a new tab)</span></a>
                      {r.prerelease && <> <Chip>Pre-release</Chip></>}
                    </th>
                    <td data-label="Tag"><code>{r.tag}</code></td>
                    <td data-label="Published">{formatInstant(r.publishedAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
          <h2>Published images</h2>
          <p className="muted">Each row is a successful CD run that built, scanned and published an image for that commit.</p>
          {data.publishedImages.length === 0 ? (
            <p>No images have been published.</p>
          ) : (
            <table className="loan-table record-cards">
              <thead><tr><th scope="col">Run</th><th scope="col">Commit</th><th scope="col">Completed</th></tr></thead>
              <tbody>
                {data.publishedImages.map((p) => (
                  <tr key={p.run}>
                    <th scope="row" data-label="Run">
                      <a href={p.url} target="_blank" rel="noreferrer">#{p.run}<span className="visually-hidden"> (opens in a new tab)</span></a>
                    </th>
                    <td data-label="Commit"><code>{p.commit.slice(0, 12)}</code></td>
                    <td data-label="Completed">{formatInstant(p.completedAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </>
      )}
      <p><Link to="/version">Back to Version</Link></p>
    </section>
  );
}

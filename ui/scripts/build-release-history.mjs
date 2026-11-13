// Writes public/release-history.json at build time from GitHub Releases and successful CD runs, so the Release history
// page is a static file: no runtime call to GitHub, nothing for the API to serve, and no token in the browser.
//
// Environment: GITHUB_REPOSITORY (owner/name) and GITHUB_TOKEN (read access). Without them, or when GitHub cannot be
// reached, an empty history is written and the build continues; a release must never fail because of this page.
import { mkdir, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';

const repo = process.env.GITHUB_REPOSITORY;
const token = process.env.GITHUB_TOKEN;
const output = fileURLToPath(new URL('../public/release-history.json', import.meta.url));

/** Reads one GitHub REST endpoint; any failure becomes an empty result so the history is best-effort. */
async function github(path) {
  const response = await fetch(`https://api.github.com/repos/${repo}/${path}`, {
    headers: { Accept: 'application/vnd.github+json', Authorization: `Bearer ${token}`, 'X-GitHub-Api-Version': '2022-11-28' },
    signal: AbortSignal.timeout(15_000),
  });
  if (!response.ok) throw new Error(`${path}: HTTP ${response.status}`);
  return response.json();
}

const history = { generatedAt: new Date().toISOString(), releases: [], publishedImages: [] };

if (repo && token) {
  try {
    const releases = await github('releases?per_page=20');
    history.releases = releases.filter((r) => !r.draft).map((r) => ({
      name: r.name || r.tag_name, tag: r.tag_name, publishedAt: r.published_at, url: r.html_url, prerelease: !!r.prerelease,
    }));
    // Pull-request dry runs never publish, so only runs started by CI on main or by hand count.
    const runs = await github('actions/workflows/cd.yml/runs?status=success&per_page=30');
    history.publishedImages = runs.workflow_runs
      .filter((run) => run.event === 'workflow_run' || run.event === 'workflow_dispatch')
      .slice(0, 20)
      .map((run) => ({ commit: run.head_sha, completedAt: run.updated_at, url: run.html_url, run: run.run_number }));
  } catch (error) {
    console.warn(`Release history left empty: ${error.message}`);
  }
} else {
  console.warn('Release history left empty: GITHUB_REPOSITORY and GITHUB_TOKEN are not set.');
}

await mkdir(new URL('../public/', import.meta.url), { recursive: true });
await writeFile(output, `${JSON.stringify(history, null, 2)}\n`);
console.log(`Wrote ${history.releases.length} releases and ${history.publishedImages.length} published images.`);

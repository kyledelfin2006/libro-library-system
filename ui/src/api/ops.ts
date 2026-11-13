import { request } from './client';
import type { BuildInfo } from './types';

/** What one probe said: a 2xx answer, any other HTTP answer, or no answer at all. */
export type ProbeResult = 'up' | 'down' | 'unreachable';

/** The two public probes, read together. Only these facts are available; nothing per-component is claimed. */
export interface StatusSnapshot {
  /** `/livez`: the process is running (it deliberately ignores the database). */
  live: ProbeResult;
  /** `/readyz`: the app can serve traffic (readiness includes the database). */
  ready: ProbeResult;
  checkedAt: Date;
}

/** A probe that has not answered after this long counts as "not up". Readiness can hang while the database is down. */
export const PROBE_TIMEOUT_MS = 8_000;

/**
 * Reads one probe without credentials. Probes return Spring's own body, not the ApiResponse envelope, so the body is
 * ignored. A probe that answers too slowly is `down` (the service is there but not healthy); only a failed connection
 * is `unreachable`.
 */
async function probe(path: string): Promise<ProbeResult> {
  try {
    const response = await fetch(path, {
      method: 'GET',
      headers: { Accept: 'application/json' },
      cache: 'no-store',
      signal: AbortSignal.timeout(PROBE_TIMEOUT_MS),
    });
    return response.ok ? 'up' : 'down';
  } catch (e) {
    return e instanceof DOMException && e.name === 'TimeoutError' ? 'down' : 'unreachable';
  }
}

/** Checks liveness and readiness together. Never throws: an unreachable service is a result, not an error. */
export async function checkStatus(): Promise<StatusSnapshot> {
  const [live, ready] = await Promise.all([probe('/livez'), probe('/readyz')]);
  return { live, ready, checkedAt: new Date() };
}

/** Safe build identification for library staff (`GET /app/info`). */
export const getBuildInfo = () => request<BuildInfo>('GET', '/app/info');

/**
 * Whether the API documentation is served. It is off unless LIBRO_API_DOCS_ENABLED=true, so the link is shown only when
 * a HEAD request to the OpenAPI document succeeds.
 */
export async function apiDocsAvailable(): Promise<boolean> {
  try {
    const response = await fetch('/v3/api-docs', { method: 'HEAD', cache: 'no-store' });
    return response.ok;
  } catch {
    return false;
  }
}

/** One published GitHub Release. */
export interface ReleaseEntry { name: string; tag: string; publishedAt: string; url: string; prerelease: boolean }
/** One successful CD run that published an image for a commit. */
export interface PublishedImage { commit: string; completedAt: string; url: string; run: number }
/** The static file written at build time by `scripts/build-release-history.mjs`. */
export interface ReleaseHistory { generatedAt: string; releases: ReleaseEntry[]; publishedImages: PublishedImage[] }

/**
 * Reads the build-time release history. It is a static file, so it needs no credentials; a missing file, or a dev
 * server answering with the app shell instead, is an empty history rather than an error.
 */
export async function getReleaseHistory(): Promise<ReleaseHistory | null> {
  try {
    const response = await fetch('/release-history.json', { headers: { Accept: 'application/json' }, cache: 'no-store' });
    if (!response.ok) return null;
    const body: unknown = await response.json();
    if (!body || typeof body !== 'object') return null;
    const h = body as Partial<ReleaseHistory>;
    if (typeof h.generatedAt !== 'string') return null;
    return {
      generatedAt: h.generatedAt,
      releases: Array.isArray(h.releases) ? h.releases : [],
      publishedImages: Array.isArray(h.publishedImages) ? h.publishedImages : [],
    };
  } catch {
    return null;
  }
}

import { act, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, vi } from 'vitest';
import { fail, ok, pageOf, renderAt, routeFetch, staffUser } from '../test/render';
import { STATUS_POLL_MS } from './Status';

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

const assistant = { ...staffUser, universityId: '2020-0002', userRole: 'ADMINISTRATOR' as const };
const probe = (status: number) => new Response(JSON.stringify({ status: status === 200 ? 'UP' : 'OUT_OF_SERVICE' }), { status });
const callsTo = (mock: ReturnType<typeof routeFetch>, prefix: string) => mock.mock.calls.filter((c) => c[0].startsWith(prefix)).length;

describe('status page', () => {
  it('is public and shows All systems ready with the request path', async () => {
    routeFetch({ '/livez': () => probe(200), '/readyz': () => probe(200) });
    renderAt('/status', null);
    expect(await screen.findByText(/All systems ready/)).toBeInTheDocument();
    const diagram = screen.getByRole('img', { name: /Request path from the browser to the database/ });
    expect(diagram).toHaveAccessibleName(/Database: Ready/);
    expect(screen.getByText(/Last checked \d\d:\d\d:\d\d \(Manila time\)/)).toBeInTheDocument();
    // Signed out, the only way onward is sign-in.
    expect(screen.getByRole('link', { name: 'Sign in' })).toBeInTheDocument();
  });

  it('says Not ready without blaming a component when readiness fails', async () => {
    routeFetch({ '/livez': () => probe(200), '/readyz': () => probe(503) });
    renderAt('/status', null);
    expect(await screen.findByText(/Not ready/)).toBeInTheDocument();
    expect(screen.getByText(/does not say which part failed/)).toBeInTheDocument();
    expect(screen.getByRole('img', { name: /Request path/ })).toHaveAccessibleName(/Database: Not confirmed/);
  });

  it('reports an unreachable service as Not ready', async () => {
    vi.stubGlobal('fetch', vi.fn(() => Promise.reject(new TypeError('offline'))));
    renderAt('/status', null);
    expect(await screen.findByText(/Not ready/)).toBeInTheDocument();
    expect(screen.getByText(/did not respond/)).toBeInTheDocument();
    expect(screen.getByRole('img', { name: /Request path/ })).toHaveAccessibleName(/Proxy: No response/);
  });

  it('treats a readiness probe that times out as not ready while liveness answers', async () => {
    vi.stubGlobal('fetch', vi.fn((url: string) => (
      url.startsWith('/readyz') ? Promise.reject(new DOMException('timed out', 'TimeoutError')) : Promise.resolve(probe(200))
    )));
    renderAt('/status', null);
    expect(await screen.findByText(/Not ready/)).toBeInTheDocument();
    expect(screen.getByRole('img', { name: /Request path/ })).toHaveAccessibleName(/API: Running, not ready/);
    expect(screen.queryByText(/did not respond/)).toBeNull();
  });

  it('re-checks on demand and sends no credentials', async () => {
    const mock = routeFetch({ '/livez': () => probe(200), '/readyz': () => probe(200) });
    const user = userEvent.setup();
    renderAt('/status', staffUser);
    await screen.findByText(/All systems ready/);
    expect(callsTo(mock, '/readyz')).toBe(1);
    await user.click(screen.getByRole('button', { name: 'Check now' }));
    await waitFor(() => expect(callsTo(mock, '/readyz')).toBe(2));
    for (const c of mock.mock.calls) expect(JSON.stringify(c[1] ?? {})).not.toMatch(/authorization/i);
    // Signed in, the header leads back to the app instead of sign-in.
    expect(screen.getByRole('link', { name: 'Back to the library' })).toBeInTheDocument();
  });

  it('polls again after the interval', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const mock = routeFetch({ '/livez': () => probe(200), '/readyz': () => probe(200) });
    renderAt('/status', null);
    await screen.findByText(/All systems ready/);
    expect(callsTo(mock, '/readyz')).toBe(1);
    await act(async () => {
      await vi.advanceTimersByTimeAsync(STATUS_POLL_MS + 100);
    });
    await waitFor(() => expect(callsTo(mock, '/readyz')).toBeGreaterThanOrEqual(2));
  });
});

describe('version page', () => {
  const info = { version: '1.4.0', buildTime: '2026-11-13T02:08:00Z', commit: 'a1b2c3d4e5f6', environment: 'production' };
  const infoRoutes = (docs: boolean) => ({
    '/app/info': () => ok(info),
    '/v3/api-docs': () => (docs ? new Response('{}') : fail(404)),
  });

  it('shows the build facts in Manila time and links the API docs only when they are served', async () => {
    routeFetch(infoRoutes(true));
    renderAt('/version', assistant);
    expect(await screen.findByText('1.4.0')).toBeInTheDocument();
    expect(screen.getByText('13 Nov 2026, 10:08')).toBeInTheDocument(); // 02:08Z is 10:08 in Manila
    expect(screen.getByText('a1b2c3d4e5f6')).toBeInTheDocument();
    expect(screen.getByText('production')).toBeInTheDocument();
    expect(await screen.findByRole('link', { name: /API documentation/ })).toHaveAttribute('href', '/swagger-ui.html');
    expect(screen.getByRole('link', { name: 'Service status' })).toHaveAttribute('href', '/status');
  });

  it('hides the API docs link when the docs are off', async () => {
    const mock = routeFetch(infoRoutes(false));
    renderAt('/version', staffUser);
    await screen.findByText('1.4.0');
    await waitFor(() => expect(callsTo(mock, '/v3/api-docs')).toBe(1));
    expect(screen.queryByRole('link', { name: /API documentation/ })).toBeNull();
  });

  it('shows a retry when the build info cannot be read', async () => {
    routeFetch({ '/app/info': () => fail(500), '/v3/api-docs': () => fail(404) });
    renderAt('/version', staffUser);
    expect(await screen.findByRole('button', { name: 'Try again' })).toBeInTheDocument();
  });

  it('keeps patrons out and puts Version in the staff nav only', async () => {
    routeFetch({ '/app/books/query': () => ok(pageOf([])), '/app/books/genre': () => ok({}) });
    renderAt('/version');
    expect(await screen.findByRole('heading', { name: 'Catalog' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Version' })).toBeNull();
  });

  it('adds a Version link for staff', async () => {
    routeFetch(infoRoutes(false));
    renderAt('/version', assistant);
    const nav = await screen.findByRole('navigation', { name: 'Primary' });
    expect(within(nav).getByRole('link', { name: 'Version' })).toBeInTheDocument();
  });
});

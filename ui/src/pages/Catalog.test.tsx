import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, vi } from 'vitest';
import { book, fail, ok, pageOf, renderAt, routeFetch, staffUser } from '../test/render';

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

/** URLs of the catalog queries issued so far, newest last. */
const queries = (mock: ReturnType<typeof routeFetch>) =>
  mock.mock.calls.map((c) => c[0]).filter((u) => u.startsWith('/app/books/query'));

function catalogRoutes(extra: Parameters<typeof routeFetch>[0] = {}) {
  return routeFetch({
    '/app/books/genre': () => ok({ 'Sci-fi': 2, Dystopian: 1 }),
    '/app/books/query': () => ok(pageOf([book(), book({ id: 2, title: '1984', author: 'George Orwell', genre: 'Dystopian', available: false })])),
    ...extra,
  });
}

describe('catalog', () => {
  it('shows result count and book cards with real text and availability', async () => {
    catalogRoutes();
    renderAt('/catalog');
    expect(await screen.findByText('2 books')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Dune/ })).toHaveAttribute('href', '/catalog/1');
    expect(screen.getByText('Available')).toBeInTheDocument();
    expect(screen.getByText('On loan')).toBeInTheDocument();
  });

  it('restores search, filters, sort and page from the URL', async () => {
    const mock = catalogRoutes();
    renderAt('/catalog?q=dune&genre=Sci-fi&min=10&available=true&sort=replacementCost,desc&size=24&page=2');
    await screen.findByText('2 books');
    expect(queries(mock)[0]).toContain('title=dune');
    expect(queries(mock)[0]).toContain('genre=Sci-fi');
    expect(queries(mock)[0]).toContain('minReplacementCost=10');
    expect(queries(mock)[0]).toContain('available=true');
    expect(queries(mock)[0]).toContain('page=2');
    expect(queries(mock)[0]).toContain('size=24');
    expect(queries(mock)[0]).toContain('sort=replacementCost%2Cdesc');
    expect(screen.getByLabelText('Search titles')).toHaveValue('dune');
    expect(screen.getByText('Search: dune')).toBeInTheDocument();
    expect(screen.getByText('Available only')).toBeInTheDocument();
  });

  it('waits 300 ms after typing, then searches once and returns to page one', async () => {
    const mock = catalogRoutes();
    renderAt('/catalog?page=3');
    await screen.findByText('2 books');
    const before = queries(mock).length;
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    await user.type(screen.getByLabelText('Search titles'), 'dune');
    expect(queries(mock)).toHaveLength(before); // nothing sent while typing
    await vi.advanceTimersByTimeAsync(300);
    await waitFor(() => expect(queries(mock)).toHaveLength(before + 1));
    const last = queries(mock).at(-1)!;
    expect(last).toContain('title=dune');
    expect(last).toContain('page=0');
  });

  it('removes one filter chip, and clear all removes every filter', async () => {
    const mock = catalogRoutes();
    const user = userEvent.setup();
    renderAt('/catalog?genre=Sci-fi&author=Herbert&page=1');
    await screen.findByText('2 books');
    await user.click(screen.getByRole('button', { name: /Genre: Sci-fi/ }));
    await waitFor(() => expect(queries(mock).at(-1)).not.toContain('genre='));
    expect(queries(mock).at(-1)).toContain('author=Herbert');
    expect(queries(mock).at(-1)).toContain('page=0');
    await user.click(screen.getByRole('button', { name: 'Clear all' }));
    await waitFor(() => expect(queries(mock).at(-1)).not.toContain('author='));
    expect(screen.queryByRole('button', { name: 'Clear all' })).toBeNull();
  });

  it('applying filters returns to page one', async () => {
    const mock = catalogRoutes();
    const user = userEvent.setup();
    renderAt('/catalog?page=2');
    await screen.findByText('2 books');
    await user.click(screen.getByRole('button', { name: /Filters/ }));
    await user.type(screen.getByLabelText('Author'), 'Orwell');
    await user.click(screen.getByLabelText('Available only'));
    await user.click(screen.getByRole('button', { name: 'Apply filters' }));
    await waitFor(() => expect(queries(mock).at(-1)).toContain('author=Orwell'));
    expect(queries(mock).at(-1)).toContain('available=true');
    expect(queries(mock).at(-1)).toContain('page=0');
  });

  it('rejects a minimum cost above the maximum before calling the API', async () => {
    const mock = catalogRoutes();
    const user = userEvent.setup();
    renderAt('/catalog');
    await screen.findByText('2 books');
    const calls = queries(mock).length;
    await user.click(screen.getByRole('button', { name: /Filters/ }));
    await user.type(screen.getByLabelText('Minimum cost'), '30');
    await user.type(screen.getByLabelText('Maximum cost'), '10');
    await user.click(screen.getByRole('button', { name: 'Apply filters' }));
    expect(screen.getByText(/minimum cost cannot be higher/)).toBeInTheDocument();
    expect(queries(mock)).toHaveLength(calls);
  });

  it('offers clear filters on an empty result', async () => {
    catalogRoutes({ '/app/books/query': () => ok(pageOf([], { totalElements: 0, totalPages: 0 })) });
    renderAt('/catalog?genre=Poetry');
    expect(await screen.findByText('No books match these filters.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Clear filters' })).toBeInTheDocument();
  });

  it('offers a manual retry when the catalog read fails, and does not retry on its own', async () => {
    let calls = 0;
    const mock = catalogRoutes({
      '/app/books/query': () => (++calls === 1 ? fail(500, { requestId: '3f2b8c1e-1111' }) : ok(pageOf([book()]))),
    });
    const user = userEvent.setup();
    renderAt('/catalog');
    expect(await screen.findByRole('alert')).toHaveTextContent('Reference: 3f2b8c1e');
    expect(queries(mock)).toHaveLength(1);
    await user.click(screen.getByRole('button', { name: 'Try again' }));
    expect(await screen.findByText('1 book')).toBeInTheDocument();
  });

  it('shows replacement cost on cards for staff only', async () => {
    catalogRoutes();
    const { unmount } = renderAt('/catalog');
    await screen.findByText('2 books');
    expect(screen.queryByText(/Cost 24.50/)).toBeNull();
    unmount();
    renderAt('/catalog', staffUser);
    expect((await screen.findAllByText(/Cost 24.50/)).length).toBeGreaterThan(0);
  });

  it('switches to the list view and keeps it in the URL', async () => {
    catalogRoutes();
    const user = userEvent.setup();
    renderAt('/catalog');
    await screen.findByText('2 books');
    await user.click(screen.getByRole('button', { name: 'List' }));
    expect(await screen.findByRole('table')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'List' })).toHaveAttribute('aria-pressed', 'true');
  });
});

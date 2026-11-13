import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, vi } from 'vitest';
import { book, fail, ok, pageOf, renderAt, routeFetch, staffUser } from '../test/render';

afterEach(() => vi.unstubAllGlobals());

const loan = (over: Record<string, unknown> = {}) => ({
  loanId: 81, borrowerUniversityId: '2025-4321', bookId: 42, bookTitle: '1984', borrowedAt: '2026-11-01T10:00:00',
  dueAt: '2020-01-01T10:00:00', returnedAt: null, status: 'ACTIVE', overdue: true, ...over,
});

/** Answers /app/loans by its filters: size=1 queries report counts, others return rows. */
function staffRoutes(extra: Parameters<typeof routeFetch>[0] = {}) {
  return routeFetch({
    '/app/books/stats': () => ok({ totalBooks: 87, totalReplacementCost: 1684.1, highestReplacementCostBook: null }),
    '/app/books/genre': () => ok({ 'Sci-fi': 3, Dystopian: 2 }),
    '/app/loans/activity': () => ok([{ weekStart: '2026-11-02', checkouts: 4, returns: 3 }, { weekStart: '2026-11-09', checkouts: 6, returns: 5 }]),
    '/app/loans/top-titles': () => ok([{ title: 'Dune', author: 'F. Herbert', checkouts: 7 }]),
    '/app/loans?': (url) => {
      const p = new URL(url, 'http://x').searchParams;
      if (p.get('size') === '1') {
        const n = p.get('overdue') ? 12 : p.get('status') === 'ACTIVE' ? 40 : p.get('status') === 'LOST' ? 3 : 30;
        return ok(pageOf([], { totalElements: n }));
      }
      return ok(pageOf([loan(), loan({ loanId: 80, borrowerUniversityId: '2025-1177', bookTitle: 'Dune' })], { size: 12 }));
    },
    ...extra,
  });
}

describe('staff dashboard', () => {
  it('shows KPI tiles from totalElements counts and links them to filtered loans', async () => {
    const mock = staffRoutes();
    renderAt('/dashboard', staffUser);
    expect(await screen.findByText('87')).toBeInTheDocument();
    expect(await screen.findByText('40')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Overdue/ })).toHaveAttribute('href', '/staff/loans?overdue=true');
    expect(screen.getByRole('link', { name: /Active loans/ })).toHaveAttribute('href', '/staff/loans?status=ACTIVE');
    // Counts must come from one-row pages, not from downloading loans.
    expect(mock.mock.calls.filter((c) => c[0].startsWith('/app/loans?') && c[0].includes('size=1&')).length).toBeGreaterThanOrEqual(4);
  });

  it('states the computed takeaway and gives every chart a table view', async () => {
    staffRoutes();
    const user = userEvent.setup();
    renderAt('/dashboard', staffUser);
    expect(await screen.findByText('12 of 40 active loans are overdue')).toBeInTheDocument();
    await screen.findByText(/Collection by genre/);
    for (const toggle of screen.getAllByRole('button', { name: 'View as table' })) await user.click(toggle);
    expect(screen.getAllByRole('table').length).toBeGreaterThanOrEqual(4);
  });

  it('lists the longest-overdue loans with an Open link', async () => {
    staffRoutes();
    renderAt('/dashboard', staffUser);
    const heading = await screen.findByRole('heading', { name: 'Needs attention' });
    const panel = heading.closest('div')!;
    expect(await within(panel).findByText('2025-4321')).toBeInTheDocument();
    expect(within(panel).getAllByRole('link', { name: /Open/ })[0]).toHaveAttribute('href', expect.stringContaining('/staff/loans?'));
  });

  it('keeps patrons out of staff routes', async () => {
    staffRoutes({
      '/app/books/query': () => ok(pageOf([book()])),
      '/app/books/genre': () => ok({}),
    });
    renderAt('/dashboard');
    expect(await screen.findByRole('heading', { name: 'Catalog' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Dashboard' })).toBeNull();
  });
});

describe('loan management', () => {
  it('reads filters from the URL and sends them to the API', async () => {
    const mock = staffRoutes();
    renderAt('/staff/loans?status=ACTIVE&overdue=true&borrower=2025-4321&page=1', staffUser);
    await screen.findByText('2025-4321', { selector: 'td' });
    const url = mock.mock.calls.map((c) => c[0]).filter((u) => u.startsWith('/app/loans?')).at(-1)!;
    expect(url).toContain('status=ACTIVE');
    expect(url).toContain('overdue=true');
    expect(url).toContain('borrower=2025-4321');
    expect(url).toContain('page=1');
  });

  it('shows days late from the server overdue flag and opens a detail panel', async () => {
    staffRoutes();
    const user = userEvent.setup();
    renderAt('/staff/loans', staffUser);
    await user.click((await screen.findAllByRole('button', { name: /Details for loan 81/ }))[0]!);
    const panel = await screen.findByRole('complementary', { name: 'Loan 81 details' });
    expect(within(panel).getByRole('button', { name: 'Mark as lost' })).toBeInTheDocument();
  });

  it('states the consequence in a dialog before marking a loan lost', async () => {
    const mock = staffRoutes({ '/app/loans/81/lost': () => ok(loan({ status: 'LOST', lostAt: '2026-11-14T10:00:00' })) });
    const user = userEvent.setup();
    renderAt('/staff/loans?open=81', staffUser);
    await user.click(await screen.findByRole('button', { name: 'Mark as lost' }));
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByText('The loan closes and this copy can no longer be borrowed.')).toBeInTheDocument();
    await user.click(within(dialog).getByRole('button', { name: 'Mark as lost' }));
    expect(await screen.findByText('Marked 1984 as lost.')).toBeInTheDocument();
    expect(mock.mock.calls.some((c) => c[0] === '/app/loans/81/lost' && c[1]?.method === 'PUT')).toBe(true);
  });

  it('offers no Mark as lost for a loan that is not active', async () => {
    staffRoutes({ '/app/loans?': () => ok(pageOf([loan({ status: 'RETURNED', overdue: false, returnedAt: '2026-11-05T10:00:00' })])) });
    renderAt('/staff/loans?open=81', staffUser);
    await screen.findByRole('complementary');
    expect(screen.queryByRole('button', { name: 'Mark as lost' })).toBeNull();
  });

  it('shows not-found for an unknown borrower and a permission message when out of scope', async () => {
    staffRoutes({ '/app/loans?': () => fail(404) });
    const { unmount } = renderAt('/staff/loans?borrower=9999-9999', staffUser);
    expect(await screen.findByText('Borrower not found.')).toBeInTheDocument();
    unmount();
    staffRoutes({ '/app/loans?': () => fail(403) });
    renderAt('/staff/loans?borrower=0000-0000', staffUser);
    expect(await screen.findByText(/do not have permission/)).toBeInTheDocument();
  });

  it('explains a stale mark-lost conflict from its stable code', async () => {
    staffRoutes({ '/app/loans/81/lost': () => fail(409, { code: 'LOAN_NOT_ACTIVE' }) });
    const user = userEvent.setup();
    renderAt('/staff/loans?open=81', staffUser);
    await user.click(await screen.findByRole('button', { name: 'Mark as lost' }));
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Mark as lost' }));
    expect(await screen.findByText('This loan is no longer active.')).toBeInTheDocument();
  });
});

describe('inventory', () => {
  const inventoryRoutes = (extra: Parameters<typeof routeFetch>[0] = {}) =>
    routeFetch({
      '/app/books/genre': () => ok({ 'Sci-fi': 1 }),
      '/app/books/query': () => ok(pageOf([book()])),
      ...extra,
    });

  it('lists copies with edit and delete for staff', async () => {
    inventoryRoutes();
    renderAt('/inventory', staffUser);
    expect(await screen.findByRole('button', { name: /Edit Dune/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Delete Dune/ })).toBeInTheDocument();
  });

  it('validates the form, maps server field errors, and saves a new copy with trimmed values', async () => {
    const mock = inventoryRoutes({ '/app/books/add': () => ok(book({ id: 9, title: 'Emma' })) });
    const user = userEvent.setup();
    renderAt('/inventory', staffUser);
    await user.click(await screen.findByRole('button', { name: 'Add copy' }));
    await user.click(within(screen.getByRole('complementary')).getByRole('button', { name: 'Add copy' }));
    expect(await screen.findByText('Title cannot be empty.')).toBeInTheDocument();
    await waitFor(() => expect(screen.getByLabelText('Title')).toHaveFocus());
    const sheet = within(screen.getByRole('complementary'));
    await user.type(sheet.getByLabelText('Title'), '  Emma ');
    await user.type(sheet.getByLabelText('Author'), 'Jane Austen');
    await user.type(sheet.getByLabelText('Genre'), 'Classic');
    await user.type(sheet.getByLabelText('Replacement cost'), '12.5');
    await user.click(sheet.getByRole('button', { name: 'Add copy' }));
    expect(await screen.findByText('Added Emma.')).toBeInTheDocument();
    const post = mock.mock.calls.find((c) => c[0] === '/app/books/add')!;
    expect(JSON.parse(post[1]!.body as string)).toEqual({ title: 'Emma', author: 'Jane Austen', genre: 'Classic', replacementCost: 12.5 });
  });

  it('maps a server field error onto its field', async () => {
    inventoryRoutes({ '/app/books/1': () => fail(400, { fieldErrors: { title: 'Title cannot exceed 100 characters' } }) });
    const user = userEvent.setup();
    renderAt('/inventory', staffUser);
    await user.click(await screen.findByRole('button', { name: /Edit Dune/ }));
    await user.click(within(screen.getByRole('complementary')).getByRole('button', { name: 'Save changes' }));
    expect(await screen.findByText('Title cannot exceed 100 characters')).toBeInTheDocument();
  });

  it('explains that a copy with loan history must be kept', async () => {
    inventoryRoutes({ '/app/books/1': (_u, init) => (init?.method === 'DELETE' ? fail(409, { code: 'RESOURCE_HAS_HISTORY' }) : ok(book())) });
    const user = userEvent.setup();
    renderAt('/inventory', staffUser);
    await user.click(await screen.findByRole('button', { name: /Delete Dune/ }));
    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: 'Delete copy' }));
    expect(await within(dialog).findByText('This copy has loan history and must be kept.')).toBeInTheDocument();
  });

  it('shows the empty state for a collection with no copies', async () => {
    inventoryRoutes({ '/app/books/query': () => ok(pageOf([], { totalElements: 0 })) });
    renderAt('/inventory', staffUser);
    expect(await screen.findByText('No copies yet.')).toBeInTheDocument();
  });
});

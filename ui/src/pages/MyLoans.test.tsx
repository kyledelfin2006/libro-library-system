import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, vi } from 'vitest';
import { fail, ok, pageOf, renderAt, routeFetch, staffUser } from '../test/render';

afterEach(() => vi.unstubAllGlobals());

const loan = (over: Record<string, unknown> = {}) => ({
  loanId: 5, borrowerUniversityId: '2025-4321', bookId: 9, bookTitle: 'Emma', borrowedAt: '2026-11-01T10:00:00',
  dueAt: '2099-01-15T10:00:00', returnedAt: null, status: 'ACTIVE', overdue: false, ...over,
});

function routes(over: Parameters<typeof routeFetch>[0] = {}, loans: object[] = [loan(), loan({ loanId: 6, bookTitle: 'Dune' })]) {
  return routeFetch({
    '/app/loans/me?page=0&size=100': () => ok(pageOf(loans)),
    '/app/loans/me': () => ok(pageOf(loans, { size: 12 })),
    ...over,
  });
}

describe('my loans', () => {
  it('shows loan slots and current loans with a Return action', async () => {
    routes();
    renderAt('/loans');
    expect(await screen.findByText('2 of 3 loan slots used.')).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: 'Current (2)' })).toHaveAttribute('aria-selected', 'true');
    expect(screen.getAllByRole('button', { name: /^Return/ })).toHaveLength(2);
  });

  it('takes overdue from the server flag and locks free slots', async () => {
    routes({}, [loan({ overdue: true, dueAt: '2020-01-01T10:00:00' })]);
    renderAt('/loans');
    expect(await screen.findByText('Return your overdue book to borrow.')).toBeInTheDocument();
    expect(screen.getAllByText('Overdue').length).toBeGreaterThan(0);
  });

  it('does not mark a loan overdue from the clock when the server says it is not', async () => {
    routes({}, [loan({ overdue: false, dueAt: '2020-01-01T10:00:00' })]);
    renderAt('/loans');
    await screen.findByText('1 of 3 loan slots used.');
    expect(screen.queryByText('Overdue')).toBeNull();
  });

  it('confirms in a dialog naming the book, then returns and announces it', async () => {
    const mock = routes({
      '/app/loans/5/return': () => ok(loan({ status: 'RETURNED', returnedAt: '2026-11-14T15:30:00' })),
    });
    const user = userEvent.setup();
    renderAt('/loans');
    await user.click((await screen.findAllByRole('button', { name: 'Return Emma' }))[0]!);
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByRole('heading', { name: 'Return Emma?' })).toBeInTheDocument();
    await user.click(within(dialog).getByRole('button', { name: 'Return book' }));
    await waitFor(() => expect(screen.getByText(/^Returned Emma on Sat 14 Nov 2026, 15:30 Manila time\./)).toBeInTheDocument());
    expect(mock.mock.calls.some((c) => c[0] === '/app/loans/5/return' && c[1]?.method === 'PUT')).toBe(true);
  });

  it('shows "not found" for a loan that is not the borrower\'s, without confirming it exists', async () => {
    routes({ '/app/loans/5/return': () => fail(404, { details: 'Loan 5 belongs to someone else' }) });
    const user = userEvent.setup();
    renderAt('/loans');
    await user.click((await screen.findAllByRole('button', { name: 'Return Emma' }))[0]!);
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Return book' }));
    expect(await screen.findByText('This loan could not be found.')).toBeInTheDocument();
    expect(screen.queryByText(/belongs to someone else/)).toBeNull();
  });

  it('gives staff their own history with no Return button', async () => {
    routes({}, [loan({ borrowerUniversityId: '2020-0001' })]);
    renderAt('/loans', staffUser);
    expect(await screen.findByText('1 of 3 loan slots used.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /^Return/ })).toBeNull();
  });

  it('pages and sorts history with only the API-allowed sort fields', async () => {
    const mock = routes({}, [loan({ status: 'RETURNED', returnedAt: '2026-11-05T10:00:00' })]);
    const user = userEvent.setup();
    renderAt('/loans');
    await user.click(await screen.findByRole('tab', { name: 'History' }));
    await screen.findAllByText('Returned');
    const select = screen.getByLabelText('Sort by');
    const values = within(select).getAllByRole('option').map((o) => (o as HTMLOptionElement).value.split(',')[0]);
    expect(new Set(values)).toEqual(new Set(['borrowedAt', 'dueAt', 'returnedAt', 'status']));
    await user.selectOptions(select, 'dueAt,asc');
    await waitFor(() => expect(mock.mock.calls.some((c) => c[0].includes('sort=dueAt%2Casc'))).toBe(true));
  });

  it('offers the lifecycle diagram behind a disclosure', async () => {
    routes();
    renderAt('/loans');
    const summary = await screen.findByText('How borrowing works');
    expect(summary.closest('details')).not.toHaveAttribute('open');
  });

  it('offers retry when loans cannot be read', async () => {
    routes({ '/app/loans/me?page=0&size=100': () => fail(500) });
    renderAt('/loans');
    expect(await screen.findByRole('button', { name: 'Try again' })).toBeInTheDocument();
  });
});

import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, vi } from 'vitest';
import { book, fail, ok, pageOf, renderAt, routeFetch, staffUser } from '../test/render';

afterEach(() => vi.unstubAllGlobals());

const loan = (over: Record<string, unknown> = {}) => ({
  loanId: 5, bookId: 9, bookTitle: 'Emma', borrowedAt: '2026-11-01T10:00:00', dueAt: '2099-01-01T10:00:00',
  status: 'ACTIVE', overdue: false, ...over,
});

function routes(opts: { b?: object; loans?: object[]; borrow?: () => Response } = {}) {
  return routeFetch({
    '/app/books/1': () => ok(opts.b ?? book()),
    '/app/loans/me': () => ok(pageOf(opts.loans ?? [])),
    '/app/loans/1': () => (opts.borrow ?? (() => ok(loan({ bookId: 1, bookTitle: 'Dune' }))))(),
  });
}

describe('book detail', () => {
  it('renders the catalog card with copy number, status and replacement cost', async () => {
    routes();
    renderAt('/catalog/1');
    expect(await screen.findByRole('heading', { name: 'Dune' })).toBeInTheDocument();
    expect(screen.getByText('Copy no.')).toBeInTheDocument();
    expect(screen.getByText('24.50')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Borrow this copy' })).toBeEnabled();
    expect(await screen.findByText('0 of 3 loan slots used.')).toBeInTheDocument();
  });

  it('borrows and announces the due date', async () => {
    const mock = routes();
    const user = userEvent.setup();
    renderAt('/catalog/1');
    await user.click(await screen.findByRole('button', { name: 'Borrow this copy' }));
    expect(await screen.findByRole('status', { name: '' })).toBeInTheDocument();
    await waitFor(() => expect(screen.getByText(/^Borrowed\. Due /)).toBeInTheDocument());
    expect(mock.mock.calls.some((c) => c[0] === '/app/loans/1' && c[1]?.method === 'POST')).toBe(true);
  });

  it.each([
    ['LOAN_COPY_UNAVAILABLE', 'This copy is on loan. Check back after it is returned.'],
    ['LOAN_BORROWER_OVERDUE', 'Return your overdue book before you borrow another.'],
    ['LOAN_LIMIT_REACHED', 'You have 3 books checked out, the most the library allows. Return one to borrow another.'],
  ])('explains a %s conflict from the stable code', async (code, copy) => {
    routes({ borrow: () => fail(409, { code, details: 'raw server text' }) });
    const user = userEvent.setup();
    renderAt('/catalog/1');
    await user.click(await screen.findByRole('button', { name: 'Borrow this copy' }));
    expect(await screen.findByText(copy)).toBeInTheDocument();
    expect(screen.queryByText('raw server text')).toBeNull();
  });

  it('disables Borrow with the reason when the borrower has an overdue loan', async () => {
    routes({ loans: [loan({ overdue: true, dueAt: '2020-01-01T10:00:00' })] });
    renderAt('/catalog/1');
    expect(await screen.findByText('Return your overdue book before you borrow another.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Borrow this copy' })).toBeDisabled();
    expect(screen.getByText('Return your overdue book to borrow.')).toBeInTheDocument();
  });

  it('disables Borrow when the borrower already has three active loans', async () => {
    routes({ loans: [loan({ loanId: 1 }), loan({ loanId: 2 }), loan({ loanId: 3 })] });
    renderAt('/catalog/1');
    expect(await screen.findByText(/most the library allows/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Borrow this copy' })).toBeDisabled();
  });

  it('disables Borrow for a copy that is on loan', async () => {
    routes({ b: book({ available: false }) });
    renderAt('/catalog/1');
    expect(await screen.findByText('This copy is on loan. Check back after it is returned.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Borrow this copy' })).toBeDisabled();
  });

  it('shows no action for a lost copy', async () => {
    routes({ b: book({ copyStatus: 'LOST', available: false }) });
    renderAt('/catalog/1');
    expect(await screen.findByText('This copy was reported lost.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Borrow this copy' })).toBeNull();
  });

  it('gives staff no Borrow control and never loads patron loans', async () => {
    const mock = routes();
    renderAt('/catalog/1', staffUser);
    expect(await screen.findByRole('heading', { name: 'Dune' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Borrow this copy' })).toBeNull();
    expect(mock.mock.calls.some((c) => c[0].startsWith('/app/loans/me'))).toBe(false);
  });

  it('shows a not-found state for an unknown copy', async () => {
    routeFetch({ '/app/books/1': () => fail(404) });
    renderAt('/catalog/1');
    expect(await screen.findByRole('alert')).toHaveTextContent('could not be found');
  });
});

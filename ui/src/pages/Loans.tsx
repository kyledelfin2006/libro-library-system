import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { ApiError } from '../api/client';
import { listLoans, markLoanLost } from '../api/staff';
import type { LoanResponse, LoanStatus } from '../api/types';
import { useAuth } from '../auth/AuthProvider';
import { can } from '../auth/permissions';
import { useToast } from '../components/Toast';
import { Button, Chip, Dialog, EmptyState, ErrorState, Pagination, Skeleton, type ChipTone } from '../components/ui';
import { daysUntil, formatDate, formatDateTime, relativeDays } from '../lib/dates';
import { describeError } from '../lib/forms';

const STATUSES: LoanStatus[] = ['ACTIVE', 'RETURNED', 'LOST'];
const PAGE_SIZES = [12, 24, 48, 100];

/** Overdue comes from the server flag; the chip shows the days late only as a convenience for ACTIVE overdue loans. */
export function LoanStatusChip({ loan }: { loan: LoanResponse }) {
  if (loan.status === 'ACTIVE' && loan.overdue) return <Chip tone="overdue">Overdue ({Math.max(1, -daysUntil(loan.dueAt))} d)</Chip>;
  const tone: ChipTone = loan.status === 'ACTIVE' ? 'active' : loan.status === 'RETURNED' ? 'returned' : 'lost';
  return <Chip tone={tone}>{loan.status === 'ACTIVE' ? 'Active' : loan.status === 'RETURNED' ? 'Returned' : 'Lost'}</Chip>;
}

/** Loan management for library staff: filters live in the URL; a row opens a detail panel with Mark as lost. */
export function Loans() {
  const { user } = useAuth();
  const { notify } = useToast();
  const queryClient = useQueryClient();
  const [sp, setSp] = useSearchParams();
  const statusParam = sp.get('status');
  const status = STATUSES.find((s) => s === statusParam);
  const overdue = sp.get('overdue') === 'true';
  const borrower = sp.get('borrower') ?? '';
  const page = Math.max(0, Number(sp.get('page')) || 0);
  const size = PAGE_SIZES.includes(Number(sp.get('size'))) ? Number(sp.get('size')) : 12;
  const openId = Number(sp.get('open')) || null;

  const [borrowerDraft, setBorrowerDraft] = useState(borrower);
  const [lostTarget, setLostTarget] = useState<LoanResponse | null>(null);

  /** Writes filters to the URL; any filter change returns to page one. */
  const setParams = (changes: Record<string, string | null>, keepPage = false) => {
    const next = new URLSearchParams(sp);
    for (const [k, v] of Object.entries(changes)) {
      if (v === null || v === '') next.delete(k);
      else next.set(k, v);
    }
    if (!keepPage) next.delete('page');
    setSp(next);
  };

  const filters = { ...(status ? { status } : {}), ...(overdue ? { overdue: true } : {}), ...(borrower ? { borrower } : {}), page, size, sort: 'dueAt,asc' };
  const loans = useQuery({ queryKey: ['staffLoans', filters], queryFn: () => listLoans(filters), placeholderData: keepPreviousData });
  const selected = loans.data?.content.find((l) => l.loanId === openId) ?? null;

  const lost = useMutation({
    mutationFn: (loan: LoanResponse) => markLoanLost(loan.loanId),
    onSuccess: (loan) => notify(`Marked ${loan.bookTitle} as lost.`),
    onError: (e) =>
      notify(
        e instanceof ApiError && e.code === 'LOAN_NOT_ACTIVE' ? 'This loan is no longer active.'
        : e instanceof ApiError && e.status === 404 ? 'This loan could not be found.'
        : e instanceof ApiError && e.status === 403 ? 'You do not have permission to resolve this loan.'
        : describeError(e),
        'error',
      ),
    onSettled: async () => {
      setLostTarget(null);
      await Promise.all(['staffLoans', 'loanCount', 'overdueLoans', 'bookStats', 'books', 'book'].map((k) => queryClient.invalidateQueries({ queryKey: [k] })));
    },
  });

  const hasFilters = !!status || overdue || !!borrower;
  const canLose = can(user?.userRole, 'markLoanLost');

  return (
    <section>
      <h1>Loans</h1>
      <form
        className="toolbar"
        onSubmit={(e) => {
          e.preventDefault();
          setParams({ borrower: borrowerDraft.trim() });
        }}
      >
        <div className="field">
          <label htmlFor="loan-status">Status</label>
          <select id="loan-status" value={status ?? ''} onChange={(e) => setParams({ status: e.target.value })}>
            <option value="">All</option>
            {STATUSES.map((s) => <option key={s} value={s}>{s[0] + s.slice(1).toLowerCase()}</option>)}
          </select>
        </div>
        <label className="checkbox">
          <input type="checkbox" checked={overdue} onChange={(e) => setParams({ overdue: e.target.checked ? 'true' : null })} /> Overdue only
        </label>
        <div className="field">
          <label htmlFor="loan-borrower">Borrower ID</label>
          <input id="loan-borrower" value={borrowerDraft} onChange={(e) => setBorrowerDraft(e.target.value)} placeholder="2025-4321" inputMode="numeric" />
        </div>
        <Button type="submit" variant="secondary">Search</Button>
        {hasFilters && (
          <Button variant="secondary" onClick={() => { setBorrowerDraft(''); setSp(new URLSearchParams()); }}>Clear</Button>
        )}
      </form>

      <div className="split">
        <div>
          {loans.isPending ? (
            <Skeleton height={240} />
          ) : loans.isError && !loans.data ? (
            <LoanError error={loans.error} onRetry={() => void loans.refetch()} />
          ) : loans.data && loans.data.content.length === 0 ? (
            <EmptyState title="No loans match these filters.">Clear a filter or check the borrower ID.</EmptyState>
          ) : loans.data ? (
            <>
              <table className="loan-table record-cards">
                <thead>
                  <tr>
                    <th scope="col">Loan</th>
                    <th scope="col">Borrower</th>
                    <th scope="col">Book</th>
                    <th scope="col">Borrowed</th>
                    <th scope="col">Due</th>
                    <th scope="col">Status</th>
                    <th scope="col"><span className="visually-hidden">Details</span></th>
                  </tr>
                </thead>
                <tbody>
                  {loans.data.content.map((l) => (
                    <tr key={l.loanId} aria-selected={l.loanId === openId} className={l.loanId === openId ? 'row-selected' : undefined}>
                      <th scope="row" data-label="Loan">{l.loanId}</th>
                      <td data-label="Borrower">{l.borrowerUniversityId}</td>
                      <td data-label="Book">{l.bookTitle}</td>
                      <td data-label="Borrowed">{formatDate(l.borrowedAt)}</td>
                      <td data-label="Due">{formatDate(l.dueAt)}</td>
                      <td data-label="Status"><LoanStatusChip loan={l} /></td>
                      <td>
                        <Button variant="secondary" onClick={() => setParams({ open: String(l.loanId) }, true)}>
                          Details<span className="visually-hidden"> for loan {l.loanId}</span>
                        </Button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
              <Pagination
                page={loans.data.number}
                size={loans.data.size}
                total={loans.data.totalElements}
                onPage={(p) => setParams({ page: p > 0 ? String(p) : null }, true)}
                onSize={(s) => setParams({ size: s === 12 ? null : String(s) })}
              />
            </>
          ) : null}
        </div>

        {selected && (
          <aside className="side-panel" aria-label={`Loan ${selected.loanId} details`}>
            <h2>Loan {selected.loanId}</h2>
            <dl className="catalog-card">
              <div><dt>Book</dt><dd>{selected.bookTitle}</dd></div>
              <div><dt>Borrower</dt><dd>{selected.borrowerUniversityId}</dd></div>
              <div><dt>Borrowed</dt><dd>{formatDateTime(selected.borrowedAt)}</dd></div>
              <div><dt>Due</dt><dd>{formatDateTime(selected.dueAt)}{selected.status === 'ACTIVE' ? ` (${relativeDays(daysUntil(selected.dueAt))})` : ''}</dd></div>
              {selected.returnedAt && <div><dt>Returned</dt><dd>{formatDateTime(selected.returnedAt)}</dd></div>}
              {selected.lostAt && <div><dt>Lost</dt><dd>{formatDateTime(selected.lostAt)}</dd></div>}
              <div><dt>Status</dt><dd><LoanStatusChip loan={selected} /></dd></div>
            </dl>
            <div className="form-actions">
              <Button variant="secondary" onClick={() => { setBorrowerDraft(selected.borrowerUniversityId ?? ''); setParams({ borrower: selected.borrowerUniversityId ?? null, open: null }); }}>
                Borrower's loans
              </Button>
              {canLose && selected.status === 'ACTIVE' && (
                <Button variant="danger" onClick={() => setLostTarget(selected)}>Mark as lost</Button>
              )}
              <Button variant="secondary" onClick={() => setParams({ open: null }, true)}>Close</Button>
            </div>
          </aside>
        )}
      </div>

      <Dialog
        open={lostTarget !== null}
        title={`Mark ${lostTarget?.bookTitle ?? 'this copy'} as lost?`}
        confirmLabel="Mark as lost"
        danger
        busy={lost.isPending}
        onConfirm={() => lostTarget && lost.mutate(lostTarget)}
        onClose={() => !lost.isPending && setLostTarget(null)}
      >
        <p>The loan closes and this copy can no longer be borrowed.</p>
      </Dialog>
    </section>
  );
}

/** Staff-facing error copy: a bad borrower ID is not found, an out-of-scope one is a permission message. */
function LoanError({ error, onRetry }: { error: unknown; onRetry: () => void }) {
  if (error instanceof ApiError && error.status === 404) {
    return <EmptyState title="Borrower not found.">Check the university ID and try again.</EmptyState>;
  }
  if (error instanceof ApiError && error.status === 403) {
    return <EmptyState title="You do not have permission to view this borrower's loans." />;
  }
  return <ErrorState error={error} onRetry={onRetry} />;
}

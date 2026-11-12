import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { LOAN_SORT_FIELDS, myLoanHistory, myLoans, returnLoan, type LoanSortField } from '../api/loans';
import type { LoanResponse } from '../api/types';
import { useAuth } from '../auth/AuthProvider';
import { can } from '../auth/permissions';
import { LoanLifecycle, LoanSlots, type SlotLoan } from '../components/LoanSlots';
import { useToast } from '../components/Toast';
import { Chip, Dialog, EmptyState, ErrorState, Pagination, Skeleton, type ChipTone } from '../components/ui';
import { daysUntil, formatDate, formatDateTime, relativeDays } from '../lib/dates';
import { returnErrorMessage } from '../lib/loanMessages';

const SORT_LABELS: Record<LoanSortField, string> = {
  borrowedAt: 'Borrowed date',
  dueAt: 'Due date',
  returnedAt: 'Returned date',
  status: 'Status',
};

/** Status chip. Overdue comes from the server's flag, never from the browser clock. */
function LoanStatus({ loan }: { loan: LoanResponse }) {
  if (loan.status === 'ACTIVE' && loan.overdue) return <Chip tone="overdue">Overdue</Chip>;
  const tone: ChipTone = loan.status === 'ACTIVE' ? 'active' : loan.status === 'RETURNED' ? 'returned' : 'lost';
  const label = loan.status === 'ACTIVE' ? 'Active' : loan.status === 'RETURNED' ? 'Returned' : 'Lost';
  return <Chip tone={tone}>{label}</Chip>;
}

/** The caller's loans: loan slots, current loans with Return, paginated history, and the lifecycle diagram. */
export function MyLoans() {
  const { user } = useAuth();
  const { notify } = useToast();
  const queryClient = useQueryClient();
  const canReturn = can(user?.userRole, 'returnLoan');
  const [tab, setTab] = useState<'current' | 'history'>('current');
  const [returning, setReturning] = useState<LoanResponse | null>(null);

  const current = useQuery({ queryKey: ['myLoans'], queryFn: () => myLoans() });
  const active = (current.data?.content ?? []).filter((l) => l.status === 'ACTIVE');
  const slots: SlotLoan[] = active.map((l) => ({ title: l.bookTitle, daysLeft: daysUntil(l.dueAt), overdue: l.overdue }));

  const giveBack = useMutation({
    mutationFn: (loan: LoanResponse) => returnLoan(loan.loanId),
    onSuccess: (loan) => notify(`Returned ${loan.bookTitle}${loan.returnedAt ? ` on ${formatDateTime(loan.returnedAt)} Manila time` : ''}.`),
    onError: (e) => notify(returnErrorMessage(e), 'error'),
    onSettled: async () => {
      setReturning(null);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['myLoans'] }),
        queryClient.invalidateQueries({ queryKey: ['loanHistory'] }),
        queryClient.invalidateQueries({ queryKey: ['books'] }),
        queryClient.invalidateQueries({ queryKey: ['book'] }),
      ]);
    },
  });

  return (
    <section>
      <h1>My loans</h1>
      {current.isPending ? (
        <Skeleton height={96} />
      ) : current.isError ? (
        <ErrorState error={current.error} onRetry={() => void current.refetch()} />
      ) : (
        <LoanSlots loans={slots} />
      )}

      <div className="tabs" role="tablist" aria-label="Loans">
        {(['current', 'history'] as const).map((t) => (
          <button
            key={t}
            type="button"
            role="tab"
            id={`tab-${t}`}
            aria-selected={tab === t}
            aria-controls={`panel-${t}`}
            className="tab"
            onClick={() => setTab(t)}
          >
            {t === 'current' ? `Current (${active.length})` : 'History'}
          </button>
        ))}
      </div>

      {tab === 'current' ? (
        <div role="tabpanel" id="panel-current" aria-labelledby="tab-current">
          {current.isSuccess && active.length === 0 ? (
            <EmptyState title="No books checked out.">
              <Link to="/catalog">Browse the catalog</Link> to borrow a book.
            </EmptyState>
          ) : (
            current.isSuccess && (
              <table className="loan-table record-cards">
                <thead>
                  <tr>
                    <th scope="col">Book</th>
                    <th scope="col">Borrowed</th>
                    <th scope="col">Due</th>
                    <th scope="col">Status</th>
                    {canReturn && <th scope="col"><span className="visually-hidden">Actions</span></th>}
                  </tr>
                </thead>
                <tbody>
                  {active.map((l) => (
                    <tr key={l.loanId}>
                      <th scope="row" data-label="Book"><Link to={`/catalog/${l.bookId}`}>{l.bookTitle}</Link></th>
                      <td data-label="Borrowed">{formatDate(l.borrowedAt)}</td>
                      <td data-label="Due">{formatDate(l.dueAt)} ({relativeDays(daysUntil(l.dueAt))})</td>
                      <td data-label="Status"><LoanStatus loan={l} /></td>
                      {canReturn && (
                        <td>
                          <button type="button" className="btn btn-secondary" onClick={() => setReturning(l)}>
                            Return<span className="visually-hidden"> {l.bookTitle}</span>
                          </button>
                        </td>
                      )}
                    </tr>
                  ))}
                </tbody>
              </table>
            )
          )}
        </div>
      ) : (
        <div role="tabpanel" id="panel-history" aria-labelledby="tab-history">
          <History />
        </div>
      )}

      <details className="how-it-works">
        <summary>How borrowing works</summary>
        <LoanLifecycle />
      </details>

      <Dialog
        open={returning !== null}
        title={`Return ${returning?.bookTitle ?? ''}?`}
        confirmLabel="Return book"
        busy={giveBack.isPending}
        onConfirm={() => returning && giveBack.mutate(returning)}
        onClose={() => !giveBack.isPending && setReturning(null)}
      >
        <p>This closes the loan and makes the copy available to others.</p>
      </Dialog>
    </section>
  );
}

/** Paginated, sortable loan history (all statuses), sorted by the API's allowed fields only. */
function History() {
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(12);
  const [sort, setSort] = useState<LoanSortField>('borrowedAt');
  const [dir, setDir] = useState<'asc' | 'desc'>('desc');
  const history = useQuery({
    queryKey: ['loanHistory', page, size, sort, dir],
    queryFn: () => myLoanHistory(page, size, sort, dir),
    placeholderData: keepPreviousData,
  });

  return (
    <>
      <div className="toolbar">
        <div className="field">
          <label htmlFor="loan-sort">Sort by</label>
          <select
            id="loan-sort"
            value={`${sort},${dir}`}
            onChange={(e) => {
              const [s, d] = e.target.value.split(',') as [LoanSortField, 'asc' | 'desc'];
              setSort(s);
              setDir(d);
              setPage(0);
            }}
          >
            {LOAN_SORT_FIELDS.flatMap((f) => [
              <option key={`${f}d`} value={`${f},desc`}>{SORT_LABELS[f]}, newest first</option>,
              <option key={`${f}a`} value={`${f},asc`}>{SORT_LABELS[f]}, oldest first</option>,
            ])}
          </select>
        </div>
      </div>
      {history.isPending ? (
        <Skeleton height={160} />
      ) : history.isError && !history.data ? (
        <ErrorState error={history.error} onRetry={() => void history.refetch()} />
      ) : history.data && history.data.content.length === 0 ? (
        <EmptyState title="No loan history yet." />
      ) : history.data ? (
        <>
          <table className="loan-table record-cards">
            <thead>
              <tr>
                <th scope="col">Book</th>
                <th scope="col">Borrowed</th>
                <th scope="col">Due</th>
                <th scope="col">Returned</th>
                <th scope="col">Status</th>
              </tr>
            </thead>
            <tbody>
              {history.data.content.map((l) => (
                <tr key={l.loanId}>
                  <th scope="row" data-label="Book"><Link to={`/catalog/${l.bookId}`}>{l.bookTitle}</Link></th>
                  <td data-label="Borrowed">{formatDate(l.borrowedAt)}</td>
                  <td data-label="Due">{formatDate(l.dueAt)}</td>
                  <td data-label="Returned">{l.returnedAt ? formatDate(l.returnedAt) : '-'}</td>
                  <td data-label="Status"><LoanStatus loan={l} /></td>
                </tr>
              ))}
            </tbody>
          </table>
          <Pagination
            page={history.data.number}
            size={history.data.size}
            total={history.data.totalElements}
            onPage={setPage}
            onSize={(s) => {
              setSize(s);
              setPage(0);
            }}
          />
        </>
      ) : null}
    </>
  );
}

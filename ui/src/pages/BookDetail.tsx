import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';
import { getBook } from '../api/books';
import { ApiError } from '../api/client';
import { borrowBook, myLoans } from '../api/loans';
import { useAuth } from '../auth/AuthProvider';
import { can } from '../auth/permissions';
import { BookBadge } from '../components/BookBadge';
import { BookCover } from '../components/BookCover';
import { cover, formatCost } from '../components/BookViews';
import { LoanSlots, type SlotLoan } from '../components/LoanSlots';
import { useToast } from '../components/Toast';
import { Button, Chip, ErrorState, Skeleton } from '../components/ui';
import { daysUntil } from '../lib/dates';
import { BLOCK_COPY, borrowBlock, borrowErrorMessage, borrowSuccessMessage } from '../lib/loanMessages';

/** Book detail: a ruled "catalog card" panel, the borrow action for patrons, and the loan-slot diagram. */
export function BookDetail() {
  const { id } = useParams();
  const bookId = Number(id);
  const { user } = useAuth();
  const { notify } = useToast();
  const queryClient = useQueryClient();
  const patron = can(user?.userRole, 'borrow');

  const book = useQuery({ queryKey: ['book', bookId], queryFn: () => getBook(bookId), enabled: Number.isInteger(bookId) });
  const loans = useQuery({ queryKey: ['myLoans'], queryFn: () => myLoans(), enabled: patron });

  const borrow = useMutation({
    mutationFn: () => borrowBook(bookId),
    onSuccess: (loan) => notify(borrowSuccessMessage(loan)),
    onError: (e) => notify(borrowErrorMessage(e), 'error'),
    // Refresh the copy and loans after success or a 409, since availability may have changed under us.
    onSettled: () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: ['book', bookId] }),
        queryClient.invalidateQueries({ queryKey: ['myLoans'] }),
        queryClient.invalidateQueries({ queryKey: ['books'] }),
      ]),
  });

  if (!Number.isInteger(bookId)) return <ErrorState error={new ApiError(404, null, 'Not found')} />;
  if (book.isPending) return <Skeleton height={360} />;
  if (book.isError) return <ErrorState error={book.error} onRetry={() => void book.refetch()} />;

  const b = book.data;
  const lost = b.copyStatus === 'LOST';
  const active = (loans.data?.content ?? []).filter((l) => l.status === 'ACTIVE');
  const block = borrowBlock(active);
  const slots: SlotLoan[] = active.map((l) => ({ title: l.bookTitle, daysLeft: daysUntil(l.dueAt), overdue: l.overdue }));

  // Why Borrow is disabled, shown beside the button. The server still decides when this is stale.
  const reason = lost ? null : !b.available ? 'This copy is on loan. Check back after it is returned.' : block ? BLOCK_COPY[block] : null;

  return (
    <article className="book-detail">
      <Link to="/catalog">&larr; Back to catalog</Link>
      <div className="book-detail-grid">
        <BookCover book={cover(b)} size="lg" />
        <div>
          <h1 className="book-detail-title">{b.title}</h1>
          <p>
            by {b.author} <Chip>{b.genre}</Chip>
          </p>
          {b.description && <p className="book-description">{b.description}</p>}

          <dl className="catalog-card">
            <div>
              <dt>Copy no.</dt>
              <dd>{b.id}</dd>
            </div>
            <div>
              <dt>Status</dt>
              <dd>
                <BookBadge book={b} />
              </dd>
            </div>
            <div>
              <dt>Replacement cost</dt>
              <dd>{formatCost(b.replacementCost)}</dd>
            </div>
          </dl>

          {lost && <p className="muted">This copy was reported lost.</p>}

          {patron && !lost && (
            <div className="borrow">
              <Button onClick={() => borrow.mutate()} disabled={borrow.isPending || !!reason}>
                {borrow.isPending ? 'Borrowing…' : 'Borrow this copy'}
              </Button>
              {reason && <p className="muted" id="borrow-reason">{reason}</p>}
              <p className="muted">Due 14 days after checkout.</p>
            </div>
          )}
          {patron && !loans.isError && <LoanSlots loans={slots} />}
        </div>
      </div>
    </article>
  );
}

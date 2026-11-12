import { ApiError } from '../api/client';
import type { LoanResponse } from '../api/types';
import { daysUntil, formatDate } from './dates';

/** Plain, courteous copy for each stable loan error code (plan section 5.2). The client never matches message text. */
const LOAN_COPY: Record<string, string> = {
  LOAN_COPY_UNAVAILABLE: 'This copy is on loan. Check back after it is returned.',
  LOAN_BORROWER_OVERDUE: 'Return your overdue book before you borrow another.',
  LOAN_LIMIT_REACHED: 'You have 3 books checked out, the most the library allows. Return one to borrow another.',
  LOAN_NOT_ACTIVE: 'This loan is no longer active.',
};

/** Maps a failed borrow to user-facing copy: by code first, then by status, never by the server's English text. */
export function borrowErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.code && LOAN_COPY[error.code]) return LOAN_COPY[error.code]!;
    if (error.status === 404) return 'This copy could not be found.';
    if (error.status === 403) return 'Your account cannot borrow books.';
    if (error.status === 0) return error.message;
    if (error.status >= 500) {
      return `Something went wrong on our side.${error.requestId ? ` Reference: ${error.requestId.slice(0, 8)}.` : ''}`;
    }
  }
  return 'The book could not be borrowed. Try again.';
}

/** "Borrowed. Due Mon 23 Nov 2026, 14 days from today." */
export function borrowSuccessMessage(loan: LoanResponse): string {
  return `Borrowed. Due ${formatDate(loan.dueAt)}, ${daysUntil(loan.dueAt)} days from today.`;
}

/** Failed return: another borrower's loan is a 404 by design, so the copy says "not found" and never confirms it exists. */
export function returnErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.code === 'LOAN_NOT_ACTIVE') return LOAN_COPY.LOAN_NOT_ACTIVE!;
    if (error.status === 404) return 'This loan could not be found.';
    if (error.status === 0) return error.message;
    if (error.status >= 500) {
      return `Something went wrong on our side.${error.requestId ? ` Reference: ${error.requestId.slice(0, 8)}.` : ''}`;
    }
  }
  return 'The book could not be returned. Try again.';
}

export type BorrowBlock ='overdue' | 'limit' | null;

/** The same rules the server enforces, used only to explain a disabled Borrow button; the server still decides. */
export function borrowBlock(loans: Pick<LoanResponse, 'status' | 'overdue'>[]): BorrowBlock {
  const active = loans.filter((l) => l.status === 'ACTIVE');
  if (active.some((l) => l.overdue)) return 'overdue';
  if (active.length >= 3) return 'limit';
  return null;
}

export const BLOCK_COPY: Record<Exclude<BorrowBlock, null>, string> = {
  overdue: LOAN_COPY.LOAN_BORROWER_OVERDUE!,
  limit: LOAN_COPY.LOAN_LIMIT_REACHED!,
};

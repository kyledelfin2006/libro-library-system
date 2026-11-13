import { request } from './client';
import type {
  BookRequest, BookResponse, LibraryStatistics, LoanResponse, LoanStatus, Page, TopTitle, WeeklyActivity,
} from './types';

/** Staff loan directory filters. The server scopes results by the caller's role before filtering. */
export interface LoanFilters {
  status?: LoanStatus;
  overdue?: boolean;
  borrower?: string;
  page: number;
  size: number;
  sort?: string; // property,direction; properties: id, borrowedAt, dueAt, returnedAt, status
}

export function loanQueryString(f: LoanFilters): string {
  const p = new URLSearchParams({ page: String(f.page), size: String(f.size) });
  if (f.status) p.set('status', f.status);
  if (f.overdue) p.set('overdue', 'true');
  if (f.borrower?.trim()) p.set('borrower', f.borrower.trim());
  if (f.sort) p.set('sort', f.sort);
  return p.toString();
}

export const listLoans = (f: LoanFilters) => request<Page<LoanResponse>>('GET', `/app/loans?${loanQueryString(f)}`);

/** Records an ACTIVE loan as lost: the loan closes and the copy can no longer be borrowed. */
export const markLoanLost = (loanId: number) => request<LoanResponse>('PUT', `/app/loans/${loanId}/lost`);

export const bookStats = () => request<LibraryStatistics>('GET', '/app/books/stats');
export const loanActivity = (weeks = 12) => request<WeeklyActivity[]>('GET', `/app/loans/activity?weeks=${weeks}`);
export const topTitles = (days = 90, limit = 5) =>
  request<TopTitle[]>('GET', `/app/loans/top-titles?days=${days}&limit=${limit}`);

export const createBook = (body: BookRequest) => request<BookResponse>('POST', '/app/books/add', { body });
export const replaceBook = (id: number, body: BookRequest) => request<BookResponse>('PUT', `/app/books/${id}`, { body });
export const deleteBook = (id: number) => request<unknown>('DELETE', `/app/books/${id}`);

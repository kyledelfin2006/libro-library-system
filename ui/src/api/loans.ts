import { request } from './client';
import type { LoanResponse, Page } from './types';

/** Borrow one physical copy. The server enforces availability, the overdue block, and the 3-loan limit. */
export const borrowBook = (bookId: number) => request<LoanResponse>('POST', `/app/loans/${bookId}`);

/** The caller's own loans, newest first. Used for the loan-slot diagram and borrow-blocking hints. */
export const myLoans = (size = 100) => request<Page<LoanResponse>>('GET', `/app/loans/me?page=0&size=${size}`);

/** Sort fields the loan history endpoint allows; anything else returns 400. */
export const LOAN_SORT_FIELDS = ['borrowedAt', 'dueAt', 'returnedAt', 'status'] as const;
export type LoanSortField = (typeof LOAN_SORT_FIELDS)[number];

/** One page of the caller's loan history. */
export const myLoanHistory = (page: number, size: number, sort: LoanSortField, dir: 'asc' | 'desc') =>
  request<Page<LoanResponse>>('GET', `/app/loans/me?${new URLSearchParams({ page: String(page), size: String(size), sort: `${sort},${dir}` })}`);

/** Returns a loan. Another borrower's loan answers 404 by design, so the UI never confirms it exists. */
export const returnLoan = (loanId: number) => request<LoanResponse>('PUT', `/app/loans/${loanId}/return`);

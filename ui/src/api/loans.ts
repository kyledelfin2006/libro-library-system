import { request } from './client';
import type { LoanResponse, Page } from './types';

/** Borrow one physical copy. The server enforces availability, the overdue block, and the 3-loan limit. */
export const borrowBook = (bookId: number) => request<LoanResponse>('POST', `/app/loans/${bookId}`);

/** The caller's own loans, newest first. Used for the loan-slot diagram and borrow-blocking hints. */
export const myLoans = (size = 100) => request<Page<LoanResponse>>('GET', `/app/loans/me?page=0&size=${size}`);

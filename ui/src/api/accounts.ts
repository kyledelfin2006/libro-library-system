import { request } from './client';
import type { Page, UserResponse, UserRole } from './types';

/** Sort properties the account directory accepts; anything else is a 400 on the server, so it is dropped here. */
export const ACCOUNT_SORT_FIELDS = [
  'universityId', 'firstName', 'lastName', 'middleInitial', 'email', 'userRole', 'userCourse', 'major',
] as const;
export type AccountSortField = (typeof ACCOUNT_SORT_FIELDS)[number];

/** Directory filters. The server scopes results by the caller's role before filtering. */
export interface AccountFilters {
  role?: UserRole;
  q?: string;
  page: number;
  size: number;
  sort?: string; // property,direction
}

/** True when a `property,direction` sort string uses an allowed property and direction. */
export function isAllowedAccountSort(sort: string | null | undefined): sort is string {
  if (!sort) return false;
  const [property, direction = 'asc'] = sort.split(',');
  return ACCOUNT_SORT_FIELDS.includes(property as AccountSortField) && (direction === 'asc' || direction === 'desc');
}

/** Builds the query string, dropping empty filters and unknown sort fields. Size is clamped to the API maximum. */
export function accountQueryString(f: AccountFilters): string {
  const size = Math.min(100, Math.max(1, f.size));
  const p = new URLSearchParams({ page: String(Math.max(0, f.page)), size: String(size) });
  if (f.role) p.set('role', f.role);
  if (f.q?.trim()) p.set('q', f.q.trim());
  if (isAllowedAccountSort(f.sort)) p.set('sort', f.sort);
  return p.toString();
}

export const listAccounts = (f: AccountFilters) => request<Page<UserResponse>>('GET', `/app/users?${accountQueryString(f)}`);

/**
 * Fields for a staff-created account. Course and major are never sent, because faculty and library assistant
 * accounts must leave them empty, and the role is chosen by the endpoint, not the body.
 */
export interface StaffAccountRequest {
  universityId: string;
  firstName: string;
  lastName: string;
  middleInitial?: string;
  email: string;
  password: string;
}

export const createFaculty = (body: StaffAccountRequest) => request<UserResponse>('POST', '/app/users/faculty', { body });
export const createAssistant = (body: StaffAccountRequest) => request<UserResponse>('POST', '/app/users/administrators', { body });

/**
 * Sets a temporary password. The password appears only in this request body: it is not logged, cached or returned,
 * and the caller must not keep it after the request settles.
 */
export const resetPassword = (universityId: string, temporaryPassword: string) =>
  request<unknown>('PUT', `/app/users/${encodeURIComponent(universityId)}/password/reset`, { body: { temporaryPassword } });

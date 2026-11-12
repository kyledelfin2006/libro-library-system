import { request } from './client';
import type { UserResponse } from './types';

/** Partial profile update; only the fields present are changed. */
export interface ProfilePatch {
  firstName?: string;
  lastName?: string;
  middleInitial?: string;
  email?: string;
}

export const patchUser = (universityId: string, patch: ProfilePatch) =>
  request<UserResponse>('PATCH', `/app/users/${encodeURIComponent(universityId)}`, { body: patch });

/** Deletes an account. Students and faculty may delete only their own; history makes it a 409. */
export const deleteUser = (universityId: string) =>
  request<unknown>('DELETE', `/app/users/${encodeURIComponent(universityId)}`);

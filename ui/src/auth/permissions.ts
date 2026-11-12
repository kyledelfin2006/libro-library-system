import type { UserRole } from '../api/types';

export const ROLES: readonly UserRole[] = ['STUDENT', 'FACULTY', 'ADMINISTRATOR', 'LIBRARIAN'];

const PATRON: readonly UserRole[] = ['STUDENT', 'FACULTY'];
const STAFF: readonly UserRole[] = ['ADMINISTRATOR', 'LIBRARIAN'];

/**
 * Route guard table mirroring SecurityConfig. The UI is not a security boundary: this only decides what to show.
 * Keys are UI capabilities; values are the roles the server allows for the matching endpoint.
 */
export const permissions = {
  browseCatalog: ROLES,
  borrow: PATRON,
  returnLoan: PATRON,
  ownLoans: ROLES,
  ownAccount: ROLES,
  deleteOwnAccount: PATRON,
  staffDashboard: STAFF,
  listAllLoans: STAFF,
  markLoanLost: STAFF,
  manageBooks: STAFF,
  listAccounts: STAFF,
  createStaffAccounts: ['LIBRARIAN'],
  resetPassword: STAFF,
  buildInfo: STAFF,
  audit: STAFF,
} as const satisfies Record<string, readonly UserRole[]>;

export type Capability = keyof typeof permissions;

export function can(role: UserRole | null | undefined, capability: Capability): boolean {
  return !!role && (permissions[capability] as readonly UserRole[]).includes(role);
}

/** Role-aware landing page (plan section 4). */
export function homePath(role: UserRole): string {
  return can(role, 'staffDashboard') ? '/dashboard' : '/catalog';
}

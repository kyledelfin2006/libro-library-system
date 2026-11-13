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
  manageAssistantAccounts: ['LIBRARIAN'],
  resetPassword: STAFF,
  buildInfo: STAFF,
  audit: STAFF,
} as const satisfies Record<string, readonly UserRole[]>;

export type Capability = keyof typeof permissions;

export function can(role: UserRole | null | undefined, capability: Capability): boolean {
  return !!role && (permissions[capability] as readonly UserRole[]).includes(role);
}

/** Display names; ADMINISTRATOR is a library assistant, not a system administrator. */
export const ROLE_LABEL: Record<UserRole, string> = {
  STUDENT: 'Student',
  FACULTY: 'Faculty',
  ADMINISTRATOR: 'Library assistant',
  LIBRARIAN: 'Librarian',
};

/**
 * Mirrors UserAccessPolicy.canStaffManage: any staff manage students and faculty, only the librarian manages library
 * assistants, and nobody manages a librarian. The server stays authoritative.
 */
export function canManage(caller: UserRole | null | undefined, target: UserRole): boolean {
  if (!caller) return false;
  switch (target) {
    case 'STUDENT':
    case 'FACULTY':
      return STAFF.includes(caller);
    case 'ADMINISTRATOR':
      return caller === 'LIBRARIAN';
    default:
      return false;
  }
}

/** Roles a caller may list or filter by in the account directory. The librarian is never listed. */
export function visibleRoles(caller: UserRole | null | undefined): UserRole[] {
  return (['STUDENT', 'FACULTY', 'ADMINISTRATOR'] as const).filter((r) => canManage(caller, r));
}

/** A target account the caller is looking at. */
export interface AccountTarget {
  universityId: string;
  userRole: UserRole;
}

/** Staff may edit any managed account other than their own (their own profile is edited under My Account). */
export function canEdit(caller: UserRole | null | undefined, callerId: string | undefined, target: AccountTarget): boolean {
  return canManage(caller, target.userRole) && callerId !== target.universityId;
}

/** Reset is for managed accounts only and never your own. */
export function canReset(caller: UserRole | null | undefined, callerId: string | undefined, target: AccountTarget): boolean {
  return can(caller, 'resetPassword') && canManage(caller, target.userRole) && callerId !== target.universityId;
}

/** Staff delete managed accounts, never their own. */
export function canDelete(caller: UserRole | null | undefined, callerId: string | undefined, target: AccountTarget): boolean {
  return canManage(caller, target.userRole) && callerId !== target.universityId;
}

export interface MatrixRow {
  caller: UserRole;
  /** Target roles the caller can list, edit, reset and delete. */
  manages: UserRole[];
  createsAccounts: boolean;
}

/** Rows for the "Who can do what" diagram, derived from the same rules so it cannot drift. */
export function permissionMatrix(): MatrixRow[] {
  return ROLES.map((caller) => ({
    caller,
    manages: ROLES.filter((t) => canManage(caller, t)),
    createsAccounts: can(caller, 'createStaffAccounts'),
  }));
}

/** Role-aware landing page (plan section 4). */
export function homePath(role: UserRole): string {
  return can(role, 'staffDashboard') ? '/dashboard' : '/catalog';
}

import type { UserRole } from '../api/types';
import { ROLE_LABEL } from '../auth/permissions';

/** Plain-language names for the action tokens the server records. Unknown tokens fall back to the server summary. */
const ACTION_LABEL: Record<string, string> = {
  BOOK_CREATED: 'Book added',
  BOOK_CHANGED: 'Book changed',
  BOOK_DELETED: 'Book deleted',
  LOAN_MARKED_LOST: 'Loan marked lost',
  ACCOUNT_CREATED: 'Account created',
  PROFILE_CHANGED: 'Profile changed',
  PASSWORD_CHANGED: 'Password changed',
  PASSWORD_RESET: 'Password reset by staff',
  ACCOUNT_DELETED: 'Account deleted',
};

const ENTITY_LABEL: Record<string, string> = { BOOK: 'Book', LOAN: 'Loan', USER: 'Account' };

/** "Book added" for a known token; otherwise the server's own lower-case summary, capitalised. */
export function actionLabel(action: string, summary?: string): string {
  const known = ACTION_LABEL[action];
  if (known) return known;
  const text = (summary || action.replace(/_/g, ' ')).toLowerCase();
  return text.charAt(0).toUpperCase() + text.slice(1);
}

export const entityLabel = (type: string): string => ENTITY_LABEL[type] ?? type;

/** Role names the server may put in `actorRole`, shown in words. SYSTEM and ANONYMOUS are not accounts. */
export function actorRoleLabel(role: string): string {
  if (role in ROLE_LABEL) return ROLE_LABEL[role as UserRole];
  if (role === 'SYSTEM') return 'System';
  if (role === 'ANONYMOUS') return 'Not signed in';
  return 'Unknown';
}

/**
 * Actor-role filter options the caller may use. A library assistant who filters by assistant or librarian actors is
 * refused by the server (403), so those options are not offered to them; the server stays authoritative.
 */
export function actorRoleOptions(caller: UserRole | null | undefined): string[] {
  const shared = ['STUDENT', 'FACULTY', 'ANONYMOUS', 'SYSTEM'];
  return caller === 'LIBRARIAN' ? [...shared, 'ADMINISTRATOR', 'LIBRARIAN'] : shared;
}

/** Target-role filter options: assistants see student and faculty account events only. */
export function targetRoleOptions(caller: UserRole | null | undefined): UserRole[] {
  return caller === 'LIBRARIAN' ? ['STUDENT', 'FACULTY', 'ADMINISTRATOR', 'LIBRARIAN'] : ['STUDENT', 'FACULTY'];
}

/** True when both days are set and the range runs backwards (the server would answer 400). */
export function isBackwardsRange(from: string, to: string): boolean {
  return !!from && !!to && from > to;
}

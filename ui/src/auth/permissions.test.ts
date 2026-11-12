import { can, homePath, permissions, type Capability } from './permissions';
import type { UserRole } from '../api/types';

// Expected matrix mirrors SecurityConfig and the Role and JWT guide: patrons borrow/return, staff manage.
const expected: Record<Capability, UserRole[]> = {
  browseCatalog: ['STUDENT', 'FACULTY', 'ADMINISTRATOR', 'LIBRARIAN'],
  borrow: ['STUDENT', 'FACULTY'],
  returnLoan: ['STUDENT', 'FACULTY'],
  ownLoans: ['STUDENT', 'FACULTY', 'ADMINISTRATOR', 'LIBRARIAN'],
  ownAccount: ['STUDENT', 'FACULTY', 'ADMINISTRATOR', 'LIBRARIAN'],
  deleteOwnAccount: ['STUDENT', 'FACULTY'],
  staffDashboard: ['ADMINISTRATOR', 'LIBRARIAN'],
  listAllLoans: ['ADMINISTRATOR', 'LIBRARIAN'],
  markLoanLost: ['ADMINISTRATOR', 'LIBRARIAN'],
  manageBooks: ['ADMINISTRATOR', 'LIBRARIAN'],
  listAccounts: ['ADMINISTRATOR', 'LIBRARIAN'],
  createStaffAccounts: ['LIBRARIAN'],
  resetPassword: ['ADMINISTRATOR', 'LIBRARIAN'],
  buildInfo: ['ADMINISTRATOR', 'LIBRARIAN'],
  audit: ['ADMINISTRATOR', 'LIBRARIAN'],
};

describe('permissions', () => {
  it('matches the backend role matrix for every capability', () => {
    for (const cap of Object.keys(permissions) as Capability[]) {
      expect([...permissions[cap]].sort()).toEqual([...expected[cap]].sort());
    }
  });

  it('denies unknown and missing roles', () => {
    expect(can(null, 'borrow')).toBe(false);
    expect(can(undefined, 'browseCatalog')).toBe(false);
  });

  it('sends staff to the dashboard and patrons to the catalog', () => {
    expect(homePath('LIBRARIAN')).toBe('/dashboard');
    expect(homePath('ADMINISTRATOR')).toBe('/dashboard');
    expect(homePath('STUDENT')).toBe('/catalog');
    expect(homePath('FACULTY')).toBe('/catalog');
  });
});

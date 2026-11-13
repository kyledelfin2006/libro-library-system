import {
  can, canDelete, canEdit, canManage, canReset, homePath, permissionMatrix, permissions, visibleRoles, type Capability,
} from './permissions';
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
  manageAssistantAccounts: ['LIBRARIAN'],
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

// Mirrors UserAccessPolicy: caller -> target roles it may manage.
const manages: Record<UserRole, UserRole[]> = {
  STUDENT: [],
  FACULTY: [],
  ADMINISTRATOR: ['STUDENT', 'FACULTY'],
  LIBRARIAN: ['STUDENT', 'FACULTY', 'ADMINISTRATOR'],
};

describe('account management policy', () => {
  const roles: UserRole[] = ['STUDENT', 'FACULTY', 'ADMINISTRATOR', 'LIBRARIAN'];

  it('matches UserAccessPolicy for every caller and target role', () => {
    for (const caller of roles) {
      for (const target of roles) {
        expect(canManage(caller, target), `${caller} -> ${target}`).toBe(manages[caller].includes(target));
        const t = { universityId: '2025-0002', userRole: target };
        expect(canEdit(caller, '2020-0001', t)).toBe(manages[caller].includes(target));
        expect(canReset(caller, '2020-0001', t)).toBe(manages[caller].includes(target));
        expect(canDelete(caller, '2020-0001', t)).toBe(manages[caller].includes(target));
      }
    }
  });

  it('never offers reset or delete on your own row, and never on a librarian', () => {
    const own = { universityId: '2020-0001', userRole: 'ADMINISTRATOR' as const };
    expect(canReset('LIBRARIAN', '2020-0001', own)).toBe(false);
    expect(canDelete('LIBRARIAN', '2020-0001', own)).toBe(false);
    const librarian = { universityId: '2020-0009', userRole: 'LIBRARIAN' as const };
    expect(canReset('LIBRARIAN', '2020-0001', librarian)).toBe(false);
    expect(canDelete('LIBRARIAN', '2020-0001', librarian)).toBe(false);
  });

  it('scopes the directory roles and never lists the librarian', () => {
    expect(visibleRoles('ADMINISTRATOR')).toEqual(['STUDENT', 'FACULTY']);
    expect(visibleRoles('LIBRARIAN')).toEqual(['STUDENT', 'FACULTY', 'ADMINISTRATOR']);
    expect(visibleRoles('STUDENT')).toEqual([]);
    expect(visibleRoles(null)).toEqual([]);
  });

  it('derives the help matrix from the same rules', () => {
    const rows = permissionMatrix();
    expect(rows.find((r) => r.caller === 'LIBRARIAN')).toMatchObject({ manages: manages.LIBRARIAN, createsAccounts: true });
    expect(rows.find((r) => r.caller === 'ADMINISTRATOR')).toMatchObject({ manages: manages.ADMINISTRATOR, createsAccounts: false });
  });
});

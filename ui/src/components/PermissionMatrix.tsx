import type { UserRole } from '../api/types';
import { ROLE_LABEL, permissionMatrix } from '../auth/permissions';

/** "Who can do what" help table, generated from permissions.ts so it cannot drift from the rules the UI enforces. */
export function PermissionMatrix() {
  const rows = permissionMatrix();
  const targets: UserRole[] = ['STUDENT', 'FACULTY', 'ADMINISTRATOR', 'LIBRARIAN'];
  return (
    <table className="permission-matrix">
      <caption className="visually-hidden">Account actions each role may take on each kind of account</caption>
      <thead>
        <tr>
          <th scope="col">Role</th>
          {targets.map((t) => <th key={t} scope="col">{ROLE_LABEL[t]} accounts</th>)}
          <th scope="col">Create faculty and assistants</th>
        </tr>
      </thead>
      <tbody>
        {rows.map((r) => (
          <tr key={r.caller}>
            <th scope="row">{ROLE_LABEL[r.caller]}</th>
            {targets.map((t) => (
              <td key={t}>
                {r.manages.includes(t)
                  ? <><span aria-hidden="true">✓</span> View, edit, reset, delete</>
                  : <><span aria-hidden="true">–</span> <span className="visually-hidden">No access</span></>}
              </td>
            ))}
            <td>
              {r.createsAccounts
                ? <><span aria-hidden="true">✓</span> Yes</>
                : <><span aria-hidden="true">–</span> <span className="visually-hidden">No</span></>}
            </td>
          </tr>
        ))}
      </tbody>
      <tfoot>
        <tr>
          <td colSpan={6}>Nobody can manage a librarian account, and nobody can reset or delete their own account here.</td>
        </tr>
      </tfoot>
    </table>
  );
}

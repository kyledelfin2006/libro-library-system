import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { isAllowedAccountSort, listAccounts } from '../api/accounts';
import { ApiError } from '../api/client';
import type { UserResponse, UserRole } from '../api/types';
import { deleteUser } from '../api/users';
import { useAuth } from '../auth/AuthProvider';
import { ROLE_LABEL, canDelete, canEdit, canReset, visibleRoles } from '../auth/permissions';
import { CreateAccountDialog } from '../components/CreateAccountDialog';
import { PermissionMatrix } from '../components/PermissionMatrix';
import { ProfileForm } from '../components/ProfileForm';
import { ResetPasswordDialog } from '../components/ResetPasswordDialog';
import { useToast } from '../components/Toast';
import { Button, Can, Dialog, EmptyState, ErrorState, Pagination, Skeleton } from '../components/ui';
import { describeError } from '../lib/forms';
import { useDebouncedValue } from '../lib/useDebouncedValue';

const PAGE_SIZES = [12, 24, 48, 100];
const DEFAULT_SORT = 'universityId,asc';
const SORTS = [
  { value: 'universityId,asc', label: 'ID' },
  { value: 'lastName,asc', label: 'Last name A-Z' },
  { value: 'firstName,asc', label: 'First name A-Z' },
  { value: 'email,asc', label: 'Email A-Z' },
  { value: 'userRole,asc', label: 'Role' },
];
const ROLE_ICON: Record<UserRole, string> = { STUDENT: '✎', FACULTY: '⌂', ADMINISTRATOR: '✦', LIBRARIAN: '★' };

/** Role chip: an icon plus the text label, so color is never the only signal. */
export function RoleChip({ role }: { role: UserRole }) {
  return (
    <span className="chip chip-neutral">
      <span aria-hidden="true">{ROLE_ICON[role]}</span> {ROLE_LABEL[role]}
    </span>
  );
}

const fullName = (u: UserResponse) => `${u.firstName} ${u.middleInitial ? `${u.middleInitial}. ` : ''}${u.lastName}`;

/** Staff account directory: search, role filter and sort in the URL; a row opens a panel with only the allowed actions. */
export function Accounts() {
  const { user, refreshUser } = useAuth();
  const { notify } = useToast();
  const queryClient = useQueryClient();
  const [sp, setSp] = useSearchParams();

  const allowedRoles = visibleRoles(user?.userRole);
  const roleParam = sp.get('role');
  const role = allowedRoles.find((r) => r === roleParam);
  const q = sp.get('q') ?? '';
  const sortParam = sp.get('sort');
  const sort = isAllowedAccountSort(sortParam) && SORTS.some((s) => s.value === sortParam) ? sortParam : DEFAULT_SORT;
  const page = Math.max(0, Number(sp.get('page')) || 0);
  const size = PAGE_SIZES.includes(Number(sp.get('size'))) ? Number(sp.get('size')) : 12;
  const openId = sp.get('open');

  const [mode, setMode] = useState<'edit' | 'reset' | 'delete' | 'create' | null>(null);

  /** Writes filters to the URL; any filter change returns to page one. */
  const setParams = (changes: Record<string, string | null>, keepPage = false, replace = false) => {
    const next = new URLSearchParams(sp);
    for (const [k, v] of Object.entries(changes)) {
      if (v === null || v === '') next.delete(k);
      else next.set(k, v);
    }
    if (!keepPage) next.delete('page');
    setSp(next, { replace });
  };

  // Search box: local text, debounced 300 ms into the URL, and back-synced when the URL changes underneath it.
  const [text, setText] = useState(q);
  const debounced = useDebouncedValue(text, 300).trim();
  useEffect(() => {
    if (debounced !== q) setParams({ q: debounced }, false, true);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- only a settled search term should write to the URL
  }, [debounced]);
  useEffect(() => {
    if (q !== debounced) setText(q);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- sync only when the URL changes underneath the box
  }, [q]);

  const filters = { ...(role ? { role } : {}), ...(q ? { q } : {}), page, size, sort };
  const accounts = useQuery({ queryKey: ['accounts', filters], queryFn: () => listAccounts(filters), placeholderData: keepPreviousData });
  const selected = accounts.data?.content.find((a) => a.universityId === openId) ?? null;

  const refreshAccounts = () => queryClient.invalidateQueries({ queryKey: ['accounts'] });

  /** A 403/404 on a previously allowed action means our view is stale: say so, refresh /me and the directory. */
  const handleDenied = (status: number) => {
    setMode(null);
    notify(status === 404 ? 'That account no longer exists.' : 'You no longer have permission for that action.', 'error');
    if (status === 403) void refreshUser();
    void refreshAccounts();
  };

  const remove = useMutation({
    mutationFn: (target: UserResponse) => deleteUser(target.universityId),
    onSuccess: (_d, target) => {
      notify(`Deleted ${fullName(target)}.`);
      setMode(null);
      setParams({ open: null }, true);
    },
    onError: (e) => {
      if (e instanceof ApiError && (e.status === 403 || e.status === 404)) handleDenied(e.status);
      else if (e instanceof ApiError && e.code === 'RESOURCE_HAS_HISTORY') {
        setMode(null);
        notify('This account has loan history and must be kept.', 'error');
      } else notify(describeError(e), 'error');
    },
    onSettled: () => refreshAccounts(),
  });

  const hasFilters = !!role || !!q;
  const callerId = user?.universityId;
  const callerRole = user?.userRole;

  return (
    <section>
      <div className="page-head">
        <h1>Accounts</h1>
        <Can capability="createStaffAccounts">
          <Button onClick={() => setMode('create')}>+ New faculty / assistant</Button>
        </Can>
      </div>

      <details className="help-disclosure">
        <summary>Who can do what</summary>
        <PermissionMatrix />
      </details>

      <form className="toolbar" onSubmit={(e) => e.preventDefault()} role="search">
        <div className="field toolbar-search">
          <label htmlFor="acct-search">Search name, ID or email</label>
          <input id="acct-search" type="search" value={text} onChange={(e) => setText(e.target.value)} autoComplete="off" />
        </div>
        <div className="field">
          <label htmlFor="acct-role">Role</label>
          <select id="acct-role" value={role ?? ''} onChange={(e) => setParams({ role: e.target.value })}>
            <option value="">All</option>
            {allowedRoles.map((r) => <option key={r} value={r}>{ROLE_LABEL[r]}</option>)}
          </select>
        </div>
        <div className="field">
          <label htmlFor="acct-sort">Sort by</label>
          <select id="acct-sort" value={sort} onChange={(e) => setParams({ sort: e.target.value === DEFAULT_SORT ? null : e.target.value })}>
            {SORTS.map((s) => <option key={s.value} value={s.value}>{s.label}</option>)}
          </select>
        </div>
        {hasFilters && (
          <Button variant="secondary" onClick={() => { setText(''); setSp(new URLSearchParams()); }}>Clear filters</Button>
        )}
      </form>

      <div className="split">
        <div>
          {accounts.isPending ? (
            <Skeleton height={240} />
          ) : accounts.isError && !accounts.data ? (
            <AccountsError error={accounts.error} onRetry={() => void accounts.refetch()} />
          ) : accounts.data && accounts.data.content.length === 0 ? (
            <EmptyState
              title={hasFilters ? 'No accounts match these filters.' : 'There are no accounts in your scope yet.'}
              action={hasFilters ? <Button variant="secondary" onClick={() => { setText(''); setSp(new URLSearchParams()); }}>Clear filters</Button> : undefined}
            >
              {hasFilters ? 'Try a different name, ID or email, or clear the filters.' : undefined}
            </EmptyState>
          ) : accounts.data ? (
            <>
              <table className="loan-table record-cards">
                <thead>
                  <tr>
                    <th scope="col">ID</th>
                    <th scope="col">Name</th>
                    <th scope="col">Role</th>
                    <th scope="col">Course/Major</th>
                    <th scope="col">Email</th>
                    <th scope="col"><span className="visually-hidden">Details</span></th>
                  </tr>
                </thead>
                <tbody>
                  {accounts.data.content.map((a) => (
                    <tr key={a.universityId} aria-selected={a.universityId === openId} className={a.universityId === openId ? 'row-selected' : undefined}>
                      <th scope="row" data-label="ID">{a.universityId}</th>
                      <td data-label="Name">{fullName(a)}</td>
                      <td data-label="Role"><RoleChip role={a.userRole} /></td>
                      <td data-label="Course/Major">{a.userCourse ? `${a.userCourse}${a.major ? ` / ${a.major}` : ''}` : '—'}</td>
                      <td data-label="Email">{a.email}</td>
                      <td>
                        <Button variant="secondary" onClick={() => { setMode(null); setParams({ open: a.universityId }, true); }}>
                          Details<span className="visually-hidden"> for {fullName(a)}</span>
                        </Button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
              <Pagination
                page={accounts.data.number}
                size={accounts.data.size}
                total={accounts.data.totalElements}
                onPage={(p) => setParams({ page: p > 0 ? String(p) : null }, true)}
                onSize={(s) => setParams({ size: s === 12 ? null : String(s) })}
              />
            </>
          ) : null}
        </div>

        {selected && (
          <aside className="side-panel" aria-label={`${fullName(selected)} details`}>
            <h2>{fullName(selected)}</h2>
            {mode === 'edit' && canEdit(callerRole, callerId, selected) ? (
              <ProfileForm
                key={selected.universityId}
                target={selected}
                onCancel={() => setMode(null)}
                onSaved={async (saved) => {
                  if (saved) {
                    notify(`Saved ${fullName(saved)}.`);
                    await refreshAccounts();
                  }
                  setMode(null);
                }}
              />
            ) : (
              <>
                <dl className="catalog-card">
                  <div><dt>University ID</dt><dd>{selected.universityId}</dd></div>
                  <div><dt>Email</dt><dd>{selected.email}</dd></div>
                  <div><dt>Role</dt><dd><RoleChip role={selected.userRole} /></dd></div>
                  {selected.userCourse && <div><dt>Course</dt><dd>{selected.userCourse}{selected.major ? ` / ${selected.major}` : ''}</dd></div>}
                  {selected.mustChangePassword && <div><dt>Password</dt><dd>Must be changed at next sign-in</dd></div>}
                </dl>
                <div className="form-actions">
                  {canEdit(callerRole, callerId, selected) && <Button variant="secondary" onClick={() => setMode('edit')}>Edit profile</Button>}
                  {canReset(callerRole, callerId, selected) && <Button variant="secondary" onClick={() => setMode('reset')}>Reset password</Button>}
                  {canDelete(callerRole, callerId, selected) && <Button variant="danger" onClick={() => setMode('delete')}>Delete account</Button>}
                  <Button variant="secondary" onClick={() => { setMode(null); setParams({ open: null }, true); }}>Close</Button>
                </div>
              </>
            )}
          </aside>
        )}
      </div>

      {mode === 'reset' && selected && canReset(callerRole, callerId, selected) && (
        <ResetPasswordDialog
          key={selected.universityId}
          target={selected}
          onClose={() => setMode(null)}
          onReset={(t) => {
            notify(`Temporary password set for ${fullName(t)}. They must change it at next sign-in.`);
            setMode(null);
            void refreshAccounts();
          }}
          onDenied={handleDenied}
        />
      )}

      {mode === 'delete' && selected && canDelete(callerRole, callerId, selected) && (
        <Dialog
          open
          title={`Delete ${fullName(selected)}?`}
          confirmLabel="Delete account"
          danger
          busy={remove.isPending}
          onConfirm={() => remove.mutate(selected)}
          onClose={() => !remove.isPending && setMode(null)}
        >
          <p>
            This permanently deletes the {ROLE_LABEL[selected.userRole].toLowerCase()} account <strong>{selected.universityId}</strong>.
            Accounts with loan history cannot be deleted.
          </p>
        </Dialog>
      )}

      {mode === 'create' && (
        <CreateAccountDialog
          onClose={() => setMode(null)}
          onDenied={() => handleDenied(403)}
          onCreated={(created) => {
            notify(`Created the account for ${fullName(created)}.`);
            setMode(null);
            setText(created.universityId);
            setSp(new URLSearchParams({ q: created.universityId, open: created.universityId }));
            void refreshAccounts();
          }}
        />
      )}
    </section>
  );
}

/** Staff-facing error copy: a role outside the caller's scope is a permission message, not a failure. */
function AccountsError({ error, onRetry }: { error: unknown; onRetry: () => void }) {
  if (error instanceof ApiError && error.status === 403) {
    return <EmptyState title="You do not have permission to view these accounts." />;
  }
  return <ErrorState error={error} onRetry={onRetry} />;
}

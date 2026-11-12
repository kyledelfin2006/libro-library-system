import { useRef, useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { changePassword } from '../api/auth';
import { ApiError } from '../api/client';
import { deleteUser, patchUser, type ProfilePatch } from '../api/users';
import { useAuth } from '../auth/AuthProvider';
import { can } from '../auth/permissions';
import { PasswordChecklist } from '../components/PasswordChecklist';
import { PasswordField, TextField } from '../components/TextField';
import { useToast } from '../components/Toast';
import { Button, Dialog } from '../components/ui';
import { describeError, useFocusFirstInvalid } from '../lib/forms';
import { isPasswordValid } from '../lib/password';

const ROLE_LABEL = { STUDENT: 'Student', FACULTY: 'Faculty', ADMINISTRATOR: 'Library assistant', LIBRARIAN: 'Librarian' } as const;

/** My Account: profile (read, then edit), password change, and a danger zone for students and faculty only. */
export function Account() {
  const { user } = useAuth();
  if (!user) return null;
  return (
    <section>
      <h1>My account</h1>
      <div className="account-grid">
        <Profile />
        <Security />
      </div>
      {can(user.userRole, 'deleteOwnAccount') && <DangerZone />}
    </section>
  );
}

function Profile() {
  const { user, refreshUser } = useAuth();
  const { notify } = useToast();
  const form = useRef<HTMLFormElement>(null);
  const [editing, setEditing] = useState(false);
  const [values, setValues] = useState({ firstName: '', lastName: '', middleInitial: '', email: '' });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [attempt, setAttempt] = useState(0);
  useFocusFirstInvalid(form, attempt);
  if (!user) return null;

  const startEdit = () => {
    setValues({ firstName: user.firstName, lastName: user.lastName, middleInitial: user.middleInitial ?? '', email: user.email });
    setErrors({});
    setFormError(null);
    setEditing(true);
  };
  const set = (k: keyof typeof values) => (e: { target: { value: string } }) => setValues((v) => ({ ...v, [k]: e.target.value }));

  async function save(e: FormEvent) {
    e.preventDefault();
    if (saving || !user) return;
    // Client checks mirror the DTO; only changed fields are sent.
    const found: Record<string, string> = {};
    if (!values.firstName.trim()) found.firstName = 'First name is required.';
    if (!values.lastName.trim()) found.lastName = 'Last name is required.';
    if (values.middleInitial && !/^[A-Za-z]$/.test(values.middleInitial)) found.middleInitial = 'Use one letter.';
    if (!/^\S+@\S+\.\S+$/.test(values.email)) found.email = 'Enter a valid email address.';
    setErrors(found);
    setFormError(null);
    if (Object.keys(found).length > 0) {
      setAttempt((n) => n + 1);
      return;
    }
    const patch: ProfilePatch = {};
    if (values.firstName.trim() !== user.firstName) patch.firstName = values.firstName.trim();
    if (values.lastName.trim() !== user.lastName) patch.lastName = values.lastName.trim();
    if (values.middleInitial !== (user.middleInitial ?? '')) patch.middleInitial = values.middleInitial;
    if (values.email.trim() !== user.email) patch.email = values.email.trim();
    if (Object.keys(patch).length === 0) {
      setEditing(false);
      return;
    }
    setSaving(true);
    try {
      await patchUser(user.universityId, patch);
      await refreshUser();
      notify('Profile saved.');
      setEditing(false);
    } catch (err) {
      if (err instanceof ApiError && Object.keys(err.fieldErrors).length > 0) {
        const { _global, ...fields } = err.fieldErrors;
        setErrors(fields);
        if (_global) setFormError(_global);
        setAttempt((n) => n + 1);
      } else {
        setFormError(describeError(err));
      }
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="panel">
      <h2>Profile</h2>
      {!editing ? (
        <>
          <dl className="catalog-card">
            <div><dt>University ID</dt><dd>{user.universityId}</dd></div>
            <div><dt>Name</dt><dd>{user.firstName} {user.middleInitial ? `${user.middleInitial}. ` : ''}{user.lastName}</dd></div>
            <div><dt>Email</dt><dd>{user.email}</dd></div>
            <div><dt>Role</dt><dd>{ROLE_LABEL[user.userRole]}</dd></div>
            {user.userCourse && <div><dt>Course</dt><dd>{user.userCourse}{user.major ? ` / ${user.major}` : ''}</dd></div>}
          </dl>
          <Button variant="secondary" onClick={startEdit}>Edit profile</Button>
        </>
      ) : (
        <form ref={form} onSubmit={save} noValidate>
          {formError && <p className="form-error" role="alert">{formError}</p>}
          <TextField label="First name" value={values.firstName} onChange={set('firstName')} error={errors.firstName} maxLength={50} />
          <TextField label="Last name" value={values.lastName} onChange={set('lastName')} error={errors.lastName} maxLength={50} />
          <TextField label="Middle initial (optional)" value={values.middleInitial} onChange={set('middleInitial')} error={errors.middleInitial} maxLength={1} />
          <TextField label="Email" type="email" value={values.email} onChange={set('email')} error={errors.email} maxLength={150} />
          <div className="form-actions">
            <Button type="submit" disabled={saving}>{saving ? 'Saving…' : 'Save profile'}</Button>
            <Button variant="secondary" onClick={() => setEditing(false)} disabled={saving}>Cancel</Button>
          </div>
        </form>
      )}
    </div>
  );
}

function Security() {
  const { user, refreshUser } = useAuth();
  const { notify } = useToast();
  const form = useRef<HTMLFormElement>(null);
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [attempt, setAttempt] = useState(0);
  useFocusFirstInvalid(form, attempt);
  if (!user) return null;

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (saving || !user) return;
    const found: Record<string, string> = {};
    if (!current) found.currentPassword = 'Enter your current password.';
    if (!isPasswordValid(next)) found.newPassword = 'The new password does not meet every requirement below.';
    setErrors(found);
    setFormError(null);
    if (Object.keys(found).length > 0) {
      setAttempt((n) => n + 1);
      return;
    }
    setSaving(true);
    try {
      await changePassword(user.universityId, current, next);
      await refreshUser();
      setCurrent('');
      setNext('');
      notify('Password changed.');
    } catch (err) {
      if (err instanceof ApiError && Object.keys(err.fieldErrors).length > 0) {
        const { _global, ...fields } = err.fieldErrors;
        setErrors(fields);
        if (_global) setFormError(_global);
        setAttempt((n) => n + 1);
      } else {
        setFormError(describeError(err));
      }
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="panel">
      <h2>Security</h2>
      <form ref={form} onSubmit={submit} noValidate>
        {formError && <p className="form-error" role="alert">{formError}</p>}
        <PasswordField label="Current password" value={current} onChange={(e) => setCurrent(e.target.value)}
          error={errors.currentPassword} autoComplete="current-password" />
        <PasswordField label="New password" value={next} onChange={(e) => setNext(e.target.value)}
          error={errors.newPassword} autoComplete="new-password" />
        <PasswordChecklist value={next} />
        <Button type="submit" disabled={saving}>{saving ? 'Saving…' : 'Change password'}</Button>
      </form>
    </div>
  );
}

/** Self-delete for students and faculty. The owner must type their university ID; loan history blocks it (409). */
function DangerZone() {
  const { user, signOut } = useAuth();
  const { notify } = useToast();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const [typed, setTyped] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  if (!user) return null;

  async function confirm() {
    if (!user || typed !== user.universityId || busy) return;
    setBusy(true);
    setError(null);
    try {
      await deleteUser(user.universityId);
      signOut();
      notify('Your account was deleted.');
      navigate('/signin', { replace: true });
    } catch (err) {
      setError(
        err instanceof ApiError && err.code === 'RESOURCE_HAS_HISTORY'
          ? 'Your account has loan history and must be kept. Ask library staff for help.'
          : describeError(err),
      );
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="panel danger-zone">
      <h2>Danger zone</h2>
      <p className="muted">Deleting your account cannot be undone.</p>
      <Button variant="danger" onClick={() => { setTyped(''); setError(null); setOpen(true); }}>Delete my account</Button>
      <Dialog
        open={open}
        title="Delete your account?"
        confirmLabel="Delete account"
        danger
        busy={busy}
        confirmDisabled={typed !== user.universityId}
        onConfirm={() => void confirm()}
        onClose={() => !busy && setOpen(false)}
      >
        <p>Type your University ID <strong>{user.universityId}</strong> to confirm.</p>
        <TextField label="University ID" value={typed} onChange={(e) => setTyped(e.target.value)} autoComplete="off" />
        {typed !== '' && typed !== user.universityId && <p className="field-hint">The ID does not match yet.</p>}
        {error && <p className="form-error" role="alert">{error}</p>}
      </Dialog>
    </div>
  );
}

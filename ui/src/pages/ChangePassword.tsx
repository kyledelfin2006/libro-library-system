import { useRef, useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { changePassword } from '../api/auth';
import { ApiError } from '../api/client';
import { useAuth } from '../auth/AuthProvider';
import { homePath } from '../auth/permissions';
import { AuthLayout } from '../components/AuthLayout';
import { PasswordChecklist } from '../components/PasswordChecklist';
import { PasswordField } from '../components/TextField';
import { useToast } from '../components/Toast';
import { describeError, useFocusFirstInvalid } from '../lib/forms';
import { isPasswordValid } from '../lib/password';

/** Forced password change after a staff reset. The server does not block other routes, so RequireAuth enforces it. */
export function ChangePassword() {
  const { user, refreshUser } = useAuth();
  const navigate = useNavigate();
  const { notify } = useToast();
  const form = useRef<HTMLFormElement>(null);
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [attempt, setAttempt] = useState(0);
  useFocusFirstInvalid(form, attempt);
  if (!user) return null;

  async function onSubmit(ev: FormEvent) {
    ev.preventDefault();
    if (submitting || !user) return;
    const found: Record<string, string> = {};
    if (!current) found.currentPassword = 'Enter your current password.';
    if (!isPasswordValid(next)) found.newPassword = 'The new password does not meet every requirement below.';
    setErrors(found);
    setFormError(null);
    if (Object.keys(found).length > 0) {
      setAttempt((n) => n + 1);
      return;
    }
    setSubmitting(true);
    try {
      await changePassword(user.universityId, current, next);
      await refreshUser(); // success clears mustChangePassword on the server
      notify('Password changed.');
      navigate(homePath(user.userRole), { replace: true });
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
      setSubmitting(false);
    }
  }

  return (
    <AuthLayout title="Choose a new password">
      {user.mustChangePassword && (
        <p className="notice">Your password was reset by library staff. Choose a new one to continue.</p>
      )}
      <form ref={form} onSubmit={onSubmit} noValidate>
        {formError && (
          <p className="form-error" role="alert">
            {formError}
          </p>
        )}
        <PasswordField label="Current (temporary) password" value={current}
          onChange={(e) => setCurrent(e.target.value)} error={errors.currentPassword} autoComplete="current-password" />
        <PasswordField label="New password" value={next} onChange={(e) => setNext(e.target.value)}
          error={errors.newPassword} autoComplete="new-password" />
        <PasswordChecklist value={next} />
        <button type="submit" className="btn btn-primary" disabled={submitting}>
          {submitting ? 'Saving…' : 'Change password'}
        </button>
      </form>
    </AuthLayout>
  );
}

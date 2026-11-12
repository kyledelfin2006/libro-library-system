import { useRef, useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { ApiError } from '../api/client';
import { useAuth } from '../auth/AuthProvider';
import { homePath } from '../auth/permissions';
import { AuthLayout } from '../components/AuthLayout';
import { PasswordField, TextField } from '../components/TextField';
import { describeError, useFocusFirstInvalid } from '../lib/forms';
import { useCountdown } from '../lib/useCountdown';

const ID_PATTERN = /^\d{4}-\d{4}$/;

/** Sign in: logs in, then loads /me so navigation is built from the profile's role, never from the token. */
export function SignIn() {
  const { signIn, sessionExpired } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from;

  const form = useRef<HTMLFormElement>(null);
  const [universityId, setUniversityId] = useState('');
  const [password, setPassword] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [retryAfter, setRetryAfter] = useState<number | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const secondsLeft = useCountdown(retryAfter);
  const throttled = secondsLeft !== null && secondsLeft > 0;

  useFocusFirstInvalid(form, attempt);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (submitting || throttled) return;
    const errors: Record<string, string> = {};
    if (!ID_PATTERN.test(universityId)) errors.universityId = 'Enter your University ID like 2025-4321.';
    if (!password) errors.password = 'Enter your password.';
    setFormError(null);
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) {
      setAttempt((n) => n + 1);
      return;
    }
    setSubmitting(true);
    try {
      const me = await signIn(universityId, password);
      navigate(me.mustChangePassword ? '/change-password' : (from ?? homePath(me.userRole)), { replace: true });
    } catch (err) {
      if (err instanceof ApiError && err.status === 429) {
        setRetryAfter(err.retryAfterSeconds ?? 60);
      } else if (err instanceof ApiError && err.status === 401) {
        setFormError('The University ID or password is not correct.');
      } else if (err instanceof ApiError && Object.keys(err.fieldErrors).length > 0) {
        setFieldErrors(err.fieldErrors);
        setAttempt((n) => n + 1);
      } else {
        setFormError(describeError(err));
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <AuthLayout title="Sign in to the library">
      {sessionExpired && (
        <p className="notice" role="status">
          Your session ended. Sign in again to continue.
        </p>
      )}
      <form ref={form} onSubmit={onSubmit} noValidate>
        {formError && (
          <p className="form-error" role="alert">
            {formError}
          </p>
        )}
        {throttled && (
          <p className="form-error" role="alert">
            Too many attempts. You can try again in {secondsLeft} {secondsLeft === 1 ? 'second' : 'seconds'}.
          </p>
        )}
        <TextField
          label="University ID"
          value={universityId}
          onChange={(e) => setUniversityId(e.target.value)}
          error={fieldErrors.universityId}
          autoComplete="username"
          inputMode="numeric"
          placeholder="2025-4321"
        />
        <PasswordField
          label="Password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          error={fieldErrors.password}
          autoComplete="current-password"
        />
        <button type="submit" className="btn btn-primary" disabled={submitting || throttled}>
          {submitting ? 'Signing in…' : 'Sign in'}
        </button>
      </form>
      <p className="auth-alt">
        New student? <Link to="/register">Create an account</Link>
      </p>
    </AuthLayout>
  );
}

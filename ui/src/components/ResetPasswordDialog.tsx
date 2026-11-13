import { useRef, useState } from 'react';
import { resetPassword } from '../api/accounts';
import { ApiError } from '../api/client';
import type { UserResponse } from '../api/types';
import { describeError, useFocusFirstInvalid } from '../lib/forms';
import { isPasswordValid } from '../lib/password';
import { TemporaryPasswordInput } from './TemporaryPasswordInput';
import { Dialog } from './ui';

/**
 * Staff-assisted password reset. Mount it only while a target is selected: closing unmounts it, which discards the
 * typed or generated password. The password is deliberately held in local state and sent with a direct call, not a
 * useMutation, because TanStack Query keeps mutation variables in its mutation cache.
 */
export function ResetPasswordDialog({
  target,
  onClose,
  onReset,
  onDenied,
}: {
  target: UserResponse;
  onClose: () => void;
  /** Called after success; the message names the account, never the password. */
  onReset: (target: UserResponse) => void;
  /** Called on 403/404 so the page can refresh /me and the directory. */
  onDenied: (status: number) => void;
}) {
  const form = useRef<HTMLFormElement>(null);
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | undefined>();
  const [formError, setFormError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [attempt, setAttempt] = useState(0);
  useFocusFirstInvalid(form, attempt);

  async function submit() {
    if (busy) return;
    if (!isPasswordValid(password)) {
      setError('The temporary password does not meet every requirement below.');
      setAttempt((n) => n + 1);
      return;
    }
    setError(undefined);
    setFormError(null);
    setBusy(true);
    try {
      await resetPassword(target.universityId, password);
      setPassword('');
      onReset(target);
    } catch (err) {
      if (err instanceof ApiError && (err.status === 403 || err.status === 404)) {
        setPassword('');
        onDenied(err.status);
      } else if (err instanceof ApiError && err.fieldErrors.temporaryPassword) {
        setError(err.fieldErrors.temporaryPassword);
        setAttempt((n) => n + 1);
      } else {
        setFormError(describeError(err));
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <Dialog
      open
      title={`Reset password for ${target.firstName} ${target.lastName}?`}
      confirmLabel="Reset password"
      busy={busy}
      onConfirm={() => void submit()}
      onClose={() => !busy && onClose()}
    >
      <form ref={form} noValidate onSubmit={(e) => { e.preventDefault(); void submit(); }}>
        <p>The account must choose a new password the next time it signs in.</p>
        {formError && <p className="form-error" role="alert">{formError}</p>}
        <TemporaryPasswordInput label="Temporary password" value={password} onChange={setPassword} error={error} />
      </form>
    </Dialog>
  );
}

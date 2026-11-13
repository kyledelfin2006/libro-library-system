import { useRef, useState } from 'react';
import { createAssistant, createFaculty } from '../api/accounts';
import { ApiError } from '../api/client';
import type { UserResponse } from '../api/types';
import { describeError, useFocusFirstInvalid } from '../lib/forms';
import { isPasswordValid } from '../lib/password';
import { TemporaryPasswordInput } from './TemporaryPasswordInput';
import { TextField } from './TextField';
import { Dialog } from './ui';

type Kind = 'FACULTY' | 'ADMINISTRATOR';

/**
 * Librarian-only account creation for faculty or library assistants. Course and major are never sent, because the
 * server requires them empty for these roles. Mount it only while open so the initial password is dropped on close.
 */
export function CreateAccountDialog({
  onClose,
  onCreated,
  onDenied,
}: {
  onClose: () => void;
  onCreated: (created: UserResponse) => void;
  onDenied: () => void;
}) {
  const form = useRef<HTMLFormElement>(null);
  const [kind, setKind] = useState<Kind>('FACULTY');
  const [v, setV] = useState({ universityId: '', firstName: '', lastName: '', middleInitial: '', email: '' });
  const [password, setPassword] = useState('');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [attempt, setAttempt] = useState(0);
  useFocusFirstInvalid(form, attempt);

  const set = (k: keyof typeof v) => (e: { target: { value: string } }) => setV((s) => ({ ...s, [k]: e.target.value }));

  async function submit() {
    if (busy) return;
    const found: Record<string, string> = {};
    if (!/^\d{4}-\d{4}$/.test(v.universityId.trim())) found.universityId = 'Use the format 2025-4321.';
    if (!v.firstName.trim()) found.firstName = 'First name is required.';
    if (!v.lastName.trim()) found.lastName = 'Last name is required.';
    if (v.middleInitial && !/^[A-Za-z]$/.test(v.middleInitial)) found.middleInitial = 'Use one letter.';
    if (!/^\S+@\S+\.\S+$/.test(v.email.trim())) found.email = 'Enter a valid email address.';
    if (!isPasswordValid(password)) found.password = 'The initial password does not meet every requirement below.';
    setErrors(found);
    setFormError(null);
    if (Object.keys(found).length > 0) {
      setAttempt((n) => n + 1);
      return;
    }
    setBusy(true);
    try {
      const body = {
        universityId: v.universityId.trim(),
        firstName: v.firstName.trim(),
        lastName: v.lastName.trim(),
        ...(v.middleInitial ? { middleInitial: v.middleInitial } : {}),
        email: v.email.trim(),
        password,
      };
      const created = await (kind === 'FACULTY' ? createFaculty(body) : createAssistant(body));
      setPassword('');
      onCreated(created);
    } catch (err) {
      if (err instanceof ApiError && err.status === 403) {
        setPassword('');
        onDenied();
      } else if (err instanceof ApiError && Object.keys(err.fieldErrors).length > 0) {
        const { _global, ...fields } = err.fieldErrors;
        setErrors(fields);
        if (_global) setFormError(_global);
        setAttempt((n) => n + 1);
      } else {
        setFormError(describeError(err));
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <Dialog open title="New account" confirmLabel="Create account" busy={busy} onConfirm={() => void submit()} onClose={() => !busy && onClose()}>
      <form ref={form} noValidate onSubmit={(e) => { e.preventDefault(); void submit(); }}>
        {formError && <p className="form-error" role="alert">{formError}</p>}
        <fieldset className="field">
          <legend>Account type</legend>
          <label className="checkbox">
            <input type="radio" name="kind" checked={kind === 'FACULTY'} onChange={() => setKind('FACULTY')} /> Faculty
          </label>
          <label className="checkbox">
            <input type="radio" name="kind" checked={kind === 'ADMINISTRATOR'} onChange={() => setKind('ADMINISTRATOR')} /> Library assistant
          </label>
        </fieldset>
        <TextField label="University ID" value={v.universityId} onChange={set('universityId')} error={errors.universityId} placeholder="2025-4321" maxLength={9} autoComplete="off" />
        <TextField label="First name" value={v.firstName} onChange={set('firstName')} error={errors.firstName} maxLength={50} />
        <TextField label="Last name" value={v.lastName} onChange={set('lastName')} error={errors.lastName} maxLength={50} />
        <TextField label="Middle initial (optional)" value={v.middleInitial} onChange={set('middleInitial')} error={errors.middleInitial} maxLength={1} />
        <TextField label="Email" type="email" value={v.email} onChange={set('email')} error={errors.email} maxLength={150} />
        <TemporaryPasswordInput
          label="Initial password"
          value={password}
          onChange={setPassword}
          error={errors.password}
          hint="Ask the owner to change it after their first sign-in."
        />
      </form>
    </Dialog>
  );
}

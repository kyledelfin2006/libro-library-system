import { useRef, useState, type FormEvent } from 'react';
import { ApiError } from '../api/client';
import type { UserResponse } from '../api/types';
import { patchUser, type ProfilePatch } from '../api/users';
import { describeError, useFocusFirstInvalid } from '../lib/forms';
import { TextField } from './TextField';
import { Button } from './ui';

/**
 * Profile edit form for any account the caller may edit (their own on My Account, or a managed one on Accounts).
 * Client checks mirror the DTO, only changed fields are sent, and server field errors map onto the fields.
 */
export function ProfileForm({
  target,
  onSaved,
  onCancel,
}: {
  target: UserResponse;
  /** Called with the saved profile after a successful PATCH, or with null when nothing changed. */
  onSaved: (saved: UserResponse | null) => void | Promise<void>;
  onCancel: () => void;
}) {
  const form = useRef<HTMLFormElement>(null);
  const [values, setValues] = useState({
    firstName: target.firstName,
    lastName: target.lastName,
    middleInitial: target.middleInitial ?? '',
    email: target.email,
  });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [attempt, setAttempt] = useState(0);
  useFocusFirstInvalid(form, attempt);

  const set = (k: keyof typeof values) => (e: { target: { value: string } }) => setValues((v) => ({ ...v, [k]: e.target.value }));

  async function save(e: FormEvent) {
    e.preventDefault();
    if (saving) return;
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
    // Only changed fields are sent.
    const patch: ProfilePatch = {};
    if (values.firstName.trim() !== target.firstName) patch.firstName = values.firstName.trim();
    if (values.lastName.trim() !== target.lastName) patch.lastName = values.lastName.trim();
    if (values.middleInitial !== (target.middleInitial ?? '')) patch.middleInitial = values.middleInitial;
    if (values.email.trim() !== target.email) patch.email = values.email.trim();
    if (Object.keys(patch).length === 0) {
      await onSaved(null);
      return;
    }
    setSaving(true);
    try {
      const saved = await patchUser(target.universityId, patch);
      await onSaved(saved);
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
    <form ref={form} onSubmit={save} noValidate>
      {formError && <p className="form-error" role="alert">{formError}</p>}
      <TextField label="First name" value={values.firstName} onChange={set('firstName')} error={errors.firstName} maxLength={50} />
      <TextField label="Last name" value={values.lastName} onChange={set('lastName')} error={errors.lastName} maxLength={50} />
      <TextField label="Middle initial (optional)" value={values.middleInitial} onChange={set('middleInitial')} error={errors.middleInitial} maxLength={1} />
      <TextField label="Email" type="email" value={values.email} onChange={set('email')} error={errors.email} maxLength={150} />
      <div className="form-actions">
        <Button type="submit" disabled={saving}>{saving ? 'Saving…' : 'Save profile'}</Button>
        <Button variant="secondary" onClick={onCancel} disabled={saving}>Cancel</Button>
      </div>
    </form>
  );
}

import { useRef, useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { signup } from '../api/auth';
import { ApiError } from '../api/client';
import type { SignupRequest, UserCourse, UserITMajor } from '../api/types';
import { AuthLayout } from '../components/AuthLayout';
import { PasswordChecklist } from '../components/PasswordChecklist';
import { PasswordField, TextField } from '../components/TextField';
import { useToast } from '../components/Toast';
import { describeError, useFocusFirstInvalid } from '../lib/forms';
import { isPasswordValid } from '../lib/password';
import { useCountdown } from '../lib/useCountdown';

const COURSES: UserCourse[] = ['IT', 'EMC', 'IS'];
const MAJORS: UserITMajor[] = ['SE', 'SMBPO', 'IST', 'HN'];

/** Student self-registration. The role is never offered: the server creates STUDENT accounts only. */
export function Register() {
  const navigate = useNavigate();
  const { notify } = useToast();
  const form = useRef<HTMLFormElement>(null);
  const [values, setValues] = useState({
    universityId: '',
    firstName: '',
    lastName: '',
    middleInitial: '',
    email: '',
    userCourse: '' as UserCourse | '',
    major: '' as UserITMajor | '',
    password: '',
  });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [retryAfter, setRetryAfter] = useState<number | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const secondsLeft = useCountdown(retryAfter);
  const throttled = secondsLeft !== null && secondsLeft > 0;
  useFocusFirstInvalid(form, attempt);

  const set = (key: keyof typeof values) => (e: { target: { value: string } }) =>
    setValues((v) => ({ ...v, [key]: e.target.value }));

  /** Client-side checks mirror the DTO constraints; the server remains authoritative. */
  function validate(): Record<string, string> {
    const e: Record<string, string> = {};
    if (!/^\d{4}-\d{4}$/.test(values.universityId)) e.universityId = 'Enter your University ID like 2025-4321.';
    if (!values.firstName.trim()) e.firstName = 'First name is required.';
    if (!values.lastName.trim()) e.lastName = 'Last name is required.';
    if (!/^\S+@\S+\.\S+$/.test(values.email)) e.email = 'Enter a valid email address.';
    if (!values.userCourse) e.userCourse = 'Choose your course.';
    if (values.userCourse === 'IT' && !values.major) e.major = 'Choose your major.';
    if (!isPasswordValid(values.password)) e.password = 'The password does not meet every requirement below.';
    return e;
  }

  async function onSubmit(ev: FormEvent) {
    ev.preventDefault();
    if (submitting || throttled) return;
    const found = validate();
    setErrors(found);
    setFormError(null);
    if (Object.keys(found).length > 0) {
      setAttempt((n) => n + 1);
      return;
    }
    const body: SignupRequest = {
      universityId: values.universityId,
      password: values.password,
      firstName: values.firstName.trim(),
      lastName: values.lastName.trim(),
      email: values.email.trim(),
      userCourse: values.userCourse as UserCourse,
      ...(values.middleInitial ? { middleInitial: values.middleInitial } : {}),
      // Major is sent only for IT students.
      ...(values.userCourse === 'IT' && values.major ? { major: values.major } : {}),
    };
    setSubmitting(true);
    try {
      await signup(body);
      notify('Account created. Sign in to continue.');
      navigate('/signin', { replace: true });
    } catch (err) {
      if (err instanceof ApiError && err.status === 429) {
        setRetryAfter(err.retryAfterSeconds ?? 60);
      } else if (err instanceof ApiError && Object.keys(err.fieldErrors).length > 0) {
        // Map server field errors onto fields; object-level errors (_global) go to the form summary.
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
    <AuthLayout title="Create a student account">
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
        <TextField label="University ID" value={values.universityId} onChange={set('universityId')}
          error={errors.universityId} placeholder="2025-4321" autoComplete="username" />
        <TextField label="First name" value={values.firstName} onChange={set('firstName')}
          error={errors.firstName} maxLength={50} autoComplete="given-name" />
        <TextField label="Last name" value={values.lastName} onChange={set('lastName')}
          error={errors.lastName} maxLength={50} autoComplete="family-name" />
        <TextField label="Middle initial (optional)" value={values.middleInitial} onChange={set('middleInitial')}
          error={errors.middleInitial} maxLength={1} />
        <TextField label="Email" type="email" value={values.email} onChange={set('email')}
          error={errors.email} maxLength={150} autoComplete="email" />
        <div className="field">
          <label htmlFor="course">Course</label>
          <select id="course" value={values.userCourse}
            aria-invalid={errors.userCourse ? true : undefined}
            onChange={(e) => setValues((v) => ({ ...v, userCourse: e.target.value as UserCourse | '', major: '' }))}>
            <option value="">Select a course</option>
            {COURSES.map((c) => <option key={c} value={c}>{c}</option>)}
          </select>
          {errors.userCourse && <div className="field-error">{errors.userCourse}</div>}
        </div>
        {values.userCourse === 'IT' && (
          <div className="field">
            <label htmlFor="major">Major</label>
            <select id="major" value={values.major} aria-invalid={errors.major ? true : undefined}
              onChange={set('major')}>
              <option value="">Select a major</option>
              {MAJORS.map((m) => <option key={m} value={m}>{m}</option>)}
            </select>
            {errors.major && <div className="field-error">{errors.major}</div>}
          </div>
        )}
        <PasswordField label="Password" value={values.password} onChange={set('password')}
          error={errors.password} autoComplete="new-password" />
        <PasswordChecklist value={values.password} />
        <button type="submit" className="btn btn-primary" disabled={submitting || throttled}>
          {submitting ? 'Creating account…' : 'Create account'}
        </button>
      </form>
      <p className="auth-alt">
        Already registered? <Link to="/signin">Sign in</Link>
      </p>
    </AuthLayout>
  );
}

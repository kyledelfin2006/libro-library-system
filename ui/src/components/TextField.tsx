import { forwardRef, useId, useState, type InputHTMLAttributes, type ReactNode } from 'react';

interface TextFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string;
  error?: string | undefined;
  hint?: ReactNode;
}

/** Labelled input with an associated error and hint. Errors are linked with aria-describedby. */
export const TextField = forwardRef<HTMLInputElement, TextFieldProps>(function TextField(
  { label, error, hint, id, ...rest },
  ref,
) {
  const generated = useId();
  const fieldId = id ?? generated;
  const describedBy = [error ? `${fieldId}-error` : null, hint ? `${fieldId}-hint` : null].filter(Boolean).join(' ');
  return (
    <div className="field">
      <label htmlFor={fieldId}>{label}</label>
      <input
        ref={ref}
        id={fieldId}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy || undefined}
        {...rest}
      />
      {hint && (
        <div id={`${fieldId}-hint`} className="field-hint">
          {hint}
        </div>
      )}
      {error && (
        <div id={`${fieldId}-error`} className="field-error">
          {error}
        </div>
      )}
    </div>
  );
});

/** Password input with a keyboard-accessible show/hide toggle. */
export const PasswordField = forwardRef<HTMLInputElement, TextFieldProps>(function PasswordField(props, ref) {
  const [visible, setVisible] = useState(false);
  const generated = useId();
  const id = props.id ?? generated;
  return (
    <div className="password-wrap">
      <TextField {...props} id={id} ref={ref} type={visible ? 'text' : 'password'} />
      <button
        type="button"
        className="password-toggle"
        aria-pressed={visible}
        aria-controls={id}
        onClick={() => setVisible((v) => !v)}
      >
        {visible ? 'Hide' : 'Show'}
        <span className="visually-hidden"> password</span>
      </button>
    </div>
  );
});

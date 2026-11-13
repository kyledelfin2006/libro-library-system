import { useState } from 'react';
import { generatePassword } from '../lib/password';
import { PasswordChecklist } from './PasswordChecklist';
import { PasswordField } from './TextField';
import { Button } from './ui';

/**
 * Password entry for staff-set passwords (a reset, or a new staff account). Staff can type one or press Generate.
 * The value is owned by the parent's local state and must never reach the query cache, a URL, storage, a toast or a log.
 * The parent drops it when its dialog closes (the dialog component unmounts).
 */
export function TemporaryPasswordInput({
  label,
  value,
  onChange,
  error,
  hint,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  error?: string | undefined;
  hint?: string;
}) {
  const [generated, setGenerated] = useState(false);
  const [copied, setCopied] = useState<'copied' | 'failed' | null>(null);

  async function copy() {
    try {
      await navigator.clipboard.writeText(value);
      setCopied('copied');
    } catch {
      // The clipboard can be blocked; the password stays visible below so it can be copied by hand.
      setCopied('failed');
    }
  }

  return (
    <div>
      <PasswordField
        label={label}
        value={value}
        onChange={(e) => {
          setGenerated(false);
          setCopied(null);
          onChange(e.target.value);
        }}
        error={error}
        hint={hint}
        autoComplete="off"
        spellCheck={false}
      />
      <PasswordChecklist value={value} />
      <div className="form-actions">
        <Button
          variant="secondary"
          onClick={() => {
            onChange(generatePassword());
            setGenerated(true);
            setCopied(null);
          }}
        >
          Generate
        </Button>
        {generated && value && <Button variant="secondary" onClick={() => void copy()}>Copy</Button>}
      </div>
      {generated && value && (
        <div role="status">
          <p>
            Generated password: <code>{value}</code>
          </p>
          <p className="field-hint">
            Share this with the owner. It is shown once and not stored. Copying places it on your clipboard, which this page cannot clear.
          </p>
          {copied === 'copied' && <p className="field-hint">Copied.</p>}
          {copied === 'failed' && <p className="field-hint">Copy failed. Select the password above instead.</p>}
        </div>
      )}
    </div>
  );
}

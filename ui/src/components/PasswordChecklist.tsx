import { checkPassword } from '../lib/password';

/** Live policy checklist. Each rule shows an icon and text, so color is never the only signal. */
export function PasswordChecklist({ value }: { value: string }) {
  return (
    <ul className="checklist" aria-label="Password requirements">
      {checkPassword(value).map((c) => (
        <li key={c.key} data-ok={c.ok}>
          <span aria-hidden="true">{c.ok ? '✓' : '○'}</span> {c.label}
          <span className="visually-hidden">{c.ok ? ' (met)' : ' (not met)'}</span>
        </li>
      ))}
    </ul>
  );
}

/** Client mirror of the server PasswordPolicy so hints appear before submission. The server stays authoritative. */
export const MAX_PASSWORD_LENGTH = 72;

export interface PasswordCheck {
  key: string;
  label: string;
  ok: boolean;
}

/** Counts UTF-8 bytes: BCrypt accepts at most 72 bytes, which can be fewer than 72 characters. */
export function utf8Bytes(value: string): number {
  return new TextEncoder().encode(value).length;
}

/** Returns each policy rule with its pass/fail state for the live checklist. */
export function checkPassword(value: string): PasswordCheck[] {
  return [
    { key: 'length', label: '8 to 72 characters', ok: value.length >= 8 && value.length <= MAX_PASSWORD_LENGTH },
    { key: 'bytes', label: 'At most 72 bytes', ok: utf8Bytes(value) <= 72 },
    { key: 'upper', label: 'An upper case letter', ok: /[A-Z]/.test(value) },
    { key: 'lower', label: 'A lower case letter', ok: /[a-z]/.test(value) },
    { key: 'number', label: 'A number', ok: /\d/.test(value) },
    { key: 'symbol', label: 'A symbol', ok: /[^A-Za-z0-9\s]/.test(value) },
  ];
}

export function isPasswordValid(value: string): boolean {
  return checkPassword(value).every((c) => c.ok);
}

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

const UPPER = 'ABCDEFGHJKLMNPQRSTUVWXYZ';
const LOWER = 'abcdefghijkmnopqrstuvwxyz';
const DIGITS = '23456789';
const SYMBOLS = '!#$%&*+-=?@^_';
const ALL = UPPER + LOWER + DIGITS + SYMBOLS;

/** Uniform random index below `max` from crypto.getRandomValues, using rejection sampling to avoid modulo bias. */
function randomBelow(max: number): number {
  const limit = Math.floor(0x100000000 / max) * max;
  const buffer = new Uint32Array(1);
  do {
    crypto.getRandomValues(buffer);
  } while (buffer[0]! >= limit);
  return buffer[0]! % max;
}

const pick = (set: string) => set[randomBelow(set.length)]!;

/**
 * Generates a temporary password that satisfies the policy: at least one upper case letter, lower case letter,
 * number and symbol, from an ASCII-only alphabet (no look-alike characters), so characters equal bytes and the
 * 72-byte BCrypt limit always holds. The default length is 16.
 */
export function generatePassword(length = 16): string {
  const size = Math.min(MAX_PASSWORD_LENGTH, Math.max(8, length));
  const chars = [pick(UPPER), pick(LOWER), pick(DIGITS), pick(SYMBOLS)];
  while (chars.length < size) chars.push(pick(ALL));
  // Fisher-Yates shuffle so the guaranteed characters are not always first.
  for (let i = chars.length - 1; i > 0; i--) {
    const j = randomBelow(i + 1);
    [chars[i], chars[j]] = [chars[j]!, chars[i]!];
  }
  return chars.join('');
}

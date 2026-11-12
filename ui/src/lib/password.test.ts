import { checkPassword, isPasswordValid, utf8Bytes } from './password';

describe('password policy mirror', () => {
  it('accepts a compliant password', () => {
    expect(isPasswordValid('Library#2026')).toBe(true);
  });

  it('flags each missing rule', () => {
    const failed = (v: string) => checkPassword(v).filter((c) => !c.ok).map((c) => c.key);
    expect(failed('short1A!')).toEqual([]);
    expect(failed('alllowercase1!')).toEqual(['upper']);
    expect(failed('ALLUPPERCASE1!')).toEqual(['lower']);
    expect(failed('NoNumbersHere!')).toEqual(['number']);
    expect(failed('NoSymbols1234A')).toEqual(['symbol']);
  });

  it('counts UTF-8 bytes, not characters', () => {
    // 40 emoji are 40 characters pairs (80 UTF-16 units) but 160 bytes.
    const emoji = 'A1!a' + '😀'.repeat(20);
    expect(utf8Bytes(emoji)).toBeGreaterThan(72);
    expect(isPasswordValid(emoji)).toBe(false);
  });

  it('rejects more than 72 characters', () => {
    expect(isPasswordValid('Aa1!' + 'x'.repeat(69))).toBe(false);
  });
});

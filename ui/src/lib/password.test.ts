import { checkPassword, generatePassword, isPasswordValid, utf8Bytes } from './password';

describe('generatePassword', () => {
  it('always satisfies the policy across many samples', () => {
    const seen = new Set<string>();
    for (let i = 0; i < 1000; i++) {
      const p = generatePassword();
      expect(isPasswordValid(p), p).toBe(true);
      expect(p.length).toBe(16);
      expect(utf8Bytes(p)).toBe(p.length);
      seen.add(p);
    }
    expect(seen.size).toBe(1000);
  });

  it('clamps the length to the policy bounds', () => {
    expect(generatePassword(2).length).toBe(8);
    expect(generatePassword(500).length).toBe(72);
    expect(isPasswordValid(generatePassword(72))).toBe(true);
  });
});

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

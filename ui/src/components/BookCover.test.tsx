import { render } from '@testing-library/react';
import { BookCover, CLOTH_COLORS, clothIndex, patternIndex, wrapTitle } from './BookCover';
import { contrast } from '../lib/contrast';

const base = { id: 1, title: 'Dune', author: 'Frank Herbert', genre: 'Sci-fi', copyStatus: 'IN_COLLECTION' as const, available: true };

describe('BookCover', () => {
  it('maps the same genre to the same cloth, case-insensitively', () => {
    expect(clothIndex('Dystopian')).toBe(clothIndex(' dystopian '));
  });

  it('keeps ivory text readable on every cloth color', () => {
    for (const cloth of CLOTH_COLORS) expect(contrast('#F4EFE0', cloth)).toBeGreaterThanOrEqual(7);
  });

  it('cycles through four patterns by copy id', () => {
    expect([0, 1, 2, 3, 4].map(patternIndex)).toEqual([0, 1, 2, 3, 0]);
  });

  it('is decorative and shows no ribbon for an available copy', () => {
    const { container } = render(<BookCover book={base} />);
    const svg = container.querySelector('svg')!;
    expect(svg).toHaveAttribute('aria-hidden', 'true');
    expect(container.textContent).not.toMatch(/ON LOAN|LOST/);
  });

  it('shows an On loan ribbon and a Lost ribbon', () => {
    expect(render(<BookCover book={{ ...base, available: false }} />).container.textContent).toContain('ON LOAN');
    expect(render(<BookCover book={{ ...base, copyStatus: 'LOST', available: false }} />).container.textContent).toContain('LOST');
  });

  it('clamps long titles to four lines with an ellipsis', () => {
    const lines = wrapTitle('A very long title that keeps going on and on across many many words indeed yes', 11);
    expect(lines).toHaveLength(4);
    expect(lines[3]).toMatch(/…$/);
  });
});

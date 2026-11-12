import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { contrast } from '../lib/contrast';

const css = readFileSync(resolve(__dirname, 'tokens.css'), 'utf8');

/** Extracts the custom properties declared inside a selector block of tokens.css. */
function tokens(selector: string): Record<string, string> {
  const start = css.indexOf(selector);
  const block = css.slice(css.indexOf('{', start) + 1, css.indexOf('}', start));
  return Object.fromEntries([...block.matchAll(/--([\w-]+):\s*(#[0-9a-fA-F]{6})/g)].map((m) => [m[1]!, m[2]!]));
}

const themes = { dark: tokens(":root,\n:root[data-theme='dark']"), light: tokens(":root[data-theme='light']") };

// [foreground, background, minimum ratio]: 4.5 for text, 3 for non-text (plan section 5.10).
const pairs: [string, string, number][] = [
  ['text', 'bg', 4.5],
  ['text', 'surface', 4.5],
  ['text-muted', 'bg', 4.5],
  ['text-muted', 'surface-raised', 4.5],
  ['primary', 'surface', 4.5],
  ['primary-hover', 'surface', 4.5],
  ['on-primary', 'primary', 4.5],
  ['text', 'primary-subtle', 4.5],
  ['brass', 'surface', 4.5],
  ['danger', 'surface', 4.5],
  ['warning', 'warning-bg', 4.5],
  ['info', 'surface', 4.5],
  ['border-input', 'surface', 3],
  ['focus-ring', 'bg', 3],
  ...[1, 2, 3, 4, 5].map((n): [string, string, number] => [`chart-${n}`, 'surface', 3]),
  ...['active', 'overdue', 'returned', 'lost'].map((n): [string, string, number] => [`status-${n}`, 'surface', 3]),
];

describe.each(Object.entries(themes))('%s theme contrast', (_name, t) => {
  it.each(pairs)('%s on %s is at least %s:1', (fg, bg, min) => {
    const ratio = contrast(t[`color-${fg}`] ?? t[fg]!, t[`color-${bg}`]!);
    expect(ratio).toBeGreaterThanOrEqual(min);
  });
});

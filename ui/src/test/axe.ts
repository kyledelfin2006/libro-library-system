import axe from 'axe-core';

/**
 * Runs axe against a container and returns serious or critical violations. jsdom has no layout or computed color,
 * so the color-contrast rule is skipped here; contrast is covered by the token test (plan section 5.10).
 */
export async function seriousViolations(container: Element): Promise<string[]> {
  const results = await axe.run(container, { rules: { 'color-contrast': { enabled: false } } });
  return results.violations
    .filter((v) => v.impact === 'serious' || v.impact === 'critical')
    .map((v) => `${v.id}: ${v.nodes.map((n) => n.target.join(' ')).join(', ')}`);
}

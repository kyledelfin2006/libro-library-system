import type { Datum } from '../components/charts';
import type { LoanResponse } from '../api/types';
import { daysUntil } from './dates';

/** Collection by genre: the top `limit` genres plus an "Other" bar for the rest (plan section 5.8). */
export function genreBars(distribution: Record<string, number>, limit = 8): Datum[] {
  const sorted = Object.entries(distribution).sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0]));
  const top = sorted.slice(0, limit).map(([label, value]): Datum => ({ label, value, color: 'var(--chart-1)' }));
  const other = sorted.slice(limit).reduce((sum, [, v]) => sum + v, 0);
  return other > 0 ? [...top, { label: 'Other', value: other, color: 'var(--chart-2)' }] : top;
}

/** Overdue loans grouped by whole days late: 1-3, 4-7, 8-14, 15 or more (a loan due earlier today counts as 1-3). */
export function overdueBuckets(loans: Pick<LoanResponse, 'dueAt'>[], now: Date = new Date()): Datum[] {
  const buckets = [
    { label: '1-3 days', value: 0 },
    { label: '4-7 days', value: 0 },
    { label: '8-14 days', value: 0 },
    { label: '15+ days', value: 0 },
  ];
  for (const loan of loans) {
    const late = -daysUntil(loan.dueAt, now);
    const i = late <= 3 ? 0 : late <= 7 ? 1 : late <= 14 ? 2 : 3;
    buckets[i]!.value += 1;
  }
  return buckets.map((b) => ({ ...b, color: 'var(--status-overdue)' }));
}

/** Loan status mix. Overdue is a state of ACTIVE on the server, so it is split out of the active count. */
export function statusMix(c: { active: number; overdue: number; returned: number; lost: number }): Datum[] {
  return [
    { label: 'Active', value: Math.max(0, c.active - c.overdue), color: 'var(--status-active)' },
    { label: 'Overdue', value: c.overdue, color: 'var(--status-overdue)' },
    { label: 'Returned', value: c.returned, color: 'var(--status-returned)' },
    { label: 'Lost', value: c.lost, color: 'var(--status-lost)' },
  ];
}

/** Takeaway caption computed from the data, not just a label. */
export function statusTakeaway(c: { active: number; overdue: number }): string {
  return c.active === 0 ? 'No active loans right now' : `${c.overdue} of ${c.active} active loans are overdue`;
}

/** "Mon 9 Nov" style week label from an ISO date, formatted without the browser time zone. */
export function weekLabel(iso: string): string {
  const [y, m, d] = iso.split('-').map(Number) as [number, number, number];
  const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
  return `${d} ${months[m - 1]} ${y}`;
}

/**
 * Stored values are Manila local time with no offset ("2026-10-28T15:30:00"). They are formatted from the string
 * itself, never through the browser's time zone, so every viewer sees the same wall-clock time.
 */
const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
const DAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];

function parts(local: string): { y: number; m: number; d: number } {
  const [y, m, d] = local.slice(0, 10).split('-').map(Number) as [number, number, number];
  return { y, m, d };
}

/** Whole days since the epoch for a calendar date; avoids any time zone arithmetic. */
function dayNumber({ y, m, d }: { y: number; m: number; d: number }): number {
  return Math.round(Date.UTC(y, m - 1, d) / 86_400_000);
}

/** "Mon 23 Nov 2026" for a Manila local date-time string. */
export function formatDate(local: string): string {
  const p = parts(local);
  const weekday = DAYS[new Date(Date.UTC(p.y, p.m - 1, p.d)).getUTCDay()];
  return `${weekday} ${p.d} ${MONTHS[p.m - 1]} ${p.y}`;
}

/** "Sat 14 Nov 2026, 15:30" for a Manila local date-time string (the page footer says times are Manila time). */
export function formatDateTime(local: string): string {
  return `${formatDate(local)}, ${local.slice(11, 16)}`;
}

/** Today's calendar date in Manila, whatever the browser's time zone. */
export function manilaToday(now: Date = new Date()): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Manila' }).format(now); // YYYY-MM-DD
}

/** Calendar days from today (Manila) to the given date; negative when in the past. */
export function daysUntil(local: string, now: Date = new Date()): number {
  return dayNumber(parts(local)) - dayNumber(parts(manilaToday(now)));
}

/** "in 9 days", "tomorrow", "today", "2 days overdue". Overdue wording is only for loans the server flags. */
export function relativeDays(days: number): string {
  if (days === 0) return 'today';
  if (days === 1) return 'tomorrow';
  if (days === -1) return '1 day ago';
  return days > 0 ? `in ${days} days` : `${Math.abs(days)} days ago`;
}

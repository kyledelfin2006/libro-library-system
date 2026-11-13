import { genreBars, overdueBuckets, statusMix, statusTakeaway, weekLabel } from './dashboard';

describe('dashboard helpers', () => {
  it('keeps the top 8 genres and folds the rest into Other', () => {
    const dist = Object.fromEntries(Array.from({ length: 10 }, (_, i) => [`G${i}`, 10 - i]));
    const bars = genreBars(dist);
    expect(bars).toHaveLength(9);
    expect(bars[0]).toMatchObject({ label: 'G0', value: 10 });
    expect(bars.at(-1)).toMatchObject({ label: 'Other', value: 2 + 1 });
  });

  it('does not add an Other bar when 8 or fewer genres exist', () => {
    expect(genreBars({ A: 1, B: 2 }).map((b) => b.label)).toEqual(['B', 'A']);
  });

  it('buckets overdue loans by whole days late', () => {
    const now = new Date('2026-11-20T04:00:00Z'); // 2026-11-20 12:00 Manila
    const due = (iso: string) => ({ dueAt: `${iso}T10:00:00` });
    const buckets = overdueBuckets(
      [due('2026-11-20'), due('2026-11-18'), due('2026-11-15'), due('2026-11-10'), due('2026-11-01'), due('2026-10-01')],
      now,
    );
    expect(buckets.map((b) => b.value)).toEqual([2, 1, 1, 2]);
  });

  it('splits overdue out of active, since overdue is a state of ACTIVE', () => {
    const mix = statusMix({ active: 40, overdue: 12, returned: 30, lost: 3 });
    expect(mix.map((d) => [d.label, d.value])).toEqual([['Active', 28], ['Overdue', 12], ['Returned', 30], ['Lost', 3]]);
  });

  it('computes the takeaway caption', () => {
    expect(statusTakeaway({ active: 40, overdue: 12 })).toBe('12 of 40 active loans are overdue');
    expect(statusTakeaway({ active: 0, overdue: 0 })).toBe('No active loans right now');
  });

  it('formats week labels without the browser time zone', () => {
    expect(weekLabel('2026-11-09')).toBe('9 Nov 2026');
  });
});

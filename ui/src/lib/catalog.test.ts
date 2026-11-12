import { toQueryString } from '../api/books';
import { ApiError } from '../api/client';
import { activeChips, clearFilters, parseCatalogParams, removeChip, toCatalogParams } from './catalogParams';
import { daysUntil, formatDate, manilaToday, relativeDays } from './dates';
import { borrowBlock, borrowErrorMessage, borrowSuccessMessage } from './loanMessages';

describe('catalog URL state', () => {
  it('round-trips filters, sort, size, page, and view', () => {
    const sp = new URLSearchParams('q=dune&genre=Sci-fi&min=10&available=true&sort=replacementCost,desc&size=24&page=2&view=list');
    const { query, view } = parseCatalogParams(sp);
    expect(query).toMatchObject({
      title: 'dune', genre: 'Sci-fi', minReplacementCost: '10', available: true,
      sort: 'replacementCost', dir: 'desc', size: 24, page: 2,
    });
    expect(view).toBe('list');
    expect(parseCatalogParams(toCatalogParams(query, view))).toEqual({ query, view });
  });

  it('ignores invalid sort fields, sizes, and pages instead of passing them to the API', () => {
    const { query } = parseCatalogParams(new URLSearchParams('sort=password,asc&size=500&page=-3'));
    expect(query).toMatchObject({ sort: 'title', size: 12, page: 0 });
  });

  it('omits defaults and empty filters', () => {
    const { query } = parseCatalogParams(new URLSearchParams('q=  '));
    expect(toQueryString(query)).toBe('page=0&size=12&sort=title%2Casc');
    expect(toCatalogParams(query, 'grid').toString()).toBe('');
  });

  it('removing a filter or clearing all resets to page one and keeps sort and size', () => {
    const { query } = parseCatalogParams(new URLSearchParams('q=a&genre=g&min=1&max=2&size=24&page=3&sort=author,asc'));
    expect(activeChips(query).map((c) => c.key)).toEqual(['title', 'genre', 'cost']);
    expect(removeChip(query, 'cost')).toMatchObject({ page: 0, genre: 'g' });
    expect(removeChip(query, 'cost').minReplacementCost).toBeUndefined();
    expect(clearFilters(query)).toEqual({ page: 0, size: 24, sort: 'author', dir: 'asc' });
  });
});

describe('dates', () => {
  it('formats stored Manila local time without using the browser time zone', () => {
    expect(formatDate('2026-11-23T15:30:00')).toBe('Mon 23 Nov 2026');
  });
  it('counts calendar days in Manila', () => {
    const now = new Date('2026-11-14T17:00:00Z'); // 2026-11-15 01:00 in Manila
    expect(manilaToday(now)).toBe('2026-11-15');
    expect(daysUntil('2026-11-29T10:00:00', now)).toBe(14);
    expect(daysUntil('2026-11-13T10:00:00', now)).toBe(-2);
  });
  it('words relative days', () => {
    expect([0, 1, 9, -1, -3].map(relativeDays)).toEqual(['today', 'tomorrow', 'in 9 days', '1 day ago', '3 days ago']);
  });
});

describe('loan messages', () => {
  it.each([
    ['LOAN_COPY_UNAVAILABLE', 'This copy is on loan. Check back after it is returned.'],
    ['LOAN_BORROWER_OVERDUE', 'Return your overdue book before you borrow another.'],
    ['LOAN_LIMIT_REACHED', 'You have 3 books checked out, the most the library allows. Return one to borrow another.'],
  ])('maps %s by code, not by server text', (code, copy) => {
    expect(borrowErrorMessage(new ApiError(409, code, 'server text that must not be shown'))).toBe(copy);
  });
  it('shows a reference for unexpected server errors', () => {
    expect(borrowErrorMessage(new ApiError(500, null, 'boom', {}, '3f2b8c1e-0000'))).toContain('Reference: 3f2b8c1e');
  });
  it('explains blocks from the same rules the server applies', () => {
    expect(borrowBlock([{ status: 'ACTIVE', overdue: true }])).toBe('overdue');
    expect(borrowBlock(Array(3).fill({ status: 'ACTIVE', overdue: false }))).toBe('limit');
    expect(borrowBlock([{ status: 'RETURNED', overdue: false }])).toBeNull();
  });
  it('words a successful borrow with the due date', () => {
    const due = new Date(Date.now() + 14 * 86_400_000);
    const iso = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Manila' }).format(due) + 'T10:00:00';
    expect(borrowSuccessMessage({ dueAt: iso } as never)).toMatch(/^Borrowed\. Due \w{3} \d+ \w{3} \d{4}, 14 days from today\.$/);
  });
});

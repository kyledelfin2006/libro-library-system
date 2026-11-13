import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, vi } from 'vitest';
import { auditQueryString } from '../api/audit';
import { actionLabel, actorRoleOptions, isBackwardsRange, targetRoleOptions } from '../lib/audit';
import { fail, ok, pageOf, renderAt, routeFetch, staffUser } from '../test/render';

afterEach(() => vi.unstubAllGlobals());

const assistant = { ...staffUser, universityId: '2020-0002', userRole: 'ADMINISTRATOR' as const };
const event = (over: Record<string, unknown> = {}) => ({
  id: 7, occurredAt: '2026-11-13T09:30:00', actorUniversityId: '2020-0001', actorRole: 'LIBRARIAN', targetRole: null,
  action: 'BOOK_CREATED', entityType: 'BOOK', entityId: '42', summary: 'book created', ...over,
});
const calls = (mock: ReturnType<typeof routeFetch>) => mock.mock.calls.map((c) => new URL(c[0], 'http://x').searchParams);

describe('audit query and helpers', () => {
  it('drops empty filters, widens days to whole days, and clamps the page size', () => {
    const qs = new URLSearchParams(auditQueryString({ actor: ' 2025-1 ', from: '2026-11-01', to: '2026-11-13', page: -2, size: 500 }));
    expect(qs.get('actor')).toBe('2025-1');
    expect(qs.get('from')).toBe('2026-11-01T00:00:00');
    expect(qs.get('to')).toBe('2026-11-13T23:59:59');
    expect(qs.get('page')).toBe('0');
    expect(qs.get('size')).toBe('100');
    expect(qs.has('entityType')).toBe(false);
  });

  it('ignores a malformed day instead of sending it', () => {
    expect(new URLSearchParams(auditQueryString({ from: 'yesterday', page: 0, size: 12 })).has('from')).toBe(false);
  });

  it('offers assistants no filters for staff events, and the librarian all of them', () => {
    expect(actorRoleOptions('ADMINISTRATOR')).not.toContain('LIBRARIAN');
    expect(actorRoleOptions('ADMINISTRATOR')).not.toContain('ADMINISTRATOR');
    expect(targetRoleOptions('ADMINISTRATOR')).toEqual(['STUDENT', 'FACULTY']);
    expect(actorRoleOptions('LIBRARIAN')).toEqual(expect.arrayContaining(['ADMINISTRATOR', 'LIBRARIAN']));
    expect(targetRoleOptions('LIBRARIAN')).toHaveLength(4);
  });

  it('labels known actions and falls back to the server summary', () => {
    expect(actionLabel('PASSWORD_RESET')).toBe('Password reset by staff');
    expect(actionLabel('NEW_THING', 'new thing')).toBe('New thing');
    expect(isBackwardsRange('2026-11-13', '2026-11-01')).toBe(true);
    expect(isBackwardsRange('', '2026-11-01')).toBe(false);
  });
});

describe('audit page', () => {
  it('lists events in Manila time and opens a detail panel', async () => {
    routeFetch({ '/app/audit': () => ok(pageOf([event(), event({ id: 6, action: 'LOAN_MARKED_LOST', entityType: 'LOAN', entityId: '81', summary: 'loan marked lost' })])) });
    const user = userEvent.setup();
    renderAt('/audit', staffUser);
    expect(await screen.findByText('Book added')).toBeInTheDocument();
    expect(screen.getByText('Loan marked lost')).toBeInTheDocument();
    expect(screen.getAllByText(/Fri 13 Nov 2026, 09:30/).length).toBeGreaterThan(0);
    await user.click(screen.getByRole('button', { name: /Details for event 7/ }));
    const panel = await screen.findByRole('complementary', { name: 'Event 7 details' });
    expect(within(panel).getByText('book created')).toBeInTheDocument();
    await user.click(within(panel).getByRole('button', { name: 'Close' }));
    expect(screen.queryByRole('complementary')).toBeNull();
  });

  it('sends filters, resets to page one, and can clear them', async () => {
    const mock = routeFetch({ '/app/audit': () => ok(pageOf([event()], { totalElements: 40, totalPages: 4 })) });
    const user = userEvent.setup();
    renderAt('/audit?page=2', staffUser);
    await screen.findByText('Book added');
    await user.selectOptions(screen.getByLabelText('Entity'), 'LOAN');
    await waitFor(() => {
      const last = calls(mock).at(-1)!;
      expect(last.get('entityType')).toBe('LOAN');
      expect(last.get('page')).toBe('0');
    });
    await user.click(screen.getByRole('button', { name: 'Clear' }));
    await waitFor(() => expect(calls(mock).at(-1)!.has('entityType')).toBe(false));
  });

  it('does not offer assistants the staff role filters', async () => {
    routeFetch({ '/app/audit': () => ok(pageOf([])) });
    renderAt('/audit', assistant);
    await screen.findByText('No events recorded yet.');
    const actorRole = screen.getByLabelText('Actor role');
    expect(within(actorRole).queryByRole('option', { name: 'Librarian' })).toBeNull();
    expect(within(actorRole).getByRole('option', { name: 'Student' })).toBeInTheDocument();
    expect(within(screen.getByLabelText('Account role affected')).queryByRole('option', { name: 'Library assistant' })).toBeNull();
  });

  it('shows the librarian every role filter', async () => {
    routeFetch({ '/app/audit': () => ok(pageOf([])) });
    renderAt('/audit', staffUser);
    await screen.findByText('No events recorded yet.');
    expect(within(screen.getByLabelText('Actor role')).getByRole('option', { name: 'Library assistant' })).toBeInTheDocument();
  });

  it('reads a 403 as a denial, not a failure', async () => {
    routeFetch({ '/app/audit': () => fail(403) });
    renderAt('/audit', assistant);
    expect(await screen.findByText(/do not have permission to view those events/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Try again' })).toBeNull();
  });

  it('offers a retry on a server error', async () => {
    routeFetch({ '/app/audit': () => fail(500) });
    renderAt('/audit', staffUser);
    expect(await screen.findByRole('button', { name: 'Try again' })).toBeInTheDocument();
  });

  it('blocks a backwards date range without calling the server', async () => {
    const mock = routeFetch({ '/app/audit': () => ok(pageOf([])) });
    renderAt('/audit?from=2026-11-13&to=2026-11-01', staffUser);
    expect(await screen.findByRole('alert')).toHaveTextContent(/start date must be on or before/);
    expect(mock).not.toHaveBeenCalled();
  });

  it('keeps patrons out and puts Audit in the staff nav only', async () => {
    routeFetch({ '/app/books/query': () => ok(pageOf([])), '/app/books/genre': () => ok({}) });
    renderAt('/audit');
    expect(await screen.findByRole('heading', { name: 'Catalog' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Audit' })).toBeNull();
  });

  it('adds an Audit link for staff', async () => {
    routeFetch({ '/app/audit': () => ok(pageOf([])) });
    renderAt('/audit', assistant);
    const nav = await screen.findByRole('navigation', { name: 'Primary' });
    expect(within(nav).getByRole('link', { name: 'Audit' })).toBeInTheDocument();
  });
});

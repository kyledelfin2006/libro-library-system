import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, vi } from 'vitest';
import { isPasswordValid } from '../lib/password';
import { fail, ok, pageOf, renderAt, routeFetch, staffUser } from '../test/render';

afterEach(() => {
  vi.unstubAllGlobals();
  localStorage.clear();
  sessionStorage.clear();
});

const assistant = { ...staffUser, universityId: '2020-0002', userRole: 'ADMINISTRATOR' as const };
const account = (over: Record<string, unknown> = {}) => ({
  universityId: '2025-4321', firstName: 'Alex', lastName: 'Rivera', middleInitial: 'Q', email: 'alex@x.edu',
  userRole: 'STUDENT', userCourse: 'IT', major: 'SE', mustChangePassword: false, ...over,
});

type Routes = Parameters<typeof routeFetch>[0];
const faculty = () => account({ universityId: '2025-1177', firstName: 'Sam', lastName: 'Cruz', userRole: 'FACULTY', userCourse: null, major: null });
const directory = (rows: unknown[] = [account(), faculty()], extra: Routes = {}) =>
  routeFetch({ '/app/users?': () => ok(pageOf(rows)), ...extra });

const rolesIn = () => within(screen.getByLabelText('Role')).getAllByRole('option').map((o) => o.textContent);
const ACTIONS = ['Edit profile', 'Reset password', 'Delete account'];

describe('accounts directory', () => {
  it('limits the role filter to the assistant scope and hides New', async () => {
    directory();
    renderAt('/accounts', assistant);
    expect(await screen.findByText('Alex Q. Rivera')).toBeInTheDocument();
    expect(rolesIn()).toEqual(['All', 'Student', 'Faculty']);
    expect(screen.queryByRole('button', { name: /New faculty/ })).toBeNull();
  });

  it('lets the librarian filter assistants, never offers the librarian role, and shows New', async () => {
    directory();
    renderAt('/accounts', staffUser);
    await screen.findByText('Alex Q. Rivera');
    expect(rolesIn()).toEqual(['All', 'Student', 'Faculty', 'Library assistant']);
    expect(screen.getByRole('button', { name: /New faculty/ })).toBeInTheDocument();
  });

  it('keeps patrons out', async () => {
    routeFetch({ '/app/books/query': () => ok(pageOf([])), '/app/books/genre': () => ok({}) });
    renderAt('/accounts');
    expect(await screen.findByRole('heading', { name: 'Catalog' })).toBeInTheDocument();
  });

  it('offers actions on a managed row, but none on a librarian or your own row', async () => {
    const own = account({ universityId: '2020-0002', firstName: 'Me', lastName: 'Self' });
    const lib = account({ universityId: '2020-0009', firstName: 'Lee', lastName: 'Brarian', userRole: 'LIBRARIAN' });
    directory([account(), own, lib]);
    const user = userEvent.setup();
    renderAt('/accounts', assistant);
    await screen.findByText('Alex Q. Rivera');

    await user.click(screen.getByRole('button', { name: /Details for Alex/ }));
    const panel = await screen.findByRole('complementary', { name: /Alex/ });
    for (const n of ACTIONS) expect(within(panel).getByRole('button', { name: n })).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /Details for Me/ }));
    const ownPanel = await screen.findByRole('complementary', { name: /Me Q. Self/ });
    for (const n of ACTIONS) expect(within(ownPanel).queryByRole('button', { name: n })).toBeNull();

    await user.click(screen.getByRole('button', { name: /Details for Lee/ }));
    const libPanel = await screen.findByRole('complementary', { name: /Lee/ });
    for (const n of ACTIONS) expect(within(libPanel).queryByRole('button', { name: n })).toBeNull();
  });

  it('debounces search into one request and returns to page one', async () => {
    const mock = directory();
    const user = userEvent.setup();
    renderAt('/accounts?page=2', staffUser);
    await screen.findByText('Alex Q. Rivera');
    await user.type(screen.getByLabelText('Search name, ID or email'), 'riv');
    await waitFor(() => expect(mock.mock.calls.some((c) => c[0].includes('q=riv'))).toBe(true));
    const searches = mock.mock.calls.filter((c) => c[0].includes('q='));
    expect(searches).toHaveLength(1);
    expect(searches[0]![0]).toContain('page=0');
  });

  it('closes the detail panel when a filter changes', async () => {
    directory();
    const user = userEvent.setup();
    renderAt('/accounts', staffUser);
    await user.click(await screen.findByRole('button', { name: /Details for Alex/ }));
    expect(await screen.findByRole('complementary', { name: /Alex/ })).toBeInTheDocument();
    await user.selectOptions(screen.getByLabelText('Role'), 'STUDENT');
    await waitFor(() => expect(screen.queryByRole('complementary')).toBeNull());
  });

  it('shows an empty state with Clear filters', async () => {
    routeFetch({ '/app/users?': () => ok(pageOf([])) });
    renderAt('/accounts?q=zzz', staffUser);
    expect(await screen.findByText('No accounts match these filters.')).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: 'Clear filters' }).length).toBeGreaterThan(0);
  });

  it('offers a retry on read failure', async () => {
    routeFetch({ '/app/users?': () => fail(500) });
    renderAt('/accounts', staffUser);
    expect(await screen.findByRole('button', { name: 'Try again' })).toBeInTheDocument();
  });

  it('renders a permission message when the server denies the listing', async () => {
    routeFetch({ '/app/users?': () => fail(403) });
    renderAt('/accounts', assistant);
    expect(await screen.findByText('You do not have permission to view these accounts.')).toBeInTheDocument();
  });
});

describe('account actions', () => {
  it('sends only the changed profile fields when editing', async () => {
    const mock = directory(undefined, { '/app/users/2025-4321': () => ok(account({ lastName: 'Rivers' })) });
    const user = userEvent.setup();
    renderAt('/accounts', assistant);
    await user.click(await screen.findByRole('button', { name: /Details for Alex/ }));
    await user.click(await screen.findByRole('button', { name: 'Edit profile' }));
    const last = screen.getByLabelText('Last name');
    await user.clear(last);
    await user.type(last, 'Rivers');
    await user.click(screen.getByRole('button', { name: 'Save profile' }));
    await waitFor(() => expect(mock.mock.calls.some((c) => c[1]?.method === 'PATCH')).toBe(true));
    const patch = mock.mock.calls.find((c) => c[1]?.method === 'PATCH')!;
    expect(JSON.parse(String(patch[1]!.body))).toEqual({ lastName: 'Rivers' });
  });

  it('resets with a generated password that only ever appears in the reset request body', async () => {
    const mock = directory(undefined, { '/app/users/2025-4321/password/reset': () => ok(null) });
    const user = userEvent.setup();
    const { client } = renderAt('/accounts', assistant);
    await user.click(await screen.findByRole('button', { name: /Details for Alex/ }));
    await user.click(await screen.findByRole('button', { name: 'Reset password' }));
    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: 'Generate' }));
    const generated = dialog.querySelector('code')!.textContent!;
    expect(isPasswordValid(generated)).toBe(true);
    expect(within(dialog).getByText(/shown once and not stored/)).toBeInTheDocument();
    await user.click(within(dialog).getByRole('button', { name: 'Reset password' }));

    expect(await screen.findByText(/Temporary password set for Alex Q. Rivera/)).toBeInTheDocument();
    // The password is in exactly one place: the body of the reset request.
    const withSecret = mock.mock.calls.filter((c) => c[0].includes(generated) || String(c[1]?.body ?? '').includes(generated));
    expect(withSecret).toHaveLength(1);
    expect(withSecret[0]![0]).toBe('/app/users/2025-4321/password/reset');
    expect(withSecret[0]![1]!.method).toBe('PUT');
    expect(JSON.stringify(Object.entries(localStorage)) + JSON.stringify(Object.entries(sessionStorage))).not.toContain(generated);
    expect(JSON.stringify(client.getMutationCache().getAll().map((m) => m.state.variables))).not.toContain(generated);
    expect(JSON.stringify(client.getQueryCache().getAll().map((q) => q.state.data))).not.toContain(generated);
    expect(document.body.textContent).not.toContain(generated);
    expect(window.location.href).not.toContain(generated);
  });

  it('rejects a weak typed password before sending', async () => {
    const mock = directory();
    const user = userEvent.setup();
    renderAt('/accounts', assistant);
    await user.click(await screen.findByRole('button', { name: /Details for Alex/ }));
    await user.click(await screen.findByRole('button', { name: 'Reset password' }));
    const dialog = await screen.findByRole('dialog');
    await user.type(within(dialog).getByLabelText('Temporary password'), 'weak');
    await user.click(within(dialog).getByRole('button', { name: 'Reset password' }));
    expect(await within(dialog).findByText(/does not meet every requirement/)).toBeInTheDocument();
    expect(mock.mock.calls.some((c) => c[1]?.method === 'PUT')).toBe(false);
  });

  it('explains that an account with loan history must be kept', async () => {
    directory(undefined, { '/app/users/2025-4321': () => fail(409, { code: 'RESOURCE_HAS_HISTORY' }) });
    const user = userEvent.setup();
    renderAt('/accounts', assistant);
    await user.click(await screen.findByRole('button', { name: /Details for Alex/ }));
    await user.click(await screen.findByRole('button', { name: 'Delete account' }));
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByRole('heading', { name: 'Delete Alex Q. Rivera?' })).toBeInTheDocument();
    await user.click(within(dialog).getByRole('button', { name: 'Delete account' }));
    expect(await screen.findByText('This account has loan history and must be kept.')).toBeInTheDocument();
  });

  it('refreshes /me and the directory when a previously allowed action returns 403', async () => {
    const mock = directory(undefined, {
      '/app/users/2025-4321/password/reset': () => fail(403),
      '/app/users/me': () => ok(assistant),
    });
    const user = userEvent.setup();
    renderAt('/accounts', assistant);
    await user.click(await screen.findByRole('button', { name: /Details for Alex/ }));
    await user.click(await screen.findByRole('button', { name: 'Reset password' }));
    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: 'Generate' }));
    await user.click(within(dialog).getByRole('button', { name: 'Reset password' }));
    expect(await screen.findByText('You no longer have permission for that action.')).toBeInTheDocument();
    await waitFor(() => expect(mock.mock.calls.some((c) => c[0].startsWith('/app/users/me'))).toBe(true));
  });
});

describe('creating accounts (librarian)', () => {
  const fill = async (user: ReturnType<typeof userEvent.setup>, dialog: HTMLElement) => {
    await user.type(within(dialog).getByLabelText('University ID'), '2026-0007');
    await user.type(within(dialog).getByLabelText('First name'), 'Ana');
    await user.type(within(dialog).getByLabelText('Last name'), 'Reyes');
    await user.type(within(dialog).getByLabelText('Email'), 'ana@x.edu');
    await user.click(within(dialog).getByRole('button', { name: 'Generate' }));
  };
  const created = (role: string) => account({ universityId: '2026-0007', firstName: 'Ana', lastName: 'Reyes', userRole: role });

  it('creates a faculty account without course or major', async () => {
    const mock = directory(undefined, { '/app/users/faculty': () => ok(created('FACULTY')) });
    const user = userEvent.setup();
    renderAt('/accounts', staffUser);
    await user.click(await screen.findByRole('button', { name: /New faculty/ }));
    const dialog = await screen.findByRole('dialog');
    await fill(user, dialog);
    await user.click(within(dialog).getByRole('button', { name: 'Create account' }));
    expect(await screen.findByText(/Created the account for Ana Q\. Reyes/)).toBeInTheDocument();
    const post = mock.mock.calls.find((c) => c[0] === '/app/users/faculty')!;
    const body = JSON.parse(String(post[1]!.body));
    expect(Object.keys(body).sort()).toEqual(['email', 'firstName', 'lastName', 'password', 'universityId']);
    expect(isPasswordValid(body.password)).toBe(true);
  });

  it('uses the administrators endpoint for a library assistant', async () => {
    const mock = directory(undefined, { '/app/users/administrators': () => ok(created('ADMINISTRATOR')) });
    const user = userEvent.setup();
    renderAt('/accounts', staffUser);
    await user.click(await screen.findByRole('button', { name: /New faculty/ }));
    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByLabelText('Library assistant'));
    await fill(user, dialog);
    await user.click(within(dialog).getByRole('button', { name: 'Create account' }));
    await waitFor(() => expect(mock.mock.calls.some((c) => c[0] === '/app/users/administrators')).toBe(true));
  });

  it('maps a duplicate email onto the field and focuses it', async () => {
    directory(undefined, { '/app/users/faculty': () => fail(400, { fieldErrors: { email: 'Email is already registered' } }) });
    const user = userEvent.setup();
    renderAt('/accounts', staffUser);
    await user.click(await screen.findByRole('button', { name: /New faculty/ }));
    const dialog = await screen.findByRole('dialog');
    await fill(user, dialog);
    await user.click(within(dialog).getByRole('button', { name: 'Create account' }));
    expect(await within(dialog).findByText('Email is already registered')).toBeInTheDocument();
    await waitFor(() => expect(within(dialog).getByLabelText('Email')).toHaveFocus());
  });

  it('validates locally and focuses the first invalid field', async () => {
    directory();
    const user = userEvent.setup();
    renderAt('/accounts', staffUser);
    await user.click(await screen.findByRole('button', { name: /New faculty/ }));
    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: 'Create account' }));
    expect(await within(dialog).findByText('Use the format 2025-4321.')).toBeInTheDocument();
    await waitFor(() => expect(within(dialog).getByLabelText('University ID')).toHaveFocus());
  });
});

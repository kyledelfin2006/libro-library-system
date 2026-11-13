import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, vi } from 'vitest';
import { fail, ok, patron, renderAt, routeFetch, staffUser } from '../test/render';

afterEach(() => vi.unstubAllGlobals());

describe('my account', () => {
  it('shows the profile read-only first, with the role label', async () => {
    renderAt('/account', staffUser);
    expect(screen.getByText('Librarian')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Edit profile' })).toBeInTheDocument();
    expect(screen.queryByLabelText('First name')).toBeNull();
  });

  it('sends only the changed profile fields and refreshes the profile', async () => {
    const mock = routeFetch({
      '/app/users/2025-4321': (_u, init) =>
        init?.method === 'PATCH' ? ok({ ...patron, email: 'new@x.edu' }) : ok(patron),
      '/app/users/me': () => ok({ ...patron, email: 'new@x.edu' }),
    });
    const user = userEvent.setup();
    renderAt('/account');
    await user.click(screen.getByRole('button', { name: 'Edit profile' }));
    const email = screen.getByLabelText('Email');
    await user.clear(email);
    await user.type(email, 'new@x.edu');
    await user.click(screen.getByRole('button', { name: 'Save profile' }));
    expect(await screen.findByText('Profile saved.')).toBeInTheDocument();
    const patch = mock.mock.calls.find((c) => c[1]?.method === 'PATCH')!;
    expect(JSON.parse(patch[1]!.body as string)).toEqual({ email: 'new@x.edu' });
    expect(await screen.findByText('new@x.edu')).toBeInTheDocument();
  });

  it('maps server field errors onto fields and focuses the first one', async () => {
    routeFetch({
      '/app/users/2025-4321': () => fail(400, { fieldErrors: { email: 'Email already in use' } }),
    });
    const user = userEvent.setup();
    renderAt('/account');
    await user.click(screen.getByRole('button', { name: 'Edit profile' }));
    await user.clear(screen.getByLabelText('First name'));
    await user.type(screen.getByLabelText('First name'), 'Alexa');
    await user.click(screen.getByRole('button', { name: 'Save profile' }));
    expect(await screen.findByText('Email already in use')).toBeInTheDocument();
    await waitFor(() => expect(screen.getByLabelText('Email')).toHaveFocus());
  });

  it('validates the new password against the policy before calling the API', async () => {
    const mock = routeFetch({});
    const user = userEvent.setup();
    renderAt('/account');
    await user.type(screen.getByLabelText('Current password'), 'Old#Pass123');
    await user.type(screen.getByLabelText('New password'), 'weak');
    await user.click(screen.getByRole('button', { name: 'Change password' }));
    expect(screen.getByText(/does not meet every requirement/)).toBeInTheDocument();
    expect(mock).not.toHaveBeenCalled();
  });

  it('changes the password and clears the fields', async () => {
    routeFetch({
      '/app/users/2025-4321/password': () => ok(null),
      '/app/users/me': () => ok(patron),
    });
    const user = userEvent.setup();
    renderAt('/account');
    await user.type(screen.getByLabelText('Current password'), 'Old#Pass123');
    await user.type(screen.getByLabelText('New password'), 'New#Pass456');
    await user.click(screen.getByRole('button', { name: 'Change password' }));
    expect(await screen.findByText('Password changed.')).toBeInTheDocument();
    expect(screen.getByLabelText('New password')).toHaveValue('');
  });

  it('shows the danger zone to students only, and staff get no delete control', () => {
    const { unmount } = renderAt('/account');
    expect(screen.getByRole('button', { name: 'Delete my account' })).toBeInTheDocument();
    unmount();
    renderAt('/account', staffUser);
    expect(screen.queryByRole('button', { name: 'Delete my account' })).toBeNull();
  });

  it('requires typing the university ID before delete is enabled, then signs out', async () => {
    const mock = routeFetch({ '/app/users/2025-4321': () => ok(null) });
    const user = userEvent.setup();
    renderAt('/account');
    await user.click(screen.getByRole('button', { name: 'Delete my account' }));
    const dialog = await screen.findByRole('dialog');
    const confirm = within(dialog).getByRole('button', { name: 'Delete account' });
    expect(confirm).toBeDisabled();
    await user.type(within(dialog).getByLabelText('University ID'), '2025-4321');
    expect(confirm).toBeEnabled();
    await user.click(confirm);
    expect(await screen.findByRole('heading', { name: 'Sign in to the library' })).toBeInTheDocument();
    expect(mock.mock.calls.some((c) => c[1]?.method === 'DELETE')).toBe(true);
  });

  it('explains that an account with loan history must be kept', async () => {
    routeFetch({ '/app/users/2025-4321': () => fail(409, { code: 'RESOURCE_HAS_HISTORY' }) });
    const user = userEvent.setup();
    renderAt('/account');
    await user.click(screen.getByRole('button', { name: 'Delete my account' }));
    const dialog = await screen.findByRole('dialog');
    await user.type(within(dialog).getByLabelText('University ID'), '2025-4321');
    await user.click(within(dialog).getByRole('button', { name: 'Delete account' }));
    expect(await within(dialog).findByText(/loan history and must be kept/)).toBeInTheDocument();
  });
});

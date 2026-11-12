import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, vi } from 'vitest';
import { App } from '../App';
import { setAccessToken } from '../api/client';
import { AuthProvider } from '../auth/AuthProvider';
import { ToastProvider } from '../components/Toast';
import { ThemeProvider } from '../theme/ThemeProvider';

function json(status: number, body: unknown, headers: Record<string, string> = {}) {
  return new Response(JSON.stringify(body), { status, headers });
}

function renderApp(path = '/signin') {
  return render(
    <ThemeProvider>
      <MemoryRouter initialEntries={[path]}>
        <ToastProvider>
          <AuthProvider>
            <App />
          </AuthProvider>
        </ToastProvider>
      </MemoryRouter>
    </ThemeProvider>,
  );
}

const me = (over: Record<string, unknown> = {}) => ({
  universityId: '2025-4321', firstName: 'Alex', lastName: 'Rivera', middleInitial: null, email: 'a@x.edu',
  userRole: 'STUDENT', userCourse: 'IT', major: 'SE', mustChangePassword: false, ...over,
});

afterEach(() => {
  vi.restoreAllMocks();
  setAccessToken(null);
});

describe('sign in', () => {
  it('validates the University ID format and focuses the first invalid field', async () => {
    const user = userEvent.setup();
    renderApp();
    await user.type(screen.getByLabelText('University ID'), '123');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(await screen.findByText(/like 2025-4321/)).toBeInTheDocument();
    await waitFor(() => expect(screen.getByLabelText('University ID')).toHaveFocus());
  });

  it('toggles password visibility from the keyboard', async () => {
    const user = userEvent.setup();
    renderApp();
    const field = screen.getByLabelText('Password');
    expect(field).toHaveAttribute('type', 'password');
    await user.tab(); // ID
    await user.tab(); // password
    await user.tab(); // toggle
    await user.keyboard('{Enter}');
    expect(field).toHaveAttribute('type', 'text');
  });

  it('shows a wrong-password message without exposing details', async () => {
    const user = userEvent.setup();
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(json(401, { error: 'Unauthorized', details: 'Bad credentials' })));
    renderApp();
    await user.type(screen.getByLabelText('University ID'), '2025-4321');
    await user.type(screen.getByLabelText('Password'), 'nope');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('not correct');
  });

  it('shows a 429 countdown and disables the button', async () => {
    const user = userEvent.setup();
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(json(429, { error: 'Too Many Requests', details: 'x' }, { 'Retry-After': '42' })));
    renderApp();
    await user.type(screen.getByLabelText('University ID'), '2025-4321');
    await user.type(screen.getByLabelText('Password'), 'whatever');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(await screen.findByText(/try again in 42 seconds/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Sign in' })).toBeDisabled();
  });

  it('loads the profile after login and lands on the role home', async () => {
    const user = userEvent.setup();
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(json(200, { success: true, data: { accessToken: 't', tokenType: 'Bearer', expiresIn: 900, mustChangePassword: false } }))
      .mockResolvedValueOnce(json(200, { success: true, data: me() }));
    vi.stubGlobal('fetch', fetchMock);
    renderApp();
    await user.type(screen.getByLabelText('University ID'), '2025-4321');
    await user.type(screen.getByLabelText('Password'), 'Library#2026');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(await screen.findByRole('heading', { name: 'Catalog' })).toBeInTheDocument();
    expect((fetchMock.mock.calls[1] as [string])[0]).toBe('/app/users/me');
  });

  it('keeps a user with a reset password on the change-password screen', async () => {
    const user = userEvent.setup();
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValueOnce(json(200, { success: true, data: { accessToken: 't', tokenType: 'Bearer', expiresIn: 900, mustChangePassword: true } }))
        .mockResolvedValueOnce(json(200, { success: true, data: me({ mustChangePassword: true }) })),
    );
    renderApp();
    await user.type(screen.getByLabelText('University ID'), '2025-4321');
    await user.type(screen.getByLabelText('Password'), 'Temp#1234');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(await screen.findByText(/reset by library staff/)).toBeInTheDocument();
  });

  it('redirects protected routes to sign-in when signed out', async () => {
    renderApp('/catalog');
    expect(await screen.findByRole('heading', { name: 'Sign in to the library' })).toBeInTheDocument();
  });
});

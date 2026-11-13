import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { vi } from 'vitest';
import { App } from '../App';
import type { UserResponse } from '../api/types';
import { AuthProvider } from '../auth/AuthProvider';
import { ToastProvider } from '../components/Toast';
import { ThemeProvider } from '../theme/ThemeProvider';

export const patron: UserResponse = {
  universityId: '2025-4321', firstName: 'Alex', lastName: 'Rivera', email: 'a@x.edu', userRole: 'STUDENT',
  userCourse: 'IT', major: 'SE', mustChangePassword: false,
};
export const staffUser: UserResponse = { ...patron, universityId: '2020-0001', userRole: 'LIBRARIAN' };

/** Renders the real App at a path with a signed-in user, a fresh query client, and no automatic retries. */
export function renderAt(path: string, user: UserResponse | null = patron) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  const result = render(
    <ThemeProvider>
      <QueryClientProvider client={client}>
        <MemoryRouter initialEntries={[path]}>
          <ToastProvider>
            <AuthProvider initialUser={user}>
              <App />
            </AuthProvider>
          </ToastProvider>
        </MemoryRouter>
      </QueryClientProvider>
    </ThemeProvider>,
  );
  return { ...result, client };
}

export const ok = (data: unknown) => new Response(JSON.stringify({ success: true, message: 'Success', data, timestamp: 1 }));
export const fail = (status: number, body: Record<string, unknown> = {}) =>
  new Response(JSON.stringify({ error: 'Error', details: 'x', statusCode: status, fieldErrors: {}, ...body }), { status });

export const book = (over: Record<string, unknown> = {}) => ({
  id: 1, title: 'Dune', author: 'Frank Herbert', genre: 'Sci-fi', description: 'A desert planet.', replacementCost: 24.5,
  copyStatus: 'IN_COLLECTION', available: true, ...over,
});
export const pageOf = (content: unknown[], over: Record<string, unknown> = {}) => ({
  content, number: 0, size: 12, totalElements: content.length, totalPages: 1, ...over,
});

/** Installs a fetch mock that answers by URL prefix; an unknown route rejects so the test fails loudly. */
export function routeFetch(routes: Record<string, (url: string, init?: RequestInit) => Response>) {
  const mock = vi.fn((url: string, init?: RequestInit) => {
    const key = Object.keys(routes).find((k) => url.startsWith(k));
    if (!key) return Promise.reject(new Error(`Unmocked request: ${url}`));
    return Promise.resolve(routes[key]!(url, init));
  });
  vi.stubGlobal('fetch', mock);
  return mock;
}

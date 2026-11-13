import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import { seriousViolations } from '../test/axe';
import { book, ok, pageOf, renderAt, routeFetch, staffUser } from '../test/render';

function stubCatalog() {
  routeFetch({
    '/app/books/query': () => ok(pageOf([book(), book({ id: 2, title: 'Emma', available: false })])),
    '/app/books/genre': () => ok({ 'Sci-fi': 1 }),
    '/app/books/1': () => ok(book()),
    '/app/loans/me': () => ok(pageOf([{ loanId: 5, bookId: 9, bookTitle: 'Emma', borrowedAt: '2026-11-01T10:00:00', dueAt: '2099-01-15T10:00:00', status: 'ACTIVE', overdue: false }])),
  });
}

describe.each(['dark', 'light'])('axe (%s theme)', (theme) => {
  afterEach(() => vi.unstubAllGlobals());

  it.each(['/signin', '/register'])('%s has no serious or critical violations', async (path) => {
    document.documentElement.dataset.theme = theme;
    const { container } = renderAt(path, null);
    expect(await seriousViolations(container)).toEqual([]);
  });

  it.each(['/catalog', '/catalog/1', '/loans', '/account'])('%s has no serious or critical violations', async (path) => {
    document.documentElement.dataset.theme = theme;
    stubCatalog();
    const { container, findAllByText } = renderAt(path);
    await findAllByText(/Dune|Emma|My account/);
    expect(await seriousViolations(container)).toEqual([]);
  });

  it('/accounts has no serious or critical violations, with the panel and dialogs open', async () => {
    document.documentElement.dataset.theme = theme;
    routeFetch({
      '/app/users?': () => ok(pageOf([{
        universityId: '2025-4321', firstName: 'Alex', lastName: 'Rivera', email: 'a@x.edu', userRole: 'STUDENT',
        userCourse: 'IT', major: 'SE', mustChangePassword: false,
      }])),
    });
    const user = userEvent.setup();
    const { container, findByRole, findByText } = renderAt('/accounts', staffUser);
    await findByText('Alex Rivera');
    expect(await seriousViolations(container)).toEqual([]);
    await user.click(await findByRole('button', { name: /Details for Alex/ }));
    await findByRole('complementary', { name: /Alex/ });
    expect(await seriousViolations(container)).toEqual([]);
    await user.click(await findByRole('button', { name: 'Reset password' }));
    await findByRole('dialog');
    expect(await seriousViolations(container)).toEqual([]);
  });

  it('/accounts new-account dialog has no serious or critical violations', async () => {
    document.documentElement.dataset.theme = theme;
    routeFetch({ '/app/users?': () => ok(pageOf([])) });
    const user = userEvent.setup();
    const { container, findByRole } = renderAt('/accounts', staffUser);
    await user.click(await findByRole('button', { name: /New faculty/ }));
    await findByRole('dialog');
    expect(await seriousViolations(container)).toEqual([]);
  });
});

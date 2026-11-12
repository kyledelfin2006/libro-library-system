import { vi } from 'vitest';
import { seriousViolations } from '../test/axe';
import { book, ok, pageOf, renderAt, routeFetch } from '../test/render';

function stubCatalog() {
  routeFetch({
    '/app/books/query': () => ok(pageOf([book(), book({ id: 2, title: 'Emma', available: false })])),
    '/app/books/genre': () => ok({ 'Sci-fi': 1 }),
    '/app/books/1': () => ok(book()),
    '/app/loans/me': () => ok(pageOf([])),
  });
}

describe.each(['dark', 'light'])('axe (%s theme)', (theme) => {
  afterEach(() => vi.unstubAllGlobals());

  it.each(['/signin', '/register'])('%s has no serious or critical violations', async (path) => {
    document.documentElement.dataset.theme = theme;
    const { container } = renderAt(path, null);
    expect(await seriousViolations(container)).toEqual([]);
  });

  it.each(['/catalog', '/catalog/1'])('%s has no serious or critical violations', async (path) => {
    document.documentElement.dataset.theme = theme;
    stubCatalog();
    const { container, findAllByText } = renderAt(path);
    await findAllByText(/Dune/);
    expect(await seriousViolations(container)).toEqual([]);
  });
});

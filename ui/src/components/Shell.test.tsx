import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, vi } from 'vitest';
import { ok, pageOf, renderAt, routeFetch, staffUser } from '../test/render';

afterEach(() => vi.unstubAllGlobals());

describe('mobile menu', () => {
  const routes = () => routeFetch({ '/app/books/query': () => ok(pageOf([])), '/app/books/genre': () => ok({}), '/app/loans': () => ok(pageOf([])) });

  it('is a labelled disclosure button that opens and closes the primary nav', async () => {
    routes();
    const user = userEvent.setup();
    renderAt('/catalog', staffUser);
    const button = await screen.findByRole('button', { name: 'Menu' });
    expect(button).toHaveAttribute('aria-controls', 'primary-nav');
    expect(button).toHaveAttribute('aria-expanded', 'false');
    await user.click(button);
    expect(button).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByRole('navigation', { name: 'Primary' })).toHaveClass('nav-open');
    await user.click(button);
    expect(button).toHaveAttribute('aria-expanded', 'false');
  });

  it('closes on Escape and returns focus to the button', async () => {
    routes();
    const user = userEvent.setup();
    renderAt('/catalog', staffUser);
    const button = await screen.findByRole('button', { name: 'Menu' });
    await user.click(button);
    await user.keyboard('{Escape}');
    expect(button).toHaveAttribute('aria-expanded', 'false');
    expect(button).toHaveFocus();
  });

  it('closes after choosing a destination', async () => {
    routes();
    const user = userEvent.setup();
    renderAt('/catalog', staffUser);
    const button = await screen.findByRole('button', { name: 'Menu' });
    await user.click(button);
    await user.click(screen.getByRole('link', { name: 'Loans' }));
    expect(await screen.findByRole('heading', { name: 'Loans' })).toBeInTheDocument();
    expect(button).toHaveAttribute('aria-expanded', 'false');
  });
});

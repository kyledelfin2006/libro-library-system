import { render } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { App } from '../App';
import { AuthProvider } from '../auth/AuthProvider';
import { ToastProvider } from '../components/Toast';
import { seriousViolations } from '../test/axe';
import { ThemeProvider } from '../theme/ThemeProvider';

describe.each(['dark', 'light'])('axe on public pages (%s theme)', (theme) => {
  it.each(['/signin', '/register'])('%s has no serious or critical violations', async (path) => {
    document.documentElement.dataset.theme = theme;
    const { container } = render(
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
    expect(await seriousViolations(container)).toEqual([]);
  });
});

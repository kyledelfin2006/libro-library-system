import { NavLink, Outlet } from 'react-router-dom';
import { useAuth } from '../auth/AuthProvider';
import { can, homePath } from '../auth/permissions';
import { useTheme, type ThemeChoice } from '../theme/ThemeProvider';

/** Authenticated app shell: role-aware navigation, theme switch, sign out, and the Manila-time footer note. */
export function Shell() {
  const { user, signOut } = useAuth();
  const { choice, setChoice } = useTheme();
  if (!user) return null;
  const staff = can(user.userRole, 'staffDashboard');

  return (
    <div className="shell">
      <a className="skip-link" href="#main">
        Skip to content
      </a>
      <header className="shell-header">
        <NavLink to={homePath(user.userRole)} className="brand" aria-label="Libro home">
          <img src="/brand/libro-96.png" srcSet="/brand/libro-48.png 1x, /brand/libro-96.png 2x" alt="Libro" height={44} width={47} />
        </NavLink>
        <nav aria-label="Primary">
          {staff && <NavLink to="/dashboard">Dashboard</NavLink>}
          <NavLink to="/catalog">Catalog</NavLink>
          {staff && <NavLink to="/staff/loans">Loans</NavLink>}
          {staff && <NavLink to="/inventory">Inventory</NavLink>}
          {can(user.userRole, 'listAccounts') && <NavLink to="/accounts">Accounts</NavLink>}
          {can(user.userRole, 'audit') && <NavLink to="/audit">Audit</NavLink>}
          {can(user.userRole, 'buildInfo') && <NavLink to="/version">Version</NavLink>}
          <NavLink to="/loans">My Loans</NavLink>
          <NavLink to="/account">My Account</NavLink>
        </nav>
        <div className="shell-tools">
          <label className="visually-hidden" htmlFor="theme-select">
            Theme
          </label>
          <select id="theme-select" value={choice} onChange={(e) => setChoice(e.target.value as ThemeChoice)}>
            <option value="dark">Dark</option>
            <option value="light">Light</option>
            <option value="system">System</option>
          </select>
          <span className="who">{user.firstName}</span>
          <button type="button" className="btn btn-secondary" onClick={signOut}>
            Sign out
          </button>
        </div>
      </header>
      <main id="main" tabIndex={-1} className="shell-main">
        <Outlet />
      </main>
      <footer className="shell-footer">
        Libro · Times are shown in Manila time · <NavLink to="/status">Status</NavLink>
      </footer>
    </div>
  );
}

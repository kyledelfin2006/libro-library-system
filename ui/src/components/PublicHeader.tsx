import { Link } from 'react-router-dom';
import { useAuth } from '../auth/AuthProvider';
import { homePath } from '../auth/permissions';

/** Minimal header for pages anyone can open (Status): brand plus one link back into the app or to sign-in. */
export function PublicHeader() {
  const { user } = useAuth();
  return (
    <header className="shell-header">
      <Link to={user ? homePath(user.userRole) : '/signin'} className="brand" aria-label="Libro home">
        <img src="/brand/libro-96.png" srcSet="/brand/libro-48.png 1x, /brand/libro-96.png 2x" alt="Libro" height={44} width={47} />
      </Link>
      <nav aria-label="Primary">
        {user ? <Link to={homePath(user.userRole)}>Back to the library</Link> : <Link to="/signin">Sign in</Link>}
      </nav>
    </header>
  );
}

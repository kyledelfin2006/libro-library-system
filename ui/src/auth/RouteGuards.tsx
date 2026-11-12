import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from './AuthProvider';
import { can, homePath, type Capability } from './permissions';

/**
 * Requires a signed-in user. Unauthenticated visitors go to sign-in with the intended destination preserved; a
 * user who must change a reset password is kept on the change-password screen (the server does not block routes).
 */
export function RequireAuth({ capability }: { capability?: Capability }) {
  const { user } = useAuth();
  const location = useLocation();

  if (!user) return <Navigate to="/signin" replace state={{ from: location.pathname + location.search }} />;
  if (user.mustChangePassword && location.pathname !== '/change-password') {
    return <Navigate to="/change-password" replace />;
  }
  if (capability && !can(user.userRole, capability)) return <Navigate to={homePath(user.userRole)} replace />;
  return <Outlet />;
}

/** Public pages (sign in, register) send signed-in users to their home. */
export function PublicOnly() {
  const { user } = useAuth();
  if (user) return <Navigate to={user.mustChangePassword ? '/change-password' : homePath(user.userRole)} replace />;
  return <Outlet />;
}

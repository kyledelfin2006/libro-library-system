import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { fetchMe, login as loginRequest } from '../api/auth';
import { ApiError, setAccessToken, setUnauthorizedHandler } from '../api/client';
import type { UserResponse } from '../api/types';

interface AuthContextValue {
  user: UserResponse | null;
  /** Set when a 401 ended the session, so sign-in can say so. Cleared by the next successful sign-in. */
  sessionExpired: boolean;
  signIn: (universityId: string, password: string) => Promise<UserResponse>;
  signOut: () => void;
  /** Re-reads /me; a 404 means the account was deleted, so the session ends. */
  refreshUser: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

/**
 * Holds the signed-in user in memory only. The profile (and its role) comes from GET /app/users/me, never from the
 * token. A reload means signing in again by design (no refresh tokens).
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserResponse | null>(null);
  const [sessionExpired, setSessionExpired] = useState(false);

  const clear = useCallback(() => {
    setAccessToken(null);
    setUser(null);
  }, []);

  useEffect(() => {
    setUnauthorizedHandler(() => {
      clear();
      setSessionExpired(true);
    });
    return () => setUnauthorizedHandler(null);
  }, [clear]);

  const signIn = useCallback(
    async (universityId: string, password: string) => {
      const token = await loginRequest(universityId, password);
      setAccessToken(token.accessToken);
      try {
        const me = await fetchMe();
        setUser(me);
        setSessionExpired(false);
        return me;
      } catch (e) {
        clear();
        throw e;
      }
    },
    [clear],
  );

  const refreshUser = useCallback(async () => {
    try {
      setUser(await fetchMe());
    } catch (e) {
      if (e instanceof ApiError && e.status === 404) clear();
      else throw e;
    }
  }, [clear]);

  const value = useMemo<AuthContextValue>(
    () => ({ user, sessionExpired, signIn, signOut: clear, refreshUser }),
    [user, sessionExpired, signIn, clear, refreshUser],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider');
  return ctx;
}

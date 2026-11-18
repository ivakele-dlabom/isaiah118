import { createContext, useCallback, useContext, useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { toast } from "@/components/ui/toast.tsx";
import type { AuthUser } from "@/types/main.ts";

/** Dispatched on `window` when the backend rejects a token that was sent (HTTP 401). */
export const AUTH_EXPIRED_EVENT = "auth:expired";
/** The are aslo for localStorage*/
const TOKEN_KEY = "token";
const USER_KEY = "user";

// setTimeout keeps its delay in a signed 32-bit int. Anything larger fires immediately.
const MAX_TIMEOUT_MS = 2 ** 31 - 1;

interface Session {
  token: string;
  user: AuthUser;
}

interface AuthContextType {
  token: string | null;
  user: AuthUser | null;
  login: (token: string, user: AuthUser) => void;
  logout: () => void;
  /** True when a token is present and has not expired. Use this for every "is logged in" check. */
  isAuthenticated: boolean;
}

/** Returns the token's `exp` claim in milliseconds, or null if the token can't be decoded. */
function getExpiryMs(token: string): number | null {
  try {
    const payload = token.split(".")[1];
    if (!payload) return null;

    // JWTs use base64url. atob needs standard base64 with padding.
    const base64 = payload.replace(/-/g, "+").replace(/_/g, "/");
    const padded = base64.padEnd(Math.ceil(base64.length / 4) * 4, "=");
    const bytes = Uint8Array.from(atob(padded), (c) => c.charCodeAt(0));
    const { exp } = JSON.parse(new TextDecoder().decode(bytes));

    return typeof exp === "number" ? exp * 1000 : null;
  } catch {
    return null;
  }
}

/** A token we can't decode is treated as expired. */
function isExpired(token: string): boolean {
  const expiryMs = getExpiryMs(token);
  return expiryMs === null || expiryMs <= Date.now();
}

function clearStoredSession() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
}

/**
 * Loads the saved session. An expired, incomplete or corrupt session is
 * removed from localStorage and reported as "logged out".
 */
function readStoredSession(): Session | null {
  try {
    const token = localStorage.getItem(TOKEN_KEY);
    const storedUser = localStorage.getItem(USER_KEY);
    if (token && storedUser && !isExpired(token)) {
      return { token, user: JSON.parse(storedUser) as AuthUser };
    }
  } catch {
    // Corrupt storage: fall through and clear it.
  }
  clearStoredSession();
  return null;
}

const AuthContext = createContext<AuthContextType | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const navigate = useNavigate();
  const [session, setSession] = useState<Session | null>(readStoredSession);

  // Mirrors `session` synchronously so several 401s arriving in the same tick
  // produce one toast instead of one each.
  const sessionRef = useRef<Session | null>(session);

  const login = useCallback((token: string, user: AuthUser) => {
    const next = { token, user };
    sessionRef.current = next;
    setSession(next);
    localStorage.setItem(TOKEN_KEY, token);
    localStorage.setItem(USER_KEY, JSON.stringify(user));
  }, []);

  const logout = useCallback(() => {
    sessionRef.current = null;
    setSession(null);
    clearStoredSession();
  }, []);

  // Logout caused by an expired or rejected token, as opposed to the user clicking "Log out".
  const expireSession = useCallback(() => {
    if (!sessionRef.current) return;
    logout();
    toast.add({
      title: "Session expired",
      description: "Your session expired. Please log in again.",
      type: "info",
      actionProps: {
        children: "Log in",
        onClick: () => navigate("/login"),
      },
    });
  }, [logout, navigate]);

  // The API layer announces that the server rejected our token.
  useEffect(() => {
    window.addEventListener(AUTH_EXPIRED_EVENT, expireSession);
    return () => window.removeEventListener(AUTH_EXPIRED_EVENT, expireSession);
  }, [expireSession]);

  // Log out automatically when the token's lifetime runs out.
  const token = session?.token ?? null;
  useEffect(() => {
    if (!token) return;

    let timer: number | undefined;
    const schedule = () => {
      const expiryMs = getExpiryMs(token);
      const remaining = expiryMs === null ? 0 : expiryMs - Date.now();
      if (remaining <= 0) {
        expireSession();
        return;
      }
      // If the lifetime exceeds the timer limit, wake up early and re-check.
      timer = window.setTimeout(schedule, Math.min(remaining, MAX_TIMEOUT_MS));
    };

    schedule();
    return () => window.clearTimeout(timer);
  }, [token, expireSession]);

  return (
    <AuthContext.Provider
      value={{
        token,
        user: session?.user ?? null,
        login,
        logout,
        isAuthenticated: token !== null && !isExpired(token),
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return context;
}

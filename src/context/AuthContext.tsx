import { createContext, ReactNode, useCallback, useContext, useEffect, useMemo, useState } from "react";
import { AuthMe, getCurrentUser, loginUser, logoutUser, registerUser } from "@/lib/auth-api";

interface AuthContextValue {
  userEmail: string | null;
  credits: number;
  loading: boolean;
  isAuthenticated: boolean;
  register: (email: string, password: string) => Promise<AuthMe>;
  login: (email: string, password: string) => Promise<AuthMe>;
  logout: () => Promise<void>;
  refresh: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export const AuthProvider = ({ children }: { children: ReactNode }) => {
  const [userEmail, setUserEmail] = useState<string | null>(null);
  const [credits, setCredits] = useState<number>(0);
  const [loading, setLoading] = useState<boolean>(true);

  const refresh = useCallback(async () => {
    try {
      const me = await getCurrentUser();
      setUserEmail(me.email);
      setCredits(me.credits);
    } catch (error) {
      if ((error as Error).message === "AUTH_REQUIRED") {
        setUserEmail(null);
        setCredits(0);
      } else {
        throw error;
      }
    }
  }, []);

  useEffect(() => {
    refresh()
      .catch(() => {
        setUserEmail(null);
        setCredits(0);
      })
      .finally(() => {
        setLoading(false);
      });
  }, [refresh]);

  const register = useCallback(async (email: string, password: string) => {
    const next = await registerUser({ email, password });
    setUserEmail(next.email);
    setCredits(next.credits);
    return next;
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    const next = await loginUser({ email, password });
    setUserEmail(next.email);
    setCredits(next.credits);
    return next;
  }, []);

  const logout = useCallback(async () => {
    await logoutUser();
    setUserEmail(null);
    setCredits(0);
  }, []);

  const value = useMemo(
    () => ({
      userEmail,
      credits,
      loading,
      isAuthenticated: Boolean(userEmail),
      register,
      login,
      logout,
      refresh,
    }),
    [userEmail, credits, loading, register, login, logout, refresh],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

export const useAuth = (): AuthContextValue => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within AuthProvider");
  }
  return context;
};

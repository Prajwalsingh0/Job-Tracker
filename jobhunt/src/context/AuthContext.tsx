import { createContext, useCallback, useContext, useEffect, useState, ReactNode } from 'react';
import { api, clearToken, setToken } from '@/lib/api';
import { User, LoginCredentials, RegisterCredentials, AuthState } from '../types/auth';

interface AuthContextType extends AuthState {
    login: (credentials: LoginCredentials) => Promise<void>;
    register: (credentials: RegisterCredentials) => Promise<void>;
    logout: () => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
    const [user, setUser] = useState<User | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    // Restore the session on first load. The access token is gone after a reload (it is
    // kept in memory only), so /me is expected to 401 once - the API client silently
    // exchanges the httpOnly refresh cookie for a new token and retries.
    useEffect(() => {
        let cancelled = false;

        const restoreSession = async () => {
            try {
                const currentUser = await api.me();
                if (!cancelled) {
                    setUser(currentUser);
                }
            } catch {
                clearToken();
            } finally {
                if (!cancelled) {
                    setIsLoading(false);
                }
            }
        };

        void restoreSession();
        return () => {
            cancelled = true;
        };
    }, []);

    const login = useCallback(async ({ email, password }: LoginCredentials) => {
        setIsLoading(true);
        setError(null);
        try {
            const response = await api.login({ email, password });
            setToken(response.token);
            setUser(response.user);
        } catch (err) {
            setError(err instanceof Error ? err.message : 'Login failed');
            throw err;
        } finally {
            setIsLoading(false);
        }
    }, []);

    const register = useCallback(async ({ name, email, password }: RegisterCredentials) => {
        setIsLoading(true);
        setError(null);
        try {
            const response = await api.register({ name, email, password });
            setToken(response.token);
            setUser(response.user);
        } catch (err) {
            setError(err instanceof Error ? err.message : 'Registration failed');
            throw err;
        } finally {
            setIsLoading(false);
        }
    }, []);

    const logout = useCallback(() => {
        // Clear local state immediately, then tell the server to revoke the refresh token.
        clearToken();
        setUser(null);
        setError(null);
        void api.logout().catch(() => {
            // The session is already gone locally; a failed revoke must not block the UI.
        });
    }, []);

    return (
        <AuthContext.Provider value={{ user, isLoading, error, login, register, logout }}>
            {children}
        </AuthContext.Provider>
    );
}

export function useAuth() {
    const context = useContext(AuthContext);
    if (context === undefined) {
        throw new Error('useAuth must be used within an AuthProvider');
    }
    return context;
}

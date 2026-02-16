import React, { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import { User, LoginCredentials, RegisterCredentials, AuthState } from '../types/auth';

interface AuthContextType extends AuthState {
    login: (credentials: LoginCredentials) => Promise<void>;
    register: (credentials: RegisterCredentials) => Promise<void>;
    logout: () => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

const USERS_STORAGE_KEY = 'jobhunt_users';
const CURRENT_USER_STORAGE_KEY = 'jobhunt_currentUser';

export function AuthProvider({ children }: { children: ReactNode }) {
    const [user, setUser] = useState<User | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    // Auto-login on mount
    useEffect(() => {
        const storedUser = localStorage.getItem(CURRENT_USER_STORAGE_KEY);
        if (storedUser) {
            try {
                setUser(JSON.parse(storedUser));
            } catch (err) {
                console.error('Failed to parse stored user', err);
                localStorage.removeItem(CURRENT_USER_STORAGE_KEY);
            }
        }
        setIsLoading(false);
    }, []);

    const login = async ({ email, password }: LoginCredentials) => {
        setIsLoading(true);
        setError(null);

        // Simulate API delay
        await new Promise(resolve => setTimeout(resolve, 500));

        try {
            const usersJson = localStorage.getItem(USERS_STORAGE_KEY);
            const users: User[] = usersJson ? JSON.parse(usersJson) : [];

            const foundUser = users.find(u => u.email === email && u.password === password);

            if (foundUser) {
                const { password: _, ...userWithoutPassword } = foundUser;
                setUser(userWithoutPassword);
                localStorage.setItem(CURRENT_USER_STORAGE_KEY, JSON.stringify(userWithoutPassword));
            } else {
                throw new Error('Invalid email or password');
            }
        } catch (err) {
            setError(err instanceof Error ? err.message : 'Login failed');
            throw err;
        } finally {
            setIsLoading(false);
        }
    };

    const register = async ({ name, email, password }: RegisterCredentials) => {
        setIsLoading(true);
        setError(null);

        // Simulate API delay
        await new Promise(resolve => setTimeout(resolve, 500));

        try {
            const usersJson = localStorage.getItem(USERS_STORAGE_KEY);
            const users: User[] = usersJson ? JSON.parse(usersJson) : [];

            if (users.some(u => u.email === email)) {
                throw new Error('Email already exists');
            }

            const newUser: User = { name, email, password };
            const updatedUsers = [...users, newUser];

            localStorage.setItem(USERS_STORAGE_KEY, JSON.stringify(updatedUsers));

            // Auto-login after register
            const { password: _, ...userWithoutPassword } = newUser;
            setUser(userWithoutPassword);
            localStorage.setItem(CURRENT_USER_STORAGE_KEY, JSON.stringify(userWithoutPassword));
        } catch (err) {
            setError(err instanceof Error ? err.message : 'Registration failed');
            throw err;
        } finally {
            setIsLoading(false);
        }
    };

    const logout = () => {
        setUser(null);
        localStorage.removeItem(CURRENT_USER_STORAGE_KEY);
    };

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

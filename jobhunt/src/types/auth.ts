export interface User {
    name: string;
    email: string;
    password?: string; // Optional because we don't store it in session, only in DB
}

export interface LoginCredentials {
    email: string;
    password: string;
}

export interface RegisterCredentials {
    name: string;
    email: string;
    password: string;
}

export interface AuthState {
    user: User | null;
    isLoading: boolean;
    error: string | null;
}

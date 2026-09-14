export interface User {
    id: number;
    name: string;
    email: string;
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

/** Response body returned by /api/auth/register and /api/auth/login. */
export interface AuthResponse {
    token: string;
    tokenType: string;
    user: User;
}

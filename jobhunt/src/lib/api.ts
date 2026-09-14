import type { Job, JobPayload, JobStats, JobStatus, Resume } from '@/types';
import type { AuthResponse, LoginCredentials, RegisterCredentials, User } from '@/types/auth';

/**
 * Base URL of the Spring Boot API. Defaults to the local backend; override with the
 * VITE_API_BASE_URL environment variable (see .env.example).
 */
export const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080').replace(/\/+$/, '');

const TOKEN_STORAGE_KEY = 'jobhunt_token';

export function getToken(): string | null {
    return localStorage.getItem(TOKEN_STORAGE_KEY);
}

export function setToken(token: string): void {
    localStorage.setItem(TOKEN_STORAGE_KEY, token);
}

export function clearToken(): void {
    localStorage.removeItem(TOKEN_STORAGE_KEY);
}

interface ApiErrorPayload {
    status?: number;
    error?: string;
    message?: string;
    fieldErrors?: Record<string, string> | null;
}

/** Error thrown for any non-2xx API response (or an unreachable server). */
export class ApiError extends Error {
    readonly status: number;
    readonly fieldErrors: Record<string, string> | null;

    constructor(message: string, status: number, fieldErrors: Record<string, string> | null = null) {
        super(message);
        this.name = 'ApiError';
        this.status = status;
        this.fieldErrors = fieldErrors;
    }
}

async function toApiError(response: Response): Promise<ApiError> {
    let payload: ApiErrorPayload | null = null;
    try {
        payload = (await response.json()) as ApiErrorPayload;
    } catch {
        payload = null;
    }

    const firstFieldError = payload?.fieldErrors ? Object.values(payload.fieldErrors)[0] : undefined;
    const message =
        payload?.message ??
        firstFieldError ??
        `Request failed with status ${response.status}`;

    return new ApiError(message, response.status, payload?.fieldErrors ?? null);
}

interface RequestOptions {
    method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
    body?: unknown;
    formData?: FormData;
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
    const headers: Record<string, string> = {};
    const token = getToken();
    if (token) {
        headers.Authorization = `Bearer ${token}`;
    }

    let body: BodyInit | undefined;
    if (options.formData) {
        // Let the browser set the multipart boundary.
        body = options.formData;
    } else if (options.body !== undefined) {
        headers['Content-Type'] = 'application/json';
        body = JSON.stringify(options.body);
    }

    let response: Response;
    try {
        response = await fetch(`${API_BASE_URL}${path}`, {
            method: options.method ?? 'GET',
            headers,
            body,
        });
    } catch {
        throw new ApiError('Could not reach the API server. Is the backend running?', 0);
    }

    if (!response.ok) {
        // An expired/invalid token means the stored session is no longer usable.
        if (response.status === 401 && token) {
            clearToken();
        }
        throw await toApiError(response);
    }

    if (response.status === 204) {
        return undefined as T;
    }

    const text = await response.text();
    return (text ? JSON.parse(text) : undefined) as T;
}

function buildQuery(params: Record<string, string | undefined>): string {
    const search = new URLSearchParams();
    Object.entries(params).forEach(([key, value]) => {
        if (value) {
            search.append(key, value);
        }
    });
    const query = search.toString();
    return query ? `?${query}` : '';
}

function fileNameFromDisposition(disposition: string | null, fallback: string): string {
    if (!disposition) {
        return fallback;
    }
    const utf8Match = /filename\*=UTF-8''([^;]+)/i.exec(disposition);
    if (utf8Match?.[1]) {
        return decodeURIComponent(utf8Match[1]);
    }
    const plainMatch = /filename="?([^";]+)"?/i.exec(disposition);
    return plainMatch?.[1] ?? fallback;
}

/**
 * Downloads a resume with the Authorization header (a plain link/window.open cannot
 * send the bearer token) and returns it as a blob.
 */
export async function fetchResumeFile(resume: Resume): Promise<{ blob: Blob; fileName: string }> {
    const token = getToken();
    let response: Response;
    try {
        response = await fetch(`${API_BASE_URL}/api/resumes/${resume.id}/download`, {
            headers: token ? { Authorization: `Bearer ${token}` } : {},
        });
    } catch {
        throw new ApiError('Could not reach the API server. Is the backend running?', 0);
    }

    if (!response.ok) {
        throw await toApiError(response);
    }

    const blob = await response.blob();
    return {
        blob,
        fileName: fileNameFromDisposition(response.headers.get('Content-Disposition'), resume.fileName),
    };
}

export const api = {
    register: (credentials: RegisterCredentials) =>
        request<AuthResponse>('/api/auth/register', { method: 'POST', body: credentials }),

    login: (credentials: LoginCredentials) =>
        request<AuthResponse>('/api/auth/login', { method: 'POST', body: credentials }),

    me: () => request<User>('/api/auth/me'),

    listJobs: (filters: { search?: string; status?: JobStatus | 'all' } = {}) =>
        request<Job[]>(
            `/api/jobs${buildQuery({
                search: filters.search,
                status: filters.status && filters.status !== 'all' ? filters.status : undefined,
            })}`,
        ),

    jobStats: () => request<JobStats>('/api/jobs/stats'),

    createJob: (payload: JobPayload) => request<Job>('/api/jobs', { method: 'POST', body: payload }),

    updateJob: (id: number, payload: JobPayload) =>
        request<Job>(`/api/jobs/${id}`, { method: 'PUT', body: payload }),

    updateJobStatus: (id: number, status: JobStatus) =>
        request<Job>(`/api/jobs/${id}/status`, { method: 'PATCH', body: { status } }),

    deleteJob: (id: number) => request<void>(`/api/jobs/${id}`, { method: 'DELETE' }),

    listResumes: () => request<Resume[]>('/api/resumes'),

    uploadResume: (file: File, meta: { name?: string; versionTag?: string } = {}) => {
        const formData = new FormData();
        formData.append('file', file);
        if (meta.name) {
            formData.append('name', meta.name);
        }
        if (meta.versionTag) {
            formData.append('versionTag', meta.versionTag);
        }
        return request<Resume>('/api/resumes', { method: 'POST', formData });
    },

    deleteResume: (id: number) => request<void>(`/api/resumes/${id}`, { method: 'DELETE' }),
};

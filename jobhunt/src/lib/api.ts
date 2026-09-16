import type { Job, JobPayload, JobSortField, JobStats, JobStatus, JobStatusHistoryEntry, PageResponse, Resume } from '@/types';
import type { AuthResponse, LoginCredentials, RegisterCredentials, User } from '@/types/auth';

/**
 * Base URL of the Spring Boot API. Defaults to the local backend; override with the
 * VITE_API_BASE_URL environment variable (see .env.example).
 */
export const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080').replace(/\/+$/, '');

/**
 * The access token is held in memory only - never in localStorage or sessionStorage - so an
 * injected script cannot read it back from storage. Sessions survive a page reload because
 * the refresh token lives in an httpOnly cookie that JavaScript cannot see, and a 401 on
 * the first request transparently triggers a refresh.
 */
let accessToken: string | null = null;

export function getToken(): string | null {
    return accessToken;
}

export function setToken(token: string | null): void {
    accessToken = token;
}

export function clearToken(): void {
    accessToken = null;
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

    // Validation responses carry both a generic "Validation failed" message and the
    // per-field messages. The field message is the actionable one, so prefer it.
    const firstFieldError = payload?.fieldErrors ? Object.values(payload.fieldErrors)[0] : undefined;
    const message =
        firstFieldError ??
        payload?.message ??
        `Request failed with status ${response.status}`;

    return new ApiError(message, response.status, payload?.fieldErrors ?? null);
}

/** Endpoints whose 401 means "bad credentials", not "expired access token". */
const AUTH_PATHS = ['/api/auth/login', '/api/auth/register', '/api/auth/refresh', '/api/auth/logout'];

function isAuthPath(path: string): boolean {
    return AUTH_PATHS.some((authPath) => path.startsWith(authPath));
}

let refreshInFlight: Promise<boolean> | null = null;

/**
 * Exchanges the httpOnly refresh cookie for a new access token. Concurrent callers share a
 * single in-flight request so a burst of 401s does not rotate the token repeatedly.
 */
function attemptRefresh(): Promise<boolean> {
    if (refreshInFlight) {
        return refreshInFlight;
    }

    refreshInFlight = (async () => {
        try {
            const response = await fetch(`${API_BASE_URL}/api/auth/refresh`, {
                method: 'POST',
                credentials: 'include',
            });
            if (!response.ok) {
                accessToken = null;
                return false;
            }
            const body = (await response.json()) as AuthResponse;
            accessToken = body.token ?? null;
            return accessToken !== null;
        } catch {
            accessToken = null;
            return false;
        }
    })().finally(() => {
        refreshInFlight = null;
    });

    return refreshInFlight;
}

interface RequestOptions {
    method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
    body?: unknown;
    formData?: FormData;
}

async function request<T>(path: string, options: RequestOptions = {}, allowRetry = true): Promise<T> {
    const headers: Record<string, string> = {};
    if (accessToken) {
        headers.Authorization = `Bearer ${accessToken}`;
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
            // Required so the httpOnly refresh cookie is sent.
            credentials: 'include',
        });
    } catch {
        throw new ApiError('Could not reach the API server. Is the backend running?', 0);
    }

    // An expired access token is transparent to callers: refresh once, then retry.
    if (response.status === 401 && allowRetry && !isAuthPath(path)) {
        const refreshed = await attemptRefresh();
        if (refreshed) {
            return request<T>(path, options, false);
        }
        clearToken();
        throw await toApiError(response);
    }

    if (!response.ok) {
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
 * Downloads a binary resource with the Authorization header and the refresh cookie
 * (a plain link or window.open cannot send either) and returns it as a blob.
 */
async function downloadFile(path: string, fallbackFileName: string): Promise<{ blob: Blob; fileName: string }> {
    let response: Response;
    try {
        response = await fetch(`${API_BASE_URL}${path}`, {
            headers: accessToken ? { Authorization: `Bearer ${accessToken}` } : {},
            credentials: 'include',
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
        fileName: fileNameFromDisposition(response.headers.get('Content-Disposition'), fallbackFileName),
    };
}

/** Downloads a resume document. */
export function fetchResumeFile(resume: Resume): Promise<{ blob: Blob; fileName: string }> {
    return downloadFile(`/api/resumes/${resume.id}/download`, resume.fileName);
}

/** Exports the current job list as CSV, honouring the active search and status filters. */
export function exportJobsCsv(
    filters: { search?: string; status?: JobStatus | 'all' } = {},
): Promise<{ blob: Blob; fileName: string }> {
    const query = buildQuery({
        search: filters.search,
        status: filters.status && filters.status !== 'all' ? filters.status : undefined,
    });
    return downloadFile(`/api/jobs/export${query}`, 'job-applications.csv');
}

export const api = {
    register: async (credentials: RegisterCredentials) => {
        const response = await request<AuthResponse>('/api/auth/register', { method: 'POST', body: credentials });
        accessToken = response.token;
        return response;
    },

    login: async (credentials: LoginCredentials) => {
        const response = await request<AuthResponse>('/api/auth/login', { method: 'POST', body: credentials });
        accessToken = response.token;
        return response;
    },

    /** Revokes the refresh token server-side and clears the cookie. */
    logout: () => request<void>('/api/auth/logout', { method: 'POST' }),

    me: () => request<User>('/api/auth/me'),

    listJobs: (
        filters: {
            search?: string;
            status?: JobStatus | 'all';
            page?: number;
            size?: number;
            sort?: JobSortField;
            direction?: 'asc' | 'desc';
        } = {},
    ) =>
        request<PageResponse<Job>>(
            `/api/jobs${buildQuery({
                search: filters.search,
                status: filters.status && filters.status !== 'all' ? filters.status : undefined,
                page: filters.page === undefined ? undefined : String(filters.page),
                size: filters.size === undefined ? undefined : String(filters.size),
                sort: filters.sort,
                direction: filters.direction,
            })}`,
        ),

    jobStats: () => request<JobStats>('/api/jobs/stats'),

    /** Pipeline transitions for a single job, oldest first. */
    jobHistory: (id: number) => request<JobStatusHistoryEntry[]>(`/api/jobs/${id}/history`),

    /** Recent transitions across all of the user's jobs, newest first. */
    recentActivity: () => request<JobStatusHistoryEntry[]>('/api/jobs/activity'),

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

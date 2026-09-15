import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { api, ApiError, clearToken, exportJobsCsv, getToken, setToken } from './api';

/** Minimal fetch double - avoids depending on any particular fetch implementation. */
function mockResponse(options: {
  status?: number;
  body?: unknown;
  headers?: Record<string, string>;
}) {
  const { status = 200, body = null, headers = {} } = options;
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: { get: (key: string) => headers[key.toLowerCase()] ?? null },
    json: async () => body,
    text: async () => (body === null ? '' : JSON.stringify(body)),
  } as unknown as Response;
}

const fetchMock = vi.fn();

beforeEach(() => {
  localStorage.clear();
  fetchMock.mockReset();
  vi.stubGlobal('fetch', fetchMock);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('token storage', () => {
  it('round-trips the token', () => {
    expect(getToken()).toBeNull();
    setToken('abc.def.ghi');
    expect(getToken()).toBe('abc.def.ghi');
    clearToken();
    expect(getToken()).toBeNull();
  });
});

describe('api.login', () => {
  it('posts credentials as JSON and returns the auth response', async () => {
    fetchMock.mockResolvedValue(
      mockResponse({ status: 200, body: { token: 't', tokenType: 'Bearer', user: { id: 1, name: 'A', email: 'a@b.c' } } }),
    );

    const result = await api.login({ email: 'a@b.c', password: 'secret123' });

    expect(result.token).toBe('t');
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toContain('/api/auth/login');
    expect(init.method).toBe('POST');
    expect(init.headers['Content-Type']).toBe('application/json');
    expect(JSON.parse(init.body)).toEqual({ email: 'a@b.c', password: 'secret123' });
    expect(init.headers.Authorization).toBeUndefined();
  });

  it('surfaces the server message on invalid credentials', async () => {
    fetchMock.mockResolvedValue(
      mockResponse({ status: 401, body: { status: 401, message: 'Invalid email or password' } }),
    );

    await expect(api.login({ email: 'a@b.c', password: 'nope' })).rejects.toThrow('Invalid email or password');
  });
});

describe('authenticated requests', () => {
  it('sends the bearer token and query parameters', async () => {
    setToken('jwt-token');
    fetchMock.mockResolvedValue(mockResponse({ status: 200, body: [] }));

    await api.listJobs({ search: 'globex', status: 'applied' });

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toContain('/api/jobs?');
    expect(url).toContain('search=globex');
    expect(url).toContain('status=applied');
    expect(init.headers.Authorization).toBe('Bearer jwt-token');
  });

  it('omits the status parameter when filtering by "all"', async () => {
    fetchMock.mockResolvedValue(mockResponse({ status: 200, body: [] }));

    await api.listJobs({ status: 'all' });

    expect(fetchMock.mock.calls[0][0]).not.toContain('status=');
  });

  it('clears a stored token and reports the error when the session is rejected', async () => {
    setToken('expired-token');
    fetchMock.mockResolvedValue(mockResponse({ status: 401, body: { message: 'Authentication is required' } }));

    await expect(api.jobStats()).rejects.toBeInstanceOf(ApiError);
    expect(getToken()).toBeNull();
  });

  it('uses the first field error when the message is generic', async () => {
    fetchMock.mockResolvedValue(
      mockResponse({
        status: 400,
        body: { message: 'Validation failed', fieldErrors: { companyName: 'Company name is required' } },
      }),
    );

    await expect(
      api.createJob({ companyName: '', jobTitle: '', status: 'wishlist' }),
    ).rejects.toThrow('Company name is required');
  });

  it('resolves with undefined for 204 responses', async () => {
    fetchMock.mockResolvedValue(mockResponse({ status: 204 }));

    await expect(api.deleteJob(7)).resolves.toBeUndefined();
  });

  it('reports an unreachable server as a status 0 ApiError', async () => {
    fetchMock.mockRejectedValue(new Error('connection refused'));

    const error = await api.me().catch((caught) => caught);
    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(0);
  });

  it('passes paging and sorting parameters through to the server', async () => {
    fetchMock.mockResolvedValue(
      mockResponse({
        status: 200,
        body: { content: [], page: 2, size: 10, totalElements: 0, totalPages: 0, first: false, last: true },
      }),
    );

    const result = await api.listJobs({ page: 2, size: 10, sort: 'companyName', direction: 'asc' });

    const url = fetchMock.mock.calls[0][0];
    expect(url).toContain('page=2');
    expect(url).toContain('size=10');
    expect(url).toContain('sort=companyName');
    expect(url).toContain('direction=asc');
    expect(result.page).toBe(2);
    expect(result.content).toEqual([]);
  });
});

describe('exportJobsCsv', () => {
  it('requests the export endpoint with the active filters', async () => {
    fetchMock.mockResolvedValue({
      ok: true,
      status: 200,
      headers: {
        get: (key: string) =>
          key.toLowerCase() === 'content-disposition' ? 'attachment; filename="job-applications.csv"' : null,
      },
      blob: async () => new Blob(['id,companyName'], { type: 'text/csv' }),
    } as unknown as Response);

    const { fileName, blob } = await exportJobsCsv({ search: 'acme', status: 'applied' });

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toContain('/api/jobs/export');
    expect(url).toContain('search=acme');
    expect(url).toContain('status=applied');
    expect(init.headers.Authorization).toBeUndefined();
    expect(fileName).toBe('job-applications.csv');
    // jsdom's Blob has no .text(), so assert on the blob itself instead of its contents.
    expect(blob).toBeInstanceOf(Blob);
    expect(blob.size).toBeGreaterThan(0);
  });
});

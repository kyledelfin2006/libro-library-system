import { afterEach, vi } from 'vitest';
import { ApiError, parseRetryAfter, request, setAccessToken, setUnauthorizedHandler } from './client';

function respond(status: number, body: unknown, headers: Record<string, string> = {}) {
  return vi.fn().mockImplementation(() => Promise.resolve(new Response(JSON.stringify(body), { status, headers })));
}

afterEach(() => {
  vi.restoreAllMocks();
  setAccessToken(null);
  setUnauthorizedHandler(null);
});

describe('api client', () => {
  it('unwraps the ApiResponse envelope once', async () => {
    vi.stubGlobal('fetch', respond(200, { success: true, message: 'Success', data: { id: 7 }, timestamp: 1 }));
    await expect(request('GET', '/app/x')).resolves.toEqual({ id: 7 });
  });

  it('sends the bearer token only on authenticated calls', async () => {
    const fetchMock = respond(200, { success: true, data: null });
    vi.stubGlobal('fetch', fetchMock);
    setAccessToken('tok');
    await request('GET', '/app/x');
    await request('POST', '/app/auth/login', { authenticated: false, body: {} });
    const calls = fetchMock.mock.calls as [string, RequestInit][];
    expect((calls[0]![1].headers as Record<string, string>).Authorization).toBe('Bearer tok');
    expect((calls[1]![1].headers as Record<string, string>).Authorization).toBeUndefined();
  });

  it('maps ErrorResponse fields, code, requestId and Retry-After', async () => {
    vi.stubGlobal(
      'fetch',
      respond(
        429,
        { error: 'Too Many Requests', details: 'Slow down', fieldErrors: { a: 'b' }, requestId: 'r-1', code: 'RATE' },
        { 'Retry-After': '42' },
      ),
    );
    const err = await request('POST', '/app/auth/login', { authenticated: false }).catch((e) => e as ApiError);
    expect(err).toBeInstanceOf(ApiError);
    expect(err).toMatchObject({ status: 429, code: 'RATE', requestId: 'r-1', retryAfterSeconds: 42, fieldErrors: { a: 'b' } });
  });

  it('runs the unauthorized handler on an authenticated 401 only', async () => {
    const handler = vi.fn();
    setUnauthorizedHandler(handler);
    vi.stubGlobal('fetch', respond(401, { error: 'Unauthorized', details: 'x' }));
    await request('GET', '/app/x').catch(() => undefined);
    expect(handler).toHaveBeenCalledTimes(1);
    await request('POST', '/app/auth/login', { authenticated: false }).catch(() => undefined);
    expect(handler).toHaveBeenCalledTimes(1);
  });

  it('turns network failures into a status-0 error', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('offline')));
    await expect(request('GET', '/app/x')).rejects.toMatchObject({ status: 0, code: 'NETWORK' });
  });

  it('parses Retry-After', () => {
    expect(parseRetryAfter('7')).toBe(7);
    expect(parseRetryAfter(null)).toBeNull();
    expect(parseRetryAfter('soon')).toBeNull();
  });
});

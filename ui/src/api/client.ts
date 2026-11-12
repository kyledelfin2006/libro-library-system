import type { ErrorBody } from './types';

/** Normalized API failure. UI code branches on status and code, never on English message text. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string | null,
    message: string,
    readonly fieldErrors: Record<string, string> = {},
    readonly requestId: string | null = null,
    readonly retryAfterSeconds: number | null = null,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

// The 15-minute Bearer token lives only in this module's memory: never in web storage or URLs.
let accessToken: string | null = null;
let onUnauthorized: (() => void) | null = null;

export function setAccessToken(token: string | null): void {
  accessToken = token;
}

/** Registers the handler that clears session state and routes to sign-in after a 401. */
export function setUnauthorizedHandler(handler: (() => void) | null): void {
  onUnauthorized = handler;
}

/** Parses a Retry-After header expressed in seconds; returns null when absent or not numeric. */
export function parseRetryAfter(value: string | null): number | null {
  if (value === null) return null;
  const seconds = Number(value);
  return Number.isFinite(seconds) && seconds >= 0 ? Math.ceil(seconds) : null;
}

async function readBody(response: Response): Promise<unknown> {
  const text = await response.text();
  if (!text) return null;
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

/**
 * Sends a JSON request and unwraps the ApiResponse envelope once. Failures become ApiError. A 401 on an
 * authenticated call clears the session through the registered handler; mutations are never retried here.
 */
export async function request<T>(
  method: string,
  path: string,
  options: { body?: unknown; authenticated?: boolean } = {},
): Promise<T> {
  const { body, authenticated = true } = options;
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (authenticated && accessToken) headers.Authorization = `Bearer ${accessToken}`;

  let response: Response;
  try {
    response = await fetch(path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  } catch {
    throw new ApiError(0, 'NETWORK', 'The library service could not be reached. Check your connection and try again.');
  }

  const payload = (await readBody(response)) as Record<string, unknown> | null;

  if (!response.ok) {
    const err = (payload ?? {}) as Partial<ErrorBody>;
    if (response.status === 401 && authenticated) onUnauthorized?.();
    throw new ApiError(
      response.status,
      err.code ?? null,
      err.details ?? err.error ?? 'Something went wrong on our side.',
      err.fieldErrors ?? {},
      err.requestId ?? response.headers.get('X-Request-Id'),
      parseRetryAfter(response.headers.get('Retry-After')),
    );
  }

  return (payload && 'data' in payload ? payload.data : payload) as T;
}

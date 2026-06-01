/**
 * API client for the TabVault backend REST API.
 *
 * All authenticated requests attach the JWT access token from localStorage.
 * On 401 responses the client attempts a token refresh; if refresh fails,
 * the user is signed out.
 *
 * The base URL is read from the VITE_API_BASE_URL environment variable.
 * Defaults to http://localhost:8080 for local development.
 */

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';

export const API_BASE = API_BASE_URL;

export interface ApiErrorBody {
  error: {
    code: string;
    message: string;
    field?: string;
  };
}

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
    public readonly field?: string,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

async function parseError(response: Response): Promise<ApiError> {
  try {
    const body: ApiErrorBody = await response.json();
    return new ApiError(
      response.status,
      body.error.code,
      body.error.message,
      body.error.field,
    );
  } catch {
    return new ApiError(
      response.status,
      'UNKNOWN_ERROR',
      `HTTP ${response.status}: ${response.statusText}`,
    );
  }
}

function getStoredTokens(): { accessToken: string | null; refreshToken: string | null } {
  return {
    accessToken: localStorage.getItem('tabvault_access_token'),
    refreshToken: localStorage.getItem('tabvault_refresh_token'),
  };
}

export function storeTokens(accessToken: string, refreshToken?: string): void {
  localStorage.setItem('tabvault_access_token', accessToken);
  if (refreshToken !== undefined) {
    localStorage.setItem('tabvault_refresh_token', refreshToken);
  }
}

export function clearTokens(): void {
  localStorage.removeItem('tabvault_access_token');
  localStorage.removeItem('tabvault_refresh_token');
}

let isRefreshing = false;
let refreshPromise: Promise<string | null> | null = null;

async function refreshAccessToken(): Promise<string | null> {
  const { refreshToken } = getStoredTokens();
  if (!refreshToken) return null;

  try {
    const response = await fetch(`${API_BASE}/api/auth/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken }),
    });

    if (!response.ok) {
      clearTokens();
      return null;
    }

    const data: { accessToken: string; refreshToken?: string } = await response.json();
    storeTokens(data.accessToken, data.refreshToken);
    return data.accessToken;
  } catch {
    clearTokens();
    return null;
  }
}

async function getValidAccessToken(): Promise<string | null> {
  const { accessToken } = getStoredTokens();
  return accessToken;
}

export async function apiRequest<T>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  const accessToken = await getValidAccessToken();

  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(options.headers as Record<string, string> ?? {}),
  };

  if (accessToken) {
    headers['Authorization'] = `Bearer ${accessToken}`;
  }

  let response = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers,
  });

  // Attempt token refresh on 401
  if (response.status === 401 && accessToken) {
    if (!isRefreshing) {
      isRefreshing = true;
      refreshPromise = refreshAccessToken().finally(() => {
        isRefreshing = false;
        refreshPromise = null;
      });
    }

    const newToken = await refreshPromise;

    if (newToken) {
      headers['Authorization'] = `Bearer ${newToken}`;
      response = await fetch(`${API_BASE}${path}`, {
        ...options,
        headers,
      });
    } else {
      // Refresh failed — clear auth state and redirect to login
      clearTokens();
      window.location.href = '/login';
      throw new ApiError(401, 'UNAUTHORIZED', 'Session expired. Please log in again.');
    }
  }

  if (!response.ok) {
    throw await parseError(response);
  }

  // Handle 204 No Content
  if (response.status === 204) {
    return undefined as T;
  }

  return response.json() as Promise<T>;
}

/**
 * Backend API client for the TabVault Chrome Extension service worker.
 *
 * All public functions read tokens from chrome.storage.local on every call.
 * They never hold token values in module-level variables (AC-052).
 *
 * When the stored access token is expired or absent, getValidAccessToken()
 * attempts a refresh before the API call (AC-053). If the refresh returns
 * HTTP 401, tokens are cleared and an AuthError is thrown so the popup can
 * display a re-authentication prompt (AC-054).
 */

import { API_BASE_URL } from "./config.js";
import {
  clearTokens,
  getStoredTokens,
  isAccessTokenExpiredOrAbsent,
  storeAccessToken,
} from "./tokenStorage.js";

// ---------------------------------------------------------------------------
// Custom error types
// ---------------------------------------------------------------------------

/** Thrown when the user is not authenticated or the session has expired. */
export class AuthError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "AuthError";
  }
}

/** Thrown when a backend request fails for a non-auth reason. */
export class ApiError extends Error {
  constructor(
    message: string,
    public readonly statusCode: number
  ) {
    super(message);
    this.name = "ApiError";
  }
}

// ---------------------------------------------------------------------------
// Types mirroring backend DTOs
// ---------------------------------------------------------------------------

export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
}

export interface SaveTabRequest {
  url: string;
  title: string;
  faviconUrl?: string;
}

export interface BatchSaveRequest {
  tabs: Array<{ url: string; title: string; faviconUrl?: string }>;
}

export interface SaveNoteRequest {
  noteBody: string;
}

export interface ItemResponse {
  id: number;
  itemType: string;
  url: string | null;
  title: string | null;
  faviconUrl: string | null;
  summary: string | null;
  noteBody: string | null;
  categoryId: number | null;
  pinned: boolean;
  archived: boolean;
  createdAt: string;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

// ---------------------------------------------------------------------------
// Token management
// ---------------------------------------------------------------------------

/**
 * Returns a valid access token, refreshing if necessary (AC-053).
 *
 * Reads tokens fresh from chrome.storage.local on every call — no module-
 * level caching — to survive service worker termination (AC-052).
 *
 * Throws AuthError if:
 *  - no refresh token is stored (user is not logged in)
 *  - the refresh request returns HTTP 401 (session expired)
 */
async function getValidAccessToken(): Promise<string> {
  const { accessToken, refreshToken } = await getStoredTokens();

  if (!isAccessTokenExpiredOrAbsent(accessToken)) {
    // Token is still valid — return it without a refresh call.
    return accessToken as string;
  }

  // Access token is expired or absent — attempt a refresh (AC-053).
  if (!refreshToken) {
    throw new AuthError("No refresh token stored. User must log in.");
  }

  const refreshed = await attemptTokenRefresh(refreshToken);
  await storeAccessToken(refreshed);
  return refreshed;
}

/**
 * Requests a new access token from the backend refresh endpoint.
 *
 * Throws AuthError (and clears tokens) if the refresh returns HTTP 401 (AC-054).
 */
async function attemptTokenRefresh(refreshToken: string): Promise<string> {
  const response = await fetch(`${API_BASE_URL}/api/auth/refresh`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ refreshToken }),
  });

  if (response.status === 401) {
    // Refresh failed — clear tokens so the popup shows re-auth prompt (AC-054).
    await clearTokens();
    throw new AuthError(
      "Session expired. Please log in again."
    );
  }

  if (!response.ok) {
    throw new ApiError(
      `Token refresh failed with status ${response.status}`,
      response.status
    );
  }

  const body = (await response.json()) as { accessToken: string };
  return body.accessToken;
}

// ---------------------------------------------------------------------------
// Auth endpoints
// ---------------------------------------------------------------------------

/**
 * Logs in the user and stores both tokens in chrome.storage.local.
 *
 * Called from the popup login form. Returns the accessToken on success.
 */
export async function login(request: LoginRequest): Promise<LoginResponse> {
  const response = await fetch(`${API_BASE_URL}/api/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request),
  });

  if (!response.ok) {
    const statusCode = response.status;
    throw new ApiError(`Login failed with status ${statusCode}`, statusCode);
  }

  return response.json() as Promise<LoginResponse>;
}

// ---------------------------------------------------------------------------
// Item endpoints
// ---------------------------------------------------------------------------

/**
 * Saves a single browser tab to the backend (AC-049 / US-001).
 *
 * Refreshes the access token if expired before sending the request (AC-053).
 */
export async function saveTab(request: SaveTabRequest): Promise<ItemResponse> {
  const accessToken = await getValidAccessToken();

  const response = await fetch(`${API_BASE_URL}/api/items`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${accessToken}`,
    },
    body: JSON.stringify(request),
  });

  if (response.status === 401) {
    await clearTokens();
    throw new AuthError("Session expired. Please log in again.");
  }

  if (!response.ok) {
    throw new ApiError(
      `Save tab failed with status ${response.status}`,
      response.status
    );
  }

  return response.json() as Promise<ItemResponse>;
}

/**
 * Saves all tabs in the current window to the backend (AC-050 / US-002).
 *
 * Returns immediately after the backend accepts the batch (HTTP 202).
 */
export async function saveBatchTabs(request: BatchSaveRequest): Promise<void> {
  const accessToken = await getValidAccessToken();

  const response = await fetch(`${API_BASE_URL}/api/items/batch`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${accessToken}`,
    },
    body: JSON.stringify(request),
  });

  if (response.status === 401) {
    await clearTokens();
    throw new AuthError("Session expired. Please log in again.");
  }

  if (!response.ok) {
    throw new ApiError(
      `Batch save failed with status ${response.status}`,
      response.status
    );
  }
}

/**
 * Saves a plain text note to the backend (AC-051 / US-008).
 */
export async function saveNote(request: SaveNoteRequest): Promise<ItemResponse> {
  const accessToken = await getValidAccessToken();

  const response = await fetch(`${API_BASE_URL}/api/items/notes`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${accessToken}`,
    },
    body: JSON.stringify(request),
  });

  if (response.status === 401) {
    await clearTokens();
    throw new AuthError("Session expired. Please log in again.");
  }

  if (!response.ok) {
    throw new ApiError(
      `Save note failed with status ${response.status}`,
      response.status
    );
  }

  return response.json() as Promise<ItemResponse>;
}

/**
 * Fetches the 5 most recently saved items for the popup recent-items list (AC-048).
 *
 * Uses GET /api/items with pageSize=5 and page=0 (default ordering is newest first).
 */
export async function fetchRecentItems(): Promise<ItemResponse[]> {
  const accessToken = await getValidAccessToken();

  const response = await fetch(
    `${API_BASE_URL}/api/items?page=0&pageSize=5`,
    {
      method: "GET",
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
    }
  );

  if (response.status === 401) {
    await clearTokens();
    throw new AuthError("Session expired. Please log in again.");
  }

  if (!response.ok) {
    throw new ApiError(
      `Fetch items failed with status ${response.status}`,
      response.status
    );
  }

  const page = (await response.json()) as PageResponse<ItemResponse>;
  return page.content;
}

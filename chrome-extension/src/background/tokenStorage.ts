/**
 * Token storage module — all JWT token reads and writes go through this module.
 *
 * Tokens are stored exclusively in chrome.storage.local (AC-052).
 * The service worker must never hold token values in module-level or global
 * variables because the MV3 service worker is ephemeral and those values would
 * be lost when the worker is terminated between events.
 */

import {
  STORAGE_KEY_ACCESS_TOKEN,
  STORAGE_KEY_REFRESH_TOKEN,
} from "./config.js";

export interface StoredTokens {
  accessToken: string | null;
  refreshToken: string | null;
}

/** Reads both tokens from chrome.storage.local. */
export async function getStoredTokens(): Promise<StoredTokens> {
  const result = await chrome.storage.local.get([
    STORAGE_KEY_ACCESS_TOKEN,
    STORAGE_KEY_REFRESH_TOKEN,
  ]);
  return {
    accessToken: (result[STORAGE_KEY_ACCESS_TOKEN] as string | undefined) ?? null,
    refreshToken: (result[STORAGE_KEY_REFRESH_TOKEN] as string | undefined) ?? null,
  };
}

/** Persists both tokens to chrome.storage.local. */
export async function storeTokens(
  accessToken: string,
  refreshToken: string
): Promise<void> {
  await chrome.storage.local.set({
    [STORAGE_KEY_ACCESS_TOKEN]: accessToken,
    [STORAGE_KEY_REFRESH_TOKEN]: refreshToken,
  });
}

/** Persists only the access token (used after a successful token refresh). */
export async function storeAccessToken(accessToken: string): Promise<void> {
  await chrome.storage.local.set({
    [STORAGE_KEY_ACCESS_TOKEN]: accessToken,
  });
}

/**
 * Clears both tokens from chrome.storage.local.
 *
 * Called when a token refresh returns HTTP 401 so the popup can show
 * a re-authentication prompt (AC-054).
 */
export async function clearTokens(): Promise<void> {
  await chrome.storage.local.remove([
    STORAGE_KEY_ACCESS_TOKEN,
    STORAGE_KEY_REFRESH_TOKEN,
  ]);
}

/**
 * Returns true if the stored JWT access token is absent or expired.
 *
 * Parses the token's exp claim without a crypto library — the signature is
 * not re-verified here because we trust the value we stored ourselves.
 */
export function isAccessTokenExpiredOrAbsent(accessToken: string | null): boolean {
  if (!accessToken) return true;

  try {
    const parts = accessToken.split(".");
    if (parts.length !== 3) return true;

    // Base64url decode the payload (no padding needed for JSON.parse).
    const payloadJson = atob(parts[1].replace(/-/g, "+").replace(/_/g, "/"));
    const payload = JSON.parse(payloadJson) as { exp?: number };

    if (typeof payload.exp !== "number") return true;

    // Add a 30-second buffer so we refresh before the token actually expires.
    const nowSeconds = Date.now() / 1000;
    return payload.exp - nowSeconds < 30;
  } catch {
    // Malformed token — treat as expired so we attempt a refresh.
    return true;
  }
}

/**
 * Extension-wide configuration constants.
 *
 * API_BASE_URL is read from the VITE_API_BASE_URL environment variable at
 * build time. If the variable is absent, it falls back to the default
 * localhost development address.
 *
 * To override at build time:
 *   VITE_API_BASE_URL=https://api.tabvault.app npm run build
 */
export const API_BASE_URL: string =
  (import.meta.env.VITE_API_BASE_URL as string | undefined) ??
  "http://localhost:8080";

/** chrome.storage.local keys — keep in one place to avoid typos. */
export const STORAGE_KEY_ACCESS_TOKEN = "tabvault_access_token";
export const STORAGE_KEY_REFRESH_TOKEN = "tabvault_refresh_token";

/** Alarm name used to surface reminder notifications. */
export const ALARM_NAME_REMINDER_CHECK = "tabvault_reminder_check";

/** How many recent items to show in the popup (AC-048). */
export const RECENT_ITEMS_LIMIT = 5;

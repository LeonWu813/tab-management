/**
 * Popup-side messaging helpers.
 *
 * All API calls from the popup go through the background service worker via
 * chrome.runtime.sendMessage so that token refresh logic and storage access
 * remain in the service worker (AC-052, AC-053, AC-054).
 */

import type { BackgroundMessage, BackgroundResponse } from "../background/index.js";
import type { ItemRecord } from "./types.js";

/** Sends a message to the background service worker and awaits its response. */
async function sendToBackground(message: BackgroundMessage): Promise<BackgroundResponse> {
  return chrome.runtime.sendMessage<BackgroundMessage, BackgroundResponse>(message);
}

/** Logs the user in. Returns true on success, throws on error. */
export async function loginUser(email: string, password: string): Promise<void> {
  const response = await sendToBackground({
    type: "LOGIN",
    payload: { email, password },
  });

  if (!response.success) {
    throw new Error(response.error);
  }
}

/** Clears stored tokens (logs out). */
export async function logoutUser(): Promise<void> {
  await sendToBackground({ type: "LOGOUT" });
}

/** Saves the current active tab. */
export async function saveCurrentTab(): Promise<ItemRecord | null> {
  const response = await sendToBackground({ type: "SAVE_CURRENT_TAB" });

  if (!response.success) {
    const err = new Error(response.error);
    (err as Error & { requiresReauth?: boolean }).requiresReauth =
      response.requiresReauth ?? false;
    throw err;
  }

  return (response.data as ItemRecord | undefined) ?? null;
}

/** Saves all tabs in the current window. */
export async function saveAllTabs(): Promise<void> {
  const response = await sendToBackground({ type: "SAVE_ALL_TABS" });

  if (!response.success) {
    const err = new Error(response.error);
    (err as Error & { requiresReauth?: boolean }).requiresReauth =
      response.requiresReauth ?? false;
    throw err;
  }
}

/** Saves a quick note. */
export async function saveQuickNote(noteBody: string): Promise<ItemRecord> {
  const response = await sendToBackground({
    type: "SAVE_NOTE",
    payload: { noteBody },
  });

  if (!response.success) {
    const err = new Error(response.error);
    (err as Error & { requiresReauth?: boolean }).requiresReauth =
      response.requiresReauth ?? false;
    throw err;
  }

  return response.data as ItemRecord;
}

/**
 * Fetches the 5 most recently saved items for the popup list (AC-048).
 *
 * Returns an empty array (not an error) when the user is not logged in.
 */
export async function fetchRecentItems(): Promise<ItemRecord[]> {
  const response = await sendToBackground({ type: "FETCH_RECENT_ITEMS" });

  if (!response.success) {
    if (response.requiresReauth) {
      // Caller can detect this and redirect to the login view.
      const err = new Error(response.error);
      (err as Error & { requiresReauth?: boolean }).requiresReauth = true;
      throw err;
    }
    throw new Error(response.error);
  }

  return (response.data as ItemRecord[]) ?? [];
}

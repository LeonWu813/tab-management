/**
 * TabVault Chrome Extension — Background Service Worker (Manifest V3).
 *
 * Responsibilities:
 *  - Listen for keyboard commands (AC-049, AC-050, AC-051) and relay them to
 *    the active tab via the popup or directly via chrome.tabs.
 *  - Handle messages from the popup for save-tab, save-all-tabs, save-note,
 *    login, logout, and fetch-recent-items operations.
 *  - Manage chrome.alarms for periodic reminder checks and display
 *    in-browser notifications when reminders are due (US-007).
 *  - All token reads/writes go through tokenStorage — never cached in
 *    module-level variables (AC-052).
 *
 * The service worker is ephemeral: it may be terminated by Chrome at any
 * time when idle. All persistent state is kept in chrome.storage.local.
 */

import { ALARM_NAME_REMINDER_CHECK } from "./config.js";
import {
  AuthError,
  login,
  saveTab,
  saveBatchTabs,
  saveNote,
  fetchRecentItems,
  type LoginRequest,
  type ItemResponse,
} from "./apiClient.js";
import { clearTokens, storeTokens } from "./tokenStorage.js";

// ---------------------------------------------------------------------------
// Message types shared between popup and service worker
// ---------------------------------------------------------------------------

export type BackgroundMessage =
  | { type: "LOGIN"; payload: LoginRequest }
  | { type: "LOGOUT" }
  | { type: "SAVE_CURRENT_TAB" }
  | { type: "SAVE_ALL_TABS" }
  | { type: "SAVE_NOTE"; payload: { noteBody: string } }
  | { type: "FETCH_RECENT_ITEMS" };

export type BackgroundResponse =
  | { success: true; data?: unknown }
  | { success: false; error: string; requiresReauth?: boolean };

// ---------------------------------------------------------------------------
// Keyboard command handler (AC-049, AC-050, AC-051)
// ---------------------------------------------------------------------------

chrome.commands.onCommand.addListener((command) => {
  if (command === "save-current-tab") {
    handleSaveCurrentTab();
  } else if (command === "save-all-tabs") {
    handleSaveAllTabs();
  } else if (command === "open-quick-note") {
    // AC-051: open the popup programmatically so the user sees the quick note
    // input. Chrome does not expose a direct API to open a specific panel;
    // we open the extension popup and the popup reads a pending-note flag
    // from chrome.storage.local to pre-focus the note input.
    chrome.storage.local.set({ tabvault_open_quick_note: true });
    chrome.action.openPopup().catch(() => {
      // openPopup() can fail if the popup is already open or in a context
      // where it is not allowed (e.g. fullscreen). Silently ignore — the
      // flag in storage will still be read on the next popup open.
    });
  }
});

// ---------------------------------------------------------------------------
// Message handler (requests from the popup)
// ---------------------------------------------------------------------------

chrome.runtime.onMessage.addListener(
  (
    message: BackgroundMessage,
    _sender: chrome.runtime.MessageSender,
    sendResponse: (response: BackgroundResponse) => void
  ) => {
    // Returning true from the listener tells Chrome to keep the message
    // channel open while the async handler runs.
    (async () => {
      try {
        switch (message.type) {
          case "LOGIN": {
            const loginResult = await login(message.payload);
            await storeTokens(loginResult.accessToken, loginResult.refreshToken);
            sendResponse({ success: true, data: { loggedIn: true } });
            break;
          }

          case "LOGOUT": {
            await clearTokens();
            sendResponse({ success: true });
            break;
          }

          case "SAVE_CURRENT_TAB": {
            const item = await handleSaveCurrentTab();
            sendResponse({ success: true, data: item });
            break;
          }

          case "SAVE_ALL_TABS": {
            await handleSaveAllTabs();
            sendResponse({ success: true });
            break;
          }

          case "SAVE_NOTE": {
            const noteItem = await saveNote({ noteBody: message.payload.noteBody });
            sendResponse({ success: true, data: noteItem });
            break;
          }

          case "FETCH_RECENT_ITEMS": {
            const items = await fetchRecentItems();
            sendResponse({ success: true, data: items });
            break;
          }

          default: {
            sendResponse({ success: false, error: "Unknown message type" });
          }
        }
      } catch (err) {
        if (err instanceof AuthError) {
          sendResponse({
            success: false,
            error: err.message,
            requiresReauth: true,
          });
        } else if (err instanceof Error) {
          sendResponse({ success: false, error: err.message });
        } else {
          sendResponse({ success: false, error: "An unexpected error occurred" });
        }
      }
    })();

    // Return true to keep the message channel open for the async response.
    return true;
  }
);

// ---------------------------------------------------------------------------
// Save helpers
// ---------------------------------------------------------------------------

/**
 * Reads the active tab's URL and title, then POSTs to POST /api/items.
 *
 * AC-049: triggered by Ctrl+Shift+S / Cmd+Shift+S keyboard shortcut.
 */
async function handleSaveCurrentTab(): Promise<ItemResponse | undefined> {
  const [activeTab] = await chrome.tabs.query({
    active: true,
    currentWindow: true,
  });

  if (!activeTab?.url || !activeTab.title) {
    console.warn("TabVault: No active tab URL or title found.");
    return undefined;
  }

  // Skip chrome:// and other non-http(s) URLs that cannot be saved.
  if (
    !activeTab.url.startsWith("http://") &&
    !activeTab.url.startsWith("https://")
  ) {
    console.warn("TabVault: Skipping non-http(s) URL:", activeTab.url);
    return undefined;
  }

  const item = await saveTab({
    url: activeTab.url,
    title: activeTab.title,
    faviconUrl: activeTab.favIconUrl ?? undefined,
  });

  showSaveNotification("Tab saved!", activeTab.title);
  return item;
}

/**
 * Reads all tabs in the current window and POSTs to POST /api/items/batch.
 *
 * AC-050: triggered by Ctrl+Shift+A / Cmd+Shift+A keyboard shortcut.
 */
async function handleSaveAllTabs(): Promise<void> {
  const tabs = await chrome.tabs.query({ currentWindow: true });

  const httpTabs = tabs.filter(
    (tab) =>
      tab.url &&
      (tab.url.startsWith("http://") || tab.url.startsWith("https://"))
  );

  if (httpTabs.length === 0) {
    console.warn("TabVault: No HTTP(S) tabs found in current window.");
    return;
  }

  await saveBatchTabs({
    tabs: httpTabs.map((tab) => ({
      url: tab.url as string,
      title: tab.title ?? tab.url as string,
      faviconUrl: tab.favIconUrl ?? undefined,
    })),
  });

  showSaveNotification(
    "All tabs saved!",
    `${httpTabs.length} tab${httpTabs.length !== 1 ? "s" : ""} queued for saving.`
  );
}

// ---------------------------------------------------------------------------
// Notification helper
// ---------------------------------------------------------------------------

function showSaveNotification(title: string, message: string): void {
  chrome.notifications.create({
    type: "basic",
    iconUrl: "icons/icon48.png",
    title,
    message,
  });
}

// ---------------------------------------------------------------------------
// Alarm-based reminder check (US-007)
// ---------------------------------------------------------------------------

/**
 * Creates a periodic alarm that fires every 60 minutes to check for
 * upcoming reminders and dispatch in-browser notifications.
 *
 * The alarm is created on service worker startup so it is always registered
 * even after the service worker is restarted by Chrome.
 */
chrome.alarms.create(ALARM_NAME_REMINDER_CHECK, {
  periodInMinutes: 60,
});

chrome.alarms.onAlarm.addListener((alarm) => {
  if (alarm.name === ALARM_NAME_REMINDER_CHECK) {
    checkAndDispatchReminders();
  }
});

/**
 * Checks chrome.storage.local for any reminder records that are due today
 * and fires in-browser notifications.
 *
 * In the full integration flow, the backend's Quartz scheduler dispatches
 * Web Push notifications to the registered push subscription. This client-
 * side alarm serves as a secondary in-extension notification path for
 * reminders that are stored locally (e.g., items cached in the popup).
 *
 * The alarm listener avoids calling the backend if the user is not logged in.
 */
async function checkAndDispatchReminders(): Promise<void> {
  try {
    const result = await chrome.storage.local.get("tabvault_pending_reminders");
    const reminders = (
      result["tabvault_pending_reminders"] as Array<{
        id: number;
        label: string;
        dueDate: string;
      }> | undefined
    ) ?? [];

    const today = new Date().toISOString().slice(0, 10);
    const dueToday = reminders.filter((r) => r.dueDate <= today);

    for (const reminder of dueToday) {
      chrome.notifications.create(`reminder_${reminder.id}`, {
        type: "basic",
        iconUrl: "icons/icon48.png",
        title: "TabVault Reminder",
        message: reminder.label,
      });
    }
  } catch (err) {
    console.warn("TabVault: Reminder check failed:", err);
  }
}

// ---------------------------------------------------------------------------
// Install / startup handler
// ---------------------------------------------------------------------------

chrome.runtime.onInstalled.addListener(() => {
  console.info("TabVault extension installed.");
});

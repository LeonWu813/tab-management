import React, { useCallback, useEffect, useState } from "react";
import { LoginView } from "./LoginView.js";
import { MainView } from "./MainView.js";
import { QuickNoteView } from "./QuickNoteView.js";
import type { PopupView } from "./types.js";
import {
  STORAGE_KEY_ACCESS_TOKEN,
  STORAGE_KEY_REFRESH_TOKEN,
} from "../background/config.js";

/**
 * Root application component for the TabVault popup.
 *
 * Determines which view to show based on authentication state:
 *  - "login"      — no stored tokens (or token refresh returned 401)
 *  - "main"       — authenticated user, normal actions + recent items
 *  - "quick-note" — authenticated user opened quick note via shortcut
 *
 * Auth state detection:
 *   On mount, checks chrome.storage.local for both tokens. If either is
 *   absent the login view is shown. The service worker handles actual token
 *   validity/refresh — the popup only checks presence.
 *
 * Re-auth prompt (AC-054):
 *   When any service worker call returns { requiresReauth: true }, the popup
 *   transitions to the login view with a reauthMessage banner explaining that
 *   the session expired.
 */
export function App(): React.ReactElement {
  const [view, setView] = useState<PopupView>("login");
  const [isCheckingAuth, setIsCheckingAuth] = useState(true);
  const [reauthMessage, setReauthMessage] = useState<string | null>(null);

  // Check auth state on popup open.
  useEffect(() => {
    const checkAuthState = async (): Promise<void> => {
      // AC-052: tokens live exclusively in chrome.storage.local.
      const result = await chrome.storage.local.get([
        STORAGE_KEY_ACCESS_TOKEN,
        STORAGE_KEY_REFRESH_TOKEN,
      ]);

      const hasAccessToken = Boolean(result[STORAGE_KEY_ACCESS_TOKEN]);
      const hasRefreshToken = Boolean(result[STORAGE_KEY_REFRESH_TOKEN]);

      if (hasAccessToken || hasRefreshToken) {
        // Check if the popup was opened via the quick-note shortcut (AC-051).
        const flagResult = await chrome.storage.local.get("tabvault_open_quick_note");
        const openQuickNote = flagResult["tabvault_open_quick_note"] as boolean | undefined;

        if (openQuickNote) {
          // Clear the flag so the next popup open doesn't auto-open the note.
          await chrome.storage.local.remove("tabvault_open_quick_note");
          setView("quick-note");
        } else {
          setView("main");
        }
      } else {
        setView("login");
      }

      setIsCheckingAuth(false);
    };

    void checkAuthState();
  }, []);

  const handleLoginSuccess = useCallback((): void => {
    setReauthMessage(null);
    setView("main");
  }, []);

  const handleLogout = useCallback((): void => {
    setReauthMessage(null);
    setView("login");
  }, []);

  /**
   * AC-054: Transition to the login view with a re-authentication banner when
   * the service worker reports that the session has expired (token refresh
   * returned HTTP 401 and tokens were cleared).
   */
  const handleReauthRequired = useCallback((): void => {
    setReauthMessage(
      "Your session has expired. Please sign in again to continue."
    );
    setView("login");
  }, []);

  const handleOpenQuickNote = useCallback((): void => {
    setView("quick-note");
  }, []);

  const handleNoteSaved = useCallback((): void => {
    setView("main");
  }, []);

  const handleCancelNote = useCallback((): void => {
    setView("main");
  }, []);

  // Show a blank screen while checking auth — avoids a flash of the login form.
  if (isCheckingAuth) {
    return (
      <div style={styles.loading} aria-label="Loading TabVault…">
        <span style={styles.loadingDot} />
      </div>
    );
  }

  switch (view) {
    case "login":
      return (
        <LoginView
          onLoginSuccess={handleLoginSuccess}
          reauthMessage={reauthMessage ?? undefined}
        />
      );

    case "quick-note":
      return (
        <QuickNoteView
          onNoteSaved={handleNoteSaved}
          onCancel={handleCancelNote}
          onReauthRequired={handleReauthRequired}
        />
      );

    case "main":
    default:
      return (
        <MainView
          onReauthRequired={handleReauthRequired}
          onLogout={handleLogout}
          onOpenQuickNote={handleOpenQuickNote}
        />
      );
  }
}

// ---------------------------------------------------------------------------
// Inline styles
// ---------------------------------------------------------------------------

const styles: Record<string, React.CSSProperties> = {
  loading: {
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    height: 80,
    width: "100%",
  },
  loadingDot: {
    width: 8,
    height: 8,
    borderRadius: "50%",
    backgroundColor: "#4a8cde",
    animation: "pulse 1.2s infinite",
  },
};

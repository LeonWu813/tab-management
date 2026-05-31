import React, { useCallback, useEffect, useState } from "react";
import { saveCurrentTab, saveAllTabs, logoutUser, fetchRecentItems } from "./messaging.js";
import { RecentItemsList } from "./RecentItemsList.js";
import type { ItemRecord } from "./types.js";

interface MainViewProps {
  /** Called when the service worker reports a 401 / requiresReauth error. */
  onReauthRequired: () => void;
  /** Called after the user clicks "Sign out". */
  onLogout: () => void;
  /**
   * When true, the popup was opened via the Cmd+Shift+N shortcut and the
   * caller should have already navigated to the QuickNoteView.
   * This prop is not used here; navigation is handled in App.
   */
  onOpenQuickNote: () => void;
}

/**
 * Main authenticated view of the popup.
 *
 * Shows:
 *  - Save current tab button
 *  - Save all tabs button
 *  - Quick note button (navigates to QuickNoteView)
 *  - 5 most recently saved items (AC-048)
 *  - Sign-out button
 */
export function MainView({
  onReauthRequired,
  onLogout,
  onOpenQuickNote,
}: MainViewProps): React.ReactElement {
  const [recentItems, setRecentItems] = useState<ItemRecord[]>([]);
  const [isLoadingItems, setIsLoadingItems] = useState(true);
  const [saveStatus, setSaveStatus] = useState<"idle" | "saving" | "saved" | "error">("idle");
  const [saveMessage, setSaveMessage] = useState<string | null>(null);

  // Fetch recent items on mount (AC-048).
  const loadRecentItems = useCallback(async (): Promise<void> => {
    setIsLoadingItems(true);
    try {
      const items = await fetchRecentItems();
      setRecentItems(items);
    } catch (err) {
      const typedErr = err as Error & { requiresReauth?: boolean };
      if (typedErr.requiresReauth) {
        onReauthRequired();
        return;
      }
      // Non-auth error: show empty list rather than crashing the popup.
      console.warn("TabVault: Failed to load recent items:", err);
    } finally {
      setIsLoadingItems(false);
    }
  }, [onReauthRequired]);

  useEffect(() => {
    void loadRecentItems();
  }, [loadRecentItems]);

  const handleSaveCurrentTab = async (): Promise<void> => {
    setSaveStatus("saving");
    setSaveMessage(null);

    try {
      await saveCurrentTab();
      setSaveStatus("saved");
      setSaveMessage("Tab saved!");
      void loadRecentItems();
    } catch (err) {
      const typedErr = err as Error & { requiresReauth?: boolean };
      if (typedErr.requiresReauth) {
        onReauthRequired();
        return;
      }
      setSaveStatus("error");
      setSaveMessage(err instanceof Error ? err.message : "Save failed.");
    } finally {
      // Reset status after 2 seconds.
      setTimeout(() => {
        setSaveStatus("idle");
        setSaveMessage(null);
      }, 2000);
    }
  };

  const handleSaveAllTabs = async (): Promise<void> => {
    setSaveStatus("saving");
    setSaveMessage(null);

    try {
      await saveAllTabs();
      setSaveStatus("saved");
      setSaveMessage("All tabs queued for saving!");
      void loadRecentItems();
    } catch (err) {
      const typedErr = err as Error & { requiresReauth?: boolean };
      if (typedErr.requiresReauth) {
        onReauthRequired();
        return;
      }
      setSaveStatus("error");
      setSaveMessage(err instanceof Error ? err.message : "Batch save failed.");
    } finally {
      setTimeout(() => {
        setSaveStatus("idle");
        setSaveMessage(null);
      }, 2000);
    }
  };

  const handleLogout = async (): Promise<void> => {
    await logoutUser();
    onLogout();
  };

  const isSaving = saveStatus === "saving";

  return (
    <div style={styles.container}>
      {/* Header */}
      <div style={styles.header}>
        <div style={styles.logoRow}>
          <img src="../icons/icon32.png" alt="" style={styles.logo} />
          <span style={styles.appName}>TabVault</span>
        </div>
        <button
          onClick={handleLogout}
          style={styles.logoutButton}
          type="button"
          title="Sign out"
        >
          Sign out
        </button>
      </div>

      {/* Action buttons */}
      <div style={styles.actions}>
        <button
          onClick={handleSaveCurrentTab}
          disabled={isSaving}
          style={{
            ...styles.primaryButton,
            opacity: isSaving ? 0.7 : 1,
          }}
          type="button"
        >
          {isSaving ? "Saving…" : "Save current tab"}
        </button>

        <div style={styles.secondaryActions}>
          <button
            onClick={handleSaveAllTabs}
            disabled={isSaving}
            style={{
              ...styles.secondaryButton,
              opacity: isSaving ? 0.7 : 1,
            }}
            type="button"
          >
            Save all tabs
          </button>
          <button
            onClick={onOpenQuickNote}
            disabled={isSaving}
            style={{
              ...styles.secondaryButton,
              opacity: isSaving ? 0.7 : 1,
            }}
            type="button"
          >
            Quick note
          </button>
        </div>
      </div>

      {/* Save status message */}
      {saveMessage && (
        <p
          style={{
            ...styles.statusMessage,
            color: saveStatus === "error" ? "#dc3545" : "#28a745",
          }}
          role="status"
          aria-live="polite"
        >
          {saveMessage}
        </p>
      )}

      {/* Keyboard shortcuts hint */}
      <div style={styles.shortcutsHint}>
        <span>⌨ </span>
        <span>Save tab: Ctrl+Shift+S / ⌘+Shift+S</span>
      </div>

      {/* Recent saves list — AC-048 */}
      <RecentItemsList items={recentItems} isLoading={isLoadingItems} />
    </div>
  );
}

// ---------------------------------------------------------------------------
// Inline styles
// ---------------------------------------------------------------------------

const styles: Record<string, React.CSSProperties> = {
  container: {
    display: "flex",
    flexDirection: "column",
  },
  header: {
    display: "flex",
    alignItems: "center",
    justifyContent: "space-between",
    padding: "12px 16px",
    borderBottom: "1px solid #f0f0f0",
  },
  logoRow: {
    display: "flex",
    alignItems: "center",
    gap: 8,
  },
  logo: {
    width: 24,
    height: 24,
  },
  appName: {
    fontSize: 16,
    fontWeight: 700,
    color: "#1a1a2e",
  },
  logoutButton: {
    background: "none",
    border: "none",
    fontSize: 12,
    color: "#888",
    cursor: "pointer",
    padding: "4px 6px",
  },
  actions: {
    padding: "12px 16px",
    display: "flex",
    flexDirection: "column",
    gap: "8px",
  },
  primaryButton: {
    backgroundColor: "#4a8cde",
    color: "#fff",
    border: "none",
    borderRadius: 6,
    padding: "10px 0",
    fontSize: 14,
    fontWeight: 600,
    cursor: "pointer",
    width: "100%",
  },
  secondaryActions: {
    display: "flex",
    gap: "8px",
  },
  secondaryButton: {
    flex: 1,
    backgroundColor: "#f5f5f5",
    color: "#333",
    border: "1px solid #ddd",
    borderRadius: 6,
    padding: "8px 0",
    fontSize: 13,
    cursor: "pointer",
  },
  statusMessage: {
    fontSize: 13,
    padding: "0 16px 8px",
    textAlign: "center",
  },
  shortcutsHint: {
    fontSize: 11,
    color: "#aaa",
    padding: "0 16px 8px",
    textAlign: "center",
  },
};

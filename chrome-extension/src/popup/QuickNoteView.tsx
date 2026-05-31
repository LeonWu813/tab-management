import React, { useEffect, useRef, useState } from "react";
import { saveQuickNote } from "./messaging.js";

interface QuickNoteViewProps {
  /** Called after the note is saved so the parent can return to the main view. */
  onNoteSaved: () => void;
  /** Called when the user cancels without saving. */
  onCancel: () => void;
  /** Called when a 401 / requiresReauth error occurs. */
  onReauthRequired: () => void;
}

/**
 * Quick note input panel.
 *
 * AC-051: The system shall open the quick note input in the extension popup
 * when the user activates the Ctrl+Shift+N / Cmd+Shift+N shortcut.
 *
 * US-008: Create plain text notes from the extension popup.
 */
export function QuickNoteView({
  onNoteSaved,
  onCancel,
  onReauthRequired,
}: QuickNoteViewProps): React.ReactElement {
  const [noteBody, setNoteBody] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  // Auto-focus the textarea when this view is shown (AC-051 UX).
  useEffect(() => {
    textareaRef.current?.focus();
  }, []);

  const handleSubmit = async (e: React.FormEvent): Promise<void> => {
    e.preventDefault();

    const trimmed = noteBody.trim();
    if (!trimmed) return;

    setErrorMessage(null);
    setIsSubmitting(true);

    try {
      await saveQuickNote(trimmed);
      onNoteSaved();
    } catch (err) {
      const typedErr = err as Error & { requiresReauth?: boolean };
      if (typedErr.requiresReauth) {
        onReauthRequired();
        return;
      }
      setErrorMessage(
        err instanceof Error ? err.message : "Failed to save note."
      );
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div style={styles.container}>
      <div style={styles.header}>
        <h2 style={styles.title}>Quick note</h2>
        <button onClick={onCancel} style={styles.cancelButton} type="button">
          ✕
        </button>
      </div>

      <form onSubmit={handleSubmit} style={styles.form}>
        <textarea
          ref={textareaRef}
          value={noteBody}
          onChange={(e) => setNoteBody(e.target.value)}
          placeholder="Write your note here…"
          rows={5}
          style={styles.textarea}
          disabled={isSubmitting}
          aria-label="Note content"
        />

        {errorMessage && (
          <p style={styles.errorText} role="alert">
            {errorMessage}
          </p>
        )}

        <div style={styles.actions}>
          <button
            type="button"
            onClick={onCancel}
            style={styles.secondaryButton}
            disabled={isSubmitting}
          >
            Cancel
          </button>
          <button
            type="submit"
            disabled={isSubmitting || !noteBody.trim()}
            style={{
              ...styles.primaryButton,
              opacity: isSubmitting || !noteBody.trim() ? 0.6 : 1,
            }}
          >
            {isSubmitting ? "Saving…" : "Save note"}
          </button>
        </div>
      </form>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Inline styles
// ---------------------------------------------------------------------------

const styles: Record<string, React.CSSProperties> = {
  container: {
    padding: "16px",
    display: "flex",
    flexDirection: "column",
    gap: "12px",
  },
  header: {
    display: "flex",
    alignItems: "center",
    justifyContent: "space-between",
  },
  title: {
    fontSize: 16,
    fontWeight: 600,
    color: "#1a1a2e",
  },
  cancelButton: {
    background: "none",
    border: "none",
    fontSize: 16,
    cursor: "pointer",
    color: "#888",
    padding: "2px 6px",
  },
  form: {
    display: "flex",
    flexDirection: "column",
    gap: "10px",
  },
  textarea: {
    width: "100%",
    padding: "8px 10px",
    border: "1px solid #ccc",
    borderRadius: 6,
    fontSize: 13,
    fontFamily: "inherit",
    resize: "vertical",
    outline: "none",
  },
  errorText: {
    fontSize: 12,
    color: "#dc3545",
  },
  actions: {
    display: "flex",
    gap: "8px",
    justifyContent: "flex-end",
  },
  secondaryButton: {
    backgroundColor: "#f0f0f0",
    color: "#444",
    border: "1px solid #ddd",
    borderRadius: 6,
    padding: "7px 14px",
    fontSize: 13,
    cursor: "pointer",
  },
  primaryButton: {
    backgroundColor: "#4a8cde",
    color: "#fff",
    border: "none",
    borderRadius: 6,
    padding: "7px 14px",
    fontSize: 13,
    fontWeight: 600,
    cursor: "pointer",
  },
};

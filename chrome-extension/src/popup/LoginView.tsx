import React, { useState } from "react";
import { loginUser } from "./messaging.js";

interface LoginViewProps {
  /** Called after a successful login so the parent can switch to the main view. */
  onLoginSuccess: () => void;
  /**
   * Optional message shown at the top of the login form — used when the
   * re-authentication prompt is shown after a token refresh failure (AC-054).
   */
  reauthMessage?: string;
}

/**
 * Login form displayed when the user is not authenticated or when a token
 * refresh returns HTTP 401 (AC-054 re-authentication prompt).
 *
 * Submits credentials to the background service worker via sendMessage, which
 * stores the returned tokens in chrome.storage.local (AC-052).
 */
export function LoginView({ onLoginSuccess, reauthMessage }: LoginViewProps): React.ReactElement {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleSubmit = async (e: React.FormEvent): Promise<void> => {
    e.preventDefault();
    setErrorMessage(null);
    setIsSubmitting(true);

    try {
      await loginUser(email.trim(), password);
      onLoginSuccess();
    } catch (err) {
      setErrorMessage(
        err instanceof Error ? err.message : "Login failed. Please try again."
      );
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div style={styles.container}>
      {/* Header */}
      <div style={styles.header}>
        <img src="../icons/icon48.png" alt="TabVault" style={styles.logo} />
        <h1 style={styles.title}>TabVault</h1>
        <p style={styles.subtitle}>Save tabs. Keep focus.</p>
      </div>

      {/* Re-auth prompt (AC-054) */}
      {reauthMessage && (
        <div style={styles.reauthBanner} role="alert">
          {reauthMessage}
        </div>
      )}

      {/* Login form */}
      <form onSubmit={handleSubmit} style={styles.form} noValidate>
        <label htmlFor="email" style={styles.label}>
          Email
        </label>
        <input
          id="email"
          type="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          placeholder="you@example.com"
          required
          autoFocus
          autoComplete="username"
          style={styles.input}
          disabled={isSubmitting}
        />

        <label htmlFor="password" style={styles.label}>
          Password
        </label>
        <input
          id="password"
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          placeholder="Password"
          required
          autoComplete="current-password"
          style={styles.input}
          disabled={isSubmitting}
        />

        {errorMessage && (
          <p style={styles.errorText} role="alert">
            {errorMessage}
          </p>
        )}

        <button
          type="submit"
          disabled={isSubmitting || !email || !password}
          style={{
            ...styles.primaryButton,
            opacity: isSubmitting || !email || !password ? 0.6 : 1,
          }}
        >
          {isSubmitting ? "Signing in…" : "Sign in"}
        </button>
      </form>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Inline styles — avoids a separate CSS file import complexity in the extension
// ---------------------------------------------------------------------------

const styles: Record<string, React.CSSProperties> = {
  container: {
    padding: "20px 16px",
    display: "flex",
    flexDirection: "column",
    gap: "12px",
  },
  header: {
    display: "flex",
    flexDirection: "column",
    alignItems: "center",
    gap: "4px",
    marginBottom: "4px",
  },
  logo: {
    width: 40,
    height: 40,
  },
  title: {
    fontSize: 20,
    fontWeight: 700,
    color: "#1a1a2e",
  },
  subtitle: {
    fontSize: 13,
    color: "#666",
  },
  reauthBanner: {
    backgroundColor: "#fff3cd",
    border: "1px solid #ffc107",
    borderRadius: 6,
    padding: "8px 12px",
    fontSize: 13,
    color: "#856404",
  },
  form: {
    display: "flex",
    flexDirection: "column",
    gap: "8px",
  },
  label: {
    fontSize: 13,
    fontWeight: 500,
    color: "#444",
  },
  input: {
    padding: "8px 10px",
    border: "1px solid #ccc",
    borderRadius: 6,
    fontSize: 14,
    outline: "none",
    width: "100%",
  },
  errorText: {
    fontSize: 12,
    color: "#dc3545",
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
    marginTop: 4,
  },
};

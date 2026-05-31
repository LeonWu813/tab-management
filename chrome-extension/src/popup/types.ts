/**
 * Shared TypeScript types used across the popup components.
 */

/** A saved item record as returned by the backend. */
export interface ItemRecord {
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

/** View state of the popup. */
export type PopupView =
  | "login"       // User is not authenticated — show login form.
  | "main"        // User is authenticated — show main actions and recent items.
  | "quick-note"; // User activated Cmd+Shift+N — show note input.

import React from "react";
import type { ItemRecord } from "./types.js";
import { formatRelativeTime } from "./relativeTime.js";

interface RecentItemsListProps {
  items: ItemRecord[];
  isLoading: boolean;
}

/**
 * Displays the 5 most recently saved items with title and relative timestamp.
 *
 * AC-048: The system shall display a list of the user's 5 most recently saved
 * items in the extension popup, each showing the item title and relative saved
 * timestamp.
 */
export function RecentItemsList({ items, isLoading }: RecentItemsListProps): React.ReactElement {
  if (isLoading) {
    return (
      <div style={styles.section}>
        <h2 style={styles.sectionTitle}>Recent saves</h2>
        <p style={styles.emptyText}>Loading…</p>
      </div>
    );
  }

  if (items.length === 0) {
    return (
      <div style={styles.section}>
        <h2 style={styles.sectionTitle}>Recent saves</h2>
        <p style={styles.emptyText}>No saved items yet.</p>
      </div>
    );
  }

  return (
    <div style={styles.section}>
      <h2 style={styles.sectionTitle}>Recent saves</h2>
      <ul style={styles.list} role="list">
        {items.map((item) => (
          <RecentItemRow key={item.id} item={item} />
        ))}
      </ul>
    </div>
  );
}

interface RecentItemRowProps {
  item: ItemRecord;
}

function RecentItemRow({ item }: RecentItemRowProps): React.ReactElement {
  const displayTitle =
    item.title ??
    item.noteBody?.slice(0, 60) ??
    item.url ??
    "(Untitled)";

  const relativeTime = formatRelativeTime(item.createdAt);

  const handleClick = (): void => {
    if (item.url) {
      // Open the saved URL in a new tab.
      chrome.tabs.create({ url: item.url });
    }
  };

  return (
    <li style={styles.listItem}>
      <button
        onClick={item.url ? handleClick : undefined}
        style={{
          ...styles.itemButton,
          cursor: item.url ? "pointer" : "default",
        }}
        title={displayTitle}
        disabled={!item.url}
      >
        {/* Favicon or item type indicator */}
        <span style={styles.itemIcon} aria-hidden="true">
          {item.itemType === "NOTE" ? "📝" : "🔗"}
        </span>
        <span style={styles.itemText}>
          <span style={styles.itemTitle}>{truncateTitle(displayTitle, 50)}</span>
          <span style={styles.itemTime}>{relativeTime}</span>
        </span>
      </button>
    </li>
  );
}

function truncateTitle(title: string, maxLength: number): string {
  if (title.length <= maxLength) return title;
  return title.slice(0, maxLength - 1) + "…";
}

// ---------------------------------------------------------------------------
// Inline styles
// ---------------------------------------------------------------------------

const styles: Record<string, React.CSSProperties> = {
  section: {
    marginTop: 8,
  },
  sectionTitle: {
    fontSize: 12,
    fontWeight: 600,
    textTransform: "uppercase",
    letterSpacing: "0.05em",
    color: "#888",
    marginBottom: 6,
    padding: "0 16px",
  },
  list: {
    listStyle: "none",
    margin: 0,
    padding: 0,
  },
  listItem: {
    borderTop: "1px solid #f0f0f0",
  },
  itemButton: {
    display: "flex",
    alignItems: "center",
    gap: 8,
    width: "100%",
    padding: "8px 16px",
    background: "none",
    border: "none",
    textAlign: "left",
    fontSize: 13,
    color: "#1a1a2e",
  },
  itemIcon: {
    fontSize: 14,
    flexShrink: 0,
  },
  itemText: {
    display: "flex",
    flexDirection: "column",
    gap: 1,
    overflow: "hidden",
  },
  itemTitle: {
    fontWeight: 500,
    overflow: "hidden",
    textOverflow: "ellipsis",
    whiteSpace: "nowrap",
  },
  itemTime: {
    fontSize: 11,
    color: "#999",
  },
};

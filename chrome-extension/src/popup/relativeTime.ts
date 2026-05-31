/**
 * Formats an ISO 8601 timestamp as a human-readable relative time string.
 *
 * Examples:
 *   "just now"       — less than 60 seconds ago
 *   "5 minutes ago"  — 1-59 minutes ago
 *   "2 hours ago"    — 1-23 hours ago
 *   "3 days ago"     — 1-6 days ago
 *   "2 weeks ago"    — 1-3 weeks ago
 *   "2 months ago"   — 1+ months ago
 */
export function formatRelativeTime(isoTimestamp: string): string {
  const now = Date.now();
  const then = new Date(isoTimestamp).getTime();

  if (isNaN(then)) {
    return "unknown";
  }

  const diffMs = now - then;
  const diffSeconds = Math.floor(diffMs / 1000);
  const diffMinutes = Math.floor(diffSeconds / 60);
  const diffHours = Math.floor(diffMinutes / 60);
  const diffDays = Math.floor(diffHours / 24);
  const diffWeeks = Math.floor(diffDays / 7);
  const diffMonths = Math.floor(diffDays / 30);

  if (diffSeconds < 60) return "just now";
  if (diffMinutes < 60)
    return diffMinutes === 1 ? "1 minute ago" : `${diffMinutes} minutes ago`;
  if (diffHours < 24)
    return diffHours === 1 ? "1 hour ago" : `${diffHours} hours ago`;
  if (diffDays < 7)
    return diffDays === 1 ? "1 day ago" : `${diffDays} days ago`;
  if (diffWeeks < 5)
    return diffWeeks === 1 ? "1 week ago" : `${diffWeeks} weeks ago`;
  return diffMonths === 1 ? "1 month ago" : `${diffMonths} months ago`;
}

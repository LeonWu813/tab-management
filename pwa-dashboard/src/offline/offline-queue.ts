/**
 * Offline queue for requests that cannot be submitted while the device is offline.
 *
 * AC-041: note creation requests queued in the service worker when offline.
 * AC-064: Share Target URLs queued using the same mechanism when received offline.
 *
 * Requests are stored in IndexedDB via the service worker's Background Sync API.
 * When the service worker is not available (e.g., dev mode without SW), requests
 * are stored in localStorage as a fallback and retried on the next online event.
 */

export interface QueuedRequest {
  type: 'CREATE_NOTE' | 'SHARE_TARGET_SAVE';
  payload: Record<string, unknown>;
  url: string;
  method: string;
  timestamp: number;
}

const QUEUE_STORAGE_KEY = 'tabvault_offline_queue';

/**
 * Adds a request to the offline queue.
 *
 * If the browser supports Background Sync (service worker with sync), the queue
 * entry is posted to the service worker. Otherwise it falls back to localStorage.
 */
export async function queueOfflineRequest(
  request: Omit<QueuedRequest, 'timestamp'>,
): Promise<void> {
  const entry: QueuedRequest = { ...request, timestamp: Date.now() };

  // Attempt to send to service worker via postMessage for Background Sync
  if ('serviceWorker' in navigator && navigator.serviceWorker.controller) {
    navigator.serviceWorker.controller.postMessage({
      type: 'QUEUE_REQUEST',
      payload: entry,
    });

    // Also register a sync tag so the SW retries on reconnect
    try {
      const registration = await navigator.serviceWorker.ready;
      if ('sync' in registration) {
        await (registration as ServiceWorkerRegistration & {
          sync: { register: (tag: string) => Promise<void> };
        }).sync.register('offline-queue');
      }
    } catch {
      // Background Sync not supported; SW postMessage alone is sufficient
    }
    return;
  }

  // Fallback: localStorage
  const stored = getLocalQueue();
  stored.push(entry);
  try {
    localStorage.setItem(QUEUE_STORAGE_KEY, JSON.stringify(stored));
  } catch {
    // localStorage quota exceeded — best effort; cannot queue
    console.warn('offline-queue: localStorage quota exceeded, cannot queue request', {
      type: request.type,
    });
  }
}

function getLocalQueue(): QueuedRequest[] {
  try {
    const raw = localStorage.getItem(QUEUE_STORAGE_KEY);
    if (!raw) return [];
    return JSON.parse(raw) as QueuedRequest[];
  } catch {
    return [];
  }
}

/**
 * Flushes the localStorage fallback queue by submitting all pending requests.
 * Called when the 'online' event fires and the service worker is unavailable.
 */
export async function flushLocalQueue(accessToken: string): Promise<void> {
  const queue = getLocalQueue();
  if (queue.length === 0) return;

  const remaining: QueuedRequest[] = [];

  for (const entry of queue) {
    try {
      const response = await fetch(
        `${import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'}${entry.url}`,
        {
          method: entry.method,
          headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${accessToken}`,
          },
          body: JSON.stringify(entry.payload),
        },
      );
      if (!response.ok) {
        // Keep in queue if server error (might be transient)
        remaining.push(entry);
      }
    } catch {
      remaining.push(entry);
    }
  }

  if (remaining.length === 0) {
    localStorage.removeItem(QUEUE_STORAGE_KEY);
  } else {
    localStorage.setItem(QUEUE_STORAGE_KEY, JSON.stringify(remaining));
  }
}

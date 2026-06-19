/**
 * Modal for creating a plain text note.
 *
 * Notes created while offline are queued in the service worker and submitted
 * to the backend when connectivity is restored (AC-041).
 */
import { useState } from 'react';
import { useCreateNote } from './use-items';
import { queueOfflineRequest } from '../offline/offline-queue';
import { ApiError } from '../api/api-client';

interface CreateNoteModalProps {
  onClose: () => void;
}

export default function CreateNoteModal({ onClose }: CreateNoteModalProps) {
  const [noteBody, setNoteBody] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [isQueued, setIsQueued] = useState(false);
  const createNote = useCreateNote();

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    const trimmed = noteBody.trim();
    if (!trimmed) {
      setError('Note body is required');
      return;
    }

    if (!navigator.onLine) {
      // AC-041: queue note creation request when offline
      await queueOfflineRequest({
        type: 'CREATE_NOTE',
        payload: { noteBody: trimmed },
        url: '/api/items/notes',
        method: 'POST',
      });
      setIsQueued(true);
      return;
    }

    try {
      await createNote.mutateAsync(trimmed);
      onClose();
    } catch (err) {
      // TypeError means fetch failed (network unreachable) — navigator.onLine
      // can lag the DevTools offline simulation, so treat it as offline too.
      if (!navigator.onLine || err instanceof TypeError) {
        await queueOfflineRequest({
          type: 'CREATE_NOTE',
          payload: { noteBody: trimmed },
          url: '/api/items/notes',
          method: 'POST',
        });
        setIsQueued(true);
        return;
      }
      if (err instanceof ApiError) {
        setError(err.message);
      } else {
        setError('Failed to save note. Please try again.');
      }
    }
  }

  if (isQueued) {
    return (
      <div
        className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4"
        role="dialog"
        aria-modal="true"
        aria-label="Note queued for later"
      >
        <div className="bg-white rounded-xl shadow-xl w-full max-w-md p-6 text-center">
          <div className="text-4xl mb-4">📥</div>
          <h2 className="text-lg font-semibold text-gray-900 mb-2">Note queued</h2>
          <p className="text-sm text-gray-500 mb-6">
            You are offline. Your note has been saved locally and will be submitted to the
            server when your connection is restored.
          </p>
          <button
            onClick={onClose}
            className="bg-blue-600 hover:bg-blue-700 text-white font-medium rounded-lg px-4 py-2 text-sm"
          >
            OK
          </button>
        </div>
      </div>
    );
  }

  return (
    <div
      className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4"
      role="dialog"
      aria-modal="true"
      aria-label="Create note"
    >
      <div className="bg-white rounded-xl shadow-xl w-full max-w-md">
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-200">
          <h2 className="text-lg font-semibold text-gray-900">New note</h2>
          <button
            onClick={onClose}
            className="text-gray-400 hover:text-gray-600 text-xl leading-none"
            aria-label="Close modal"
          >
            &times;
          </button>
        </div>

        <form onSubmit={handleSubmit} className="p-6 space-y-4">
          {error && (
            <div className="bg-red-50 border border-red-200 text-red-700 rounded-lg px-3 py-2 text-sm">
              {error}
            </div>
          )}

          <textarea
            autoFocus
            value={noteBody}
            onChange={(e) => {
              setNoteBody(e.target.value);
              setError(null);
            }}
            rows={6}
            className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none"
            placeholder="Type your note here..."
            aria-label="Note body"
          />

          {!navigator.onLine && (
            <p className="text-xs text-amber-600 bg-amber-50 rounded px-3 py-2">
              You are offline. This note will be queued and sent when you reconnect.
            </p>
          )}

          <div className="flex justify-end gap-3">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 text-sm text-gray-600 hover:text-gray-900 transition-colors"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={createNote.isPending}
              className="bg-blue-600 hover:bg-blue-700 disabled:bg-blue-400 text-white font-medium rounded-lg px-4 py-2 text-sm transition-colors"
            >
              {createNote.isPending ? 'Saving...' : !navigator.onLine ? 'Queue note' : 'Save note'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

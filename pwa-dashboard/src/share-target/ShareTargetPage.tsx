/**
 * Share Target landing page.
 *
 * When the user shares a URL from a mobile app via the system share sheet,
 * the browser opens the installed PWA at /share-target?url=...&title=...
 *
 * AC-042: PWA registered as a Share Target.
 * AC-043: Shared URL saved as new item; content analysis pipeline triggered.
 * AC-064: If offline, URL queued in the service worker offline queue.
 */
import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { apiRequest, ApiError } from '../api/api-client';
import { queueOfflineRequest } from '../offline/offline-queue';
import type { ItemResponse } from '../api/types';

type Status = 'saving' | 'saved' | 'queued' | 'error';

export default function ShareTargetPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const [status, setStatus] = useState<Status>('saving');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  // Extract shared data from query params (set by the manifest share_target)
  const sharedUrl = searchParams.get('url') ?? searchParams.get('text') ?? '';
  const sharedTitle = searchParams.get('title') ?? '';

  useEffect(() => {
    if (!sharedUrl) {
      setStatus('error');
      setErrorMessage('No URL was shared.');
      return;
    }

    let cancelled = false;

    async function saveSharedUrl() {
      if (!navigator.onLine) {
        // AC-064: queue when offline — same mechanism as AC-041 note queue
        await queueOfflineRequest({
          type: 'SHARE_TARGET_SAVE',
          payload: { url: sharedUrl, title: sharedTitle || sharedUrl },
          url: '/api/items',
          method: 'POST',
        });
        if (!cancelled) setStatus('queued');
        return;
      }

      try {
        await apiRequest<ItemResponse>('/api/items', {
          method: 'POST',
          body: JSON.stringify({
            url: sharedUrl,
            title: sharedTitle || sharedUrl,
            faviconUrl: null,
          }),
        });
        if (!cancelled) setStatus('saved');
      } catch (error) {
        if (!cancelled) {
          if (error instanceof ApiError && error.status === 0) {
            // Network failure — treat as offline
            await queueOfflineRequest({
              type: 'SHARE_TARGET_SAVE',
              payload: { url: sharedUrl, title: sharedTitle || sharedUrl },
              url: '/api/items',
              method: 'POST',
            });
            setStatus('queued');
          } else {
            setStatus('error');
            setErrorMessage(
              error instanceof ApiError ? error.message : 'Failed to save the shared URL.',
            );
          }
        }
      }
    }

    saveSharedUrl();

    return () => {
      cancelled = true;
    };
  }, [sharedUrl, sharedTitle]);

  // Auto-navigate to dashboard after success
  useEffect(() => {
    if (status === 'saved') {
      const timer = setTimeout(() => navigate('/'), 2000);
      return () => clearTimeout(timer);
    }
  }, [status, navigate]);

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50 px-4">
      <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-8 w-full max-w-md text-center">
        {status === 'saving' && (
          <>
            <div className="text-4xl mb-4">💾</div>
            <h1 className="text-lg font-semibold text-gray-900 mb-2">Saving to TabVault...</h1>
            <p className="text-sm text-gray-500 truncate">{sharedUrl}</p>
          </>
        )}

        {status === 'saved' && (
          <>
            <div className="text-4xl mb-4">✅</div>
            <h1 className="text-lg font-semibold text-gray-900 mb-2">Saved!</h1>
            <p className="text-sm text-gray-500 mb-4 truncate">{sharedUrl}</p>
            <p className="text-xs text-gray-400">Redirecting to dashboard...</p>
          </>
        )}

        {status === 'queued' && (
          <>
            <div className="text-4xl mb-4">📥</div>
            <h1 className="text-lg font-semibold text-gray-900 mb-2">Queued for later</h1>
            <p className="text-sm text-gray-500 mb-4 truncate">{sharedUrl}</p>
            <p className="text-sm text-gray-500 mb-6">
              You are offline. This URL has been saved locally and will be submitted to TabVault
              when your connection is restored.
            </p>
            <button
              onClick={() => navigate('/')}
              className="bg-blue-600 hover:bg-blue-700 text-white font-medium rounded-lg px-4 py-2 text-sm"
            >
              Go to dashboard
            </button>
          </>
        )}

        {status === 'error' && (
          <>
            <div className="text-4xl mb-4">❌</div>
            <h1 className="text-lg font-semibold text-gray-900 mb-2">Could not save</h1>
            <p className="text-sm text-red-600 mb-6">{errorMessage}</p>
            <button
              onClick={() => navigate('/')}
              className="bg-blue-600 hover:bg-blue-700 text-white font-medium rounded-lg px-4 py-2 text-sm"
            >
              Go to dashboard
            </button>
          </>
        )}
      </div>
    </div>
  );
}

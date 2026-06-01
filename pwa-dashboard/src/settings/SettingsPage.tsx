/**
 * Settings page.
 *
 * Exposes:
 *   - Auto-cleanup preferences (staleness threshold, opt-out) — US-011, US-012
 *   - Push notification subscription setup
 */
import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiRequest, ApiError, API_BASE } from '../api/api-client';
import type { CleanupSettingsResponse, VapidPublicKeyResponse } from '../api/types';

const THRESHOLD_OPTIONS = [
  { value: 14, label: '14 days' },
  { value: 30, label: '30 days' },
  { value: 60, label: '60 days' },
  { value: 90, label: '90 days' },
];

function urlBase64ToUint8Array(base64String: string): ArrayBuffer {
  const padding = '='.repeat((4 - (base64String.length % 4)) % 4);
  const base64 = (base64String + padding).replace(/-/g, '+').replace(/_/g, '/');
  const rawData = atob(base64);
  const outputArray = new Uint8Array(rawData.length);
  for (let i = 0; i < rawData.length; ++i) {
    outputArray[i] = rawData.charCodeAt(i);
  }
  return outputArray.buffer;
}

export default function SettingsPage() {
  const queryClient = useQueryClient();
  const [settingsError, setSettingsError] = useState<string | null>(null);
  const [pushStatus, setPushStatus] = useState<
    'idle' | 'requesting' | 'subscribed' | 'error'
  >('idle');
  const [pushError, setPushError] = useState<string | null>(null);

  const { data: settings, isLoading: settingsLoading } = useQuery<CleanupSettingsResponse>({
    queryKey: ['cleanup-settings'],
    queryFn: () => apiRequest<CleanupSettingsResponse>('/api/cleanup-settings'),
  });

  const updateSettings = useMutation({
    mutationFn: (values: { stalenessThresholdDays?: number; autoCleanupEnabled?: boolean }) =>
      apiRequest<CleanupSettingsResponse>('/api/cleanup-settings', {
        method: 'PUT',
        body: JSON.stringify(values),
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['cleanup-settings'] });
      setSettingsError(null);
    },
    onError: (error) => {
      if (error instanceof ApiError) {
        setSettingsError(error.message);
      } else {
        setSettingsError('Failed to save settings.');
      }
    },
  });

  async function handleEnablePushNotifications() {
    setPushStatus('requesting');
    setPushError(null);

    try {
      // Get VAPID public key from backend (AC-061)
      const vapidResponse = await fetch(`${API_BASE}/api/push-subscriptions/vapid-public-key`);
      if (!vapidResponse.ok) {
        throw new Error('Could not fetch VAPID public key');
      }
      const { publicKey }: VapidPublicKeyResponse = await vapidResponse.json();

      // Request browser push permission
      const registration = await navigator.serviceWorker.ready;
      const subscription = await registration.pushManager.subscribe({
        userVisibleOnly: true,
        applicationServerKey: urlBase64ToUint8Array(publicKey),
      });

      const subscriptionJson = subscription.toJSON();

      // Register subscription with backend (AC-060)
      await apiRequest('/api/push-subscriptions', {
        method: 'POST',
        body: JSON.stringify({
          endpoint: subscriptionJson.endpoint,
          auth: subscriptionJson.keys?.auth,
          p256dh: subscriptionJson.keys?.p256dh,
        }),
      });

      setPushStatus('subscribed');
    } catch (error) {
      setPushStatus('error');
      if (error instanceof Error) {
        if (error.name === 'NotAllowedError') {
          setPushError('Push notification permission was denied. Please enable it in your browser settings.');
        } else {
          setPushError(error.message);
        }
      } else {
        setPushError('Failed to set up push notifications.');
      }
    }
  }

  return (
    <div className="pb-20 sm:pb-6 space-y-8 max-w-2xl">
      <h1 className="text-xl font-semibold text-gray-900">Settings</h1>

      {/* Auto-cleanup settings */}
      <section className="bg-white border border-gray-200 rounded-xl p-6 space-y-5">
        <div>
          <h2 className="text-base font-semibold text-gray-900">Auto-Cleanup</h2>
          <p className="text-sm text-gray-500 mt-1">
            TabVault can automatically remind you about items you haven&apos;t visited in a while
            and archive them if you don&apos;t act.
          </p>
        </div>

        {settingsError && (
          <div className="bg-red-50 border border-red-200 text-red-700 rounded-lg px-3 py-2 text-sm">
            {settingsError}
          </div>
        )}

        {settingsLoading ? (
          <div className="text-gray-400 text-sm">Loading settings...</div>
        ) : settings ? (
          <div className="space-y-4">
            {/* Enable/disable toggle */}
            <label className="flex items-center justify-between cursor-pointer">
              <div>
                <span className="text-sm font-medium text-gray-900">Enable auto-cleanup</span>
                <p className="text-xs text-gray-500 mt-0.5">
                  Receive reminders for stale items and auto-archive after the grace period
                </p>
              </div>
              <button
                role="switch"
                aria-checked={settings.autoCleanupEnabled}
                onClick={() =>
                  updateSettings.mutate({ autoCleanupEnabled: !settings.autoCleanupEnabled })
                }
                disabled={updateSettings.isPending}
                className={`relative inline-flex w-11 h-6 rounded-full transition-colors ${
                  settings.autoCleanupEnabled ? 'bg-blue-600' : 'bg-gray-300'
                } disabled:opacity-50`}
                aria-label="Toggle auto-cleanup"
              >
                <span
                  className={`absolute top-0.5 left-0.5 w-5 h-5 bg-white rounded-full shadow transition-transform ${
                    settings.autoCleanupEnabled ? 'translate-x-5' : 'translate-x-0'
                  }`}
                />
              </button>
            </label>

            {/* Staleness threshold */}
            {settings.autoCleanupEnabled && (
              <div>
                <label
                  htmlFor="staleness-threshold"
                  className="block text-sm font-medium text-gray-900 mb-1"
                >
                  Staleness threshold
                </label>
                <p className="text-xs text-gray-500 mb-2">
                  Items not visited within this period will receive a staleness reminder.
                </p>
                <select
                  id="staleness-threshold"
                  value={settings.stalenessThresholdDays}
                  onChange={(e) =>
                    updateSettings.mutate({
                      stalenessThresholdDays: parseInt(e.target.value, 10),
                    })
                  }
                  disabled={updateSettings.isPending}
                  className="border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:opacity-50"
                  aria-label="Staleness threshold in days"
                >
                  {THRESHOLD_OPTIONS.map((opt) => (
                    <option key={opt.value} value={opt.value}>
                      {opt.label}
                    </option>
                  ))}
                </select>
              </div>
            )}
          </div>
        ) : null}
      </section>

      {/* Push notifications */}
      <section className="bg-white border border-gray-200 rounded-xl p-6 space-y-4">
        <div>
          <h2 className="text-base font-semibold text-gray-900">Push Notifications</h2>
          <p className="text-sm text-gray-500 mt-1">
            Get notified when reminders are due, even when the dashboard is not open.
          </p>
        </div>

        {pushError && (
          <div className="bg-red-50 border border-red-200 text-red-700 rounded-lg px-3 py-2 text-sm">
            {pushError}
          </div>
        )}

        {pushStatus === 'subscribed' ? (
          <div className="bg-green-50 border border-green-200 text-green-700 rounded-lg px-3 py-2 text-sm">
            Push notifications are enabled for this device.
          </div>
        ) : (
          <button
            onClick={handleEnablePushNotifications}
            disabled={
              pushStatus === 'requesting' ||
              !('serviceWorker' in navigator) ||
              !('PushManager' in window)
            }
            className="bg-blue-600 hover:bg-blue-700 disabled:bg-blue-400 text-white font-medium rounded-lg px-4 py-2 text-sm transition-colors"
            aria-label="Enable push notifications"
          >
            {pushStatus === 'requesting'
              ? 'Setting up...'
              : !('PushManager' in window)
              ? 'Push not supported in this browser'
              : 'Enable push notifications'}
          </button>
        )}
      </section>
    </div>
  );
}

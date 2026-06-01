import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import App from './App';
import './index.css';
import { flushLocalQueue } from './offline/offline-queue';

// AC-041, AC-064: flush the localStorage fallback queue when connectivity resumes
// (only relevant when the service worker is unavailable; otherwise SW handles sync)
window.addEventListener('online', () => {
  const accessToken = localStorage.getItem('tabvault_access_token');
  if (accessToken) {
    flushLocalQueue(accessToken).catch(() => {
      // Best effort — will retry on next online event
    });
  }
});

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 1000 * 30, // 30 seconds
      retry: 1,
    },
  },
});

const rootElement = document.getElementById('root');
if (!rootElement) {
  throw new Error('Root element not found');
}

createRoot(rootElement).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <App />
    </QueryClientProvider>
  </StrictMode>,
);

import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { VitePWA } from 'vite-plugin-pwa';

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate',
      injectRegister: 'auto',
      // Use generateSW strategy so vite-plugin-pwa generates the service worker
      strategies: 'generateSW',
      workbox: {
        globPatterns: ['**/*.{js,css,html,ico,png,svg,webp,woff,woff2}'],
        runtimeCaching: [
          {
            // Cache API responses for items list — used for offline display (AC-040).
            // Match on path prefix so this works on any host (local dev proxy + production).
            urlPattern: ({ url }: { url: URL }) => url.pathname.startsWith('/api/items'),
            handler: 'NetworkFirst',
            options: {
              cacheName: 'api-items-cache',
              expiration: {
                maxEntries: 500,
                maxAgeSeconds: 60 * 60 * 24 * 7, // 7 days
              },
              networkTimeoutSeconds: 3,
            },
          },
          {
            // Cache categories for offline display.
            urlPattern: ({ url }: { url: URL }) => url.pathname.startsWith('/api/categories'),
            handler: 'NetworkFirst',
            options: {
              cacheName: 'api-categories-cache',
              expiration: {
                maxEntries: 100,
                maxAgeSeconds: 60 * 60 * 24 * 7,
              },
              networkTimeoutSeconds: 3,
            },
          },
          {
            // Cache reminders for offline display.
            urlPattern: ({ url }: { url: URL }) => url.pathname.startsWith('/api/reminders'),
            handler: 'NetworkFirst',
            options: {
              cacheName: 'api-reminders-cache',
              expiration: {
                maxEntries: 200,
                maxAgeSeconds: 60 * 60 * 24 * 7,
              },
              networkTimeoutSeconds: 3,
            },
          },
        ],
      },
      includeAssets: ['favicon.ico', 'apple-touch-icon.png', 'mask-icon.svg'],
      manifest: {
        name: 'TabVault Dashboard',
        short_name: 'TabVault',
        description: 'Manage your saved tabs and notes',
        theme_color: '#3b82f6',
        background_color: '#ffffff',
        display: 'standalone',
        scope: '/',
        start_url: '/',
        icons: [
          {
            src: 'pwa-192x192.png',
            sizes: '192x192',
            type: 'image/png',
          },
          {
            src: 'pwa-512x512.png',
            sizes: '512x512',
            type: 'image/png',
          },
          {
            src: 'pwa-512x512.png',
            sizes: '512x512',
            type: 'image/png',
            purpose: 'any maskable',
          },
        ],
        // Share Target API registration (AC-042, US-014)
        // When a URL is shared from a mobile app, the browser sends a GET request
        // to /share-target with the URL as the "url" query parameter.
        share_target: {
          action: '/share-target',
          method: 'GET',
          params: {
            title: 'title',
            text: 'text',
            url: 'url',
          },
        },
      },
    }),
  ],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: 'dist',
  },
});

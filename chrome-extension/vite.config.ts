import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import { resolve } from "path";

/**
 * Vite config for the TabVault Chrome Extension (Manifest V3).
 *
 * Two entry points are bundled separately:
 *  - popup/popup.html   → popup UI (React), output to dist/popup/popup.html
 *  - background/index   → background service worker (plain TS, no DOM)
 *
 * The manifest.json and icons are copied from the public/ directory.
 *
 * base: "" ensures all asset URLs in the generated HTML are relative paths
 * (e.g., ./assets/popup-abc.js) rather than absolute paths (/assets/...).
 * Chrome extensions require relative or chrome-extension:// URLs — absolute
 * paths starting with "/" are not resolved correctly in extension context.
 */
export default defineConfig({
  plugins: [react()],
  base: "",
  build: {
    outDir: "dist",
    emptyOutDir: true,
    rollupOptions: {
      input: {
        popup: resolve(__dirname, "popup/popup.html"),
        background: resolve(__dirname, "src/background/index.ts"),
      },
      output: {
        // Predictable output filenames so manifest.json references are stable.
        entryFileNames: (chunkInfo) => {
          if (chunkInfo.name === "background") {
            return "background/index.js";
          }
          return "popup/assets/[name]-[hash].js";
        },
        chunkFileNames: "popup/assets/[name]-[hash].js",
        assetFileNames: "popup/assets/[name]-[hash][extname]",
      },
    },
  },
  // public/ contents (manifest.json, icons/) are copied to dist/ as-is.
  publicDir: "public",
});

import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { defineConfig } from 'vite';

const projectRoot = path.dirname(fileURLToPath(import.meta.url));

export default defineConfig(() => ({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': path.resolve(projectRoot, '.'),
    },
  },
  server: {
    // Preview links are served from sandbox-specific e2b.app hosts.
    allowedHosts: ['.e2b.app', 'localhost', '127.0.0.1'],
    // HMR is disabled in AI Studio via DISABLE_HMR env var.
    hmr: process.env.DISABLE_HMR !== 'true',
    // Disable file watching when DISABLE_HMR is true to save CPU during agent edits.
    watch: process.env.DISABLE_HMR === 'true' ? null : {},
  },
}));

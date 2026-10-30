import { copyFileSync, existsSync, mkdirSync, readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { defineConfig, type Plugin } from 'vite';
import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';

const projectRoot = path.dirname(fileURLToPath(import.meta.url));
const esrganModelDirectory = path.resolve(projectRoot, 'node_modules/@upscalerjs/default-model/models');
const esrganModelFiles = ['model.json', 'group1-shard1of1.bin'];

function bundleEsrganModel(): Plugin {
  return {
    name: 'editorshub-bundle-esrgan-model',
    configureServer(server) {
      server.middlewares.use('/models', (request, response, next) => {
        const requestedFile = (request.url ?? '').split('?')[0].replace(/^\/+/, '');
        if (!esrganModelFiles.includes(requestedFile)) { next(); return; }
        const source = path.resolve(esrganModelDirectory, requestedFile);
        if (!source.startsWith(`${esrganModelDirectory}${path.sep}`) || !existsSync(source)) { next(); return; }
        response.setHeader('Content-Type', requestedFile.endsWith('.json') ? 'application/json' : 'application/octet-stream');
        response.setHeader('Cache-Control', 'public, max-age=31536000, immutable');
        response.end(readFileSync(source));
      });
    },
    closeBundle() {
      const outputDirectory = path.resolve(projectRoot, 'dist/models');
      mkdirSync(outputDirectory, { recursive: true });
      for (const file of esrganModelFiles) {
        const source = path.resolve(esrganModelDirectory, file);
        if (!existsSync(source)) throw new Error(`Bundled ESRGAN model file is missing: ${source}`);
        copyFileSync(source, path.resolve(outputDirectory, file));
      }
    },
  };
}

export default defineConfig(() => ({
  plugins: [react(), tailwindcss(), bundleEsrganModel()],
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

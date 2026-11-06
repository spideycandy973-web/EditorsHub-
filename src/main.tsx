import { createRoot } from 'react-dom/client';
import App from './App';
import { editor } from './editor/store';
import '@fontsource/roboto/400.css';
import '@fontsource/roboto/500.css';
import '@fontsource/roboto/700.css';
import '@fontsource/roboto/900.css';
import './app.css';
import { applyBrand } from './app/brand';

applyBrand();

// Exposed for debugging/automation; the editor is fully usable without it.
(window as unknown as { __editor: typeof editor }).__editor = editor;

const root = document.getElementById('root');
if (!root) throw new Error('Root element missing');

createRoot(root).render(<App />);

/* Offline caching is for the hosted web/PWA build only.
 *
 * The Android app is a Capacitor WebView: every asset is already local inside
 * the APK, and a cache-first worker would keep serving the PREVIOUS build's
 * index.html after an update — the exact "app never updates" failure. On a
 * native build we skip registration and clear any worker installed by older
 * versions, so a new APK always shows its own UI. */
const nativeShell = (() => {
  const w = window as unknown as {
    Capacitor?: { isNativePlatform?: () => boolean; getPlatform?: () => string };
  };
  try {
    if (w.Capacitor?.isNativePlatform?.()) return true;
    const p = w.Capacitor?.getPlatform?.();
    if (p && p !== 'web') return true;
  } catch { /* ignore */ }
  // Capacitor Android serves the bundle from https://localhost inside a WebView.
  // Two extra independent signals cover the window where the JS bridge has not
  // been injected yet; a hosted web/PWA build is never on https://localhost.
  if (location.protocol === 'https:' && location.hostname === 'localhost') return true;
  return /^(capacitor|app|file):/.test(location.protocol) || /; wv\)/i.test(navigator.userAgent);
})();

/* Exposed so the in-app "Clear cached UI" control can force a clean reload. */
(window as unknown as { __clearUiCache: () => Promise<void> }).__clearUiCache = async () => {
  try {
    if ('caches' in window) {
      const keys = await caches.keys();
      await Promise.all(keys.map(k => caches.delete(k)));
    }
    if ('serviceWorker' in navigator) {
      const regs = await navigator.serviceWorker.getRegistrations();
      await Promise.all(regs.map(r => r.unregister()));
    }
  } catch { /* ignore */ }
  location.reload();
};

if (!nativeShell && 'serviceWorker' in navigator && location.protocol.startsWith('http')) {
  window.addEventListener('load', () => {
    const url = new URL('./sw.js', document.baseURI).href;
    navigator.serviceWorker.register(url, { updateViaCache: 'none' })
      .then(reg => { void reg.update(); })
      .catch(() => undefined);
  });
} else if ('serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    void navigator.serviceWorker.getRegistrations()
      .then(regs => Promise.all(regs.map(r => r.unregister())))
      .then(async () => {
        if ('caches' in window) {
          const keys = await caches.keys();
          await Promise.all(keys.map(k => caches.delete(k)));
        }
      })
      .catch(() => undefined);
  });
}

import { createRoot } from 'react-dom/client';
import App from './App';
import { editor } from './editor/store';
import './app.css';
import { applyBrand } from './app/brand';

applyBrand();

// Exposed for debugging/automation; the editor is fully usable without it.
(window as unknown as { __editor: typeof editor }).__editor = editor;

const root = document.getElementById('root');
if (!root) throw new Error('Root element missing');

createRoot(root).render(<App />);

/* Register a tiny offline service worker when served over http(s). */
if ('serviceWorker' in navigator && location.protocol.startsWith('http')) {
  window.addEventListener('load', () => {
    const url = new URL('./sw.js', document.baseURI).href;
    navigator.serviceWorker.register(url).catch(() => undefined);
  });
}

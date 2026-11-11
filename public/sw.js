/* EditorsHub — offline shell cache for the web/PWA build only.
 *
 * v4.4.0: this must NEVER serve a stale index.html. Navigation requests are
 * network-first; only immutable hashed assets are served from the cache, and
 * the cache name is tied to the build so an update always replaces the shell.
 * The native Capacitor build does not register this worker at all (see
 * src/main.tsx): its assets ship inside the APK and need no HTTP cache. */
const CACHE = 'editorshub-v18';
const SHELL = [
  './', './index.html', './manifest.json',
  './icon.svg', './logo.svg',
  './icon-192.png', './icon-512.png', './icon-maskable-512.png',
  './apple-touch-icon.png', './favicon-32.png',
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE)
      .then((cache) => cache.addAll(SHELL))
      .then(() => self.skipWaiting())
      .catch(() => self.skipWaiting()),
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim()),
  );
});

self.addEventListener('fetch', (event) => {
  const req = event.request;
  if (req.method !== 'GET' || new URL(req.url).origin !== self.location.origin) return;
  // HTML/navigation must always come from the network first: a cached
  // index.html pins the old hashed JS/CSS and silently freezes the whole UI.
  if (req.mode === 'navigate' || req.destination === 'document' ||
      new URL(req.url).pathname.endsWith('/index.html')) {
    event.respondWith(
      fetch(req).then((res) => {
        if (res.ok) {
          const copy = res.clone();
          caches.open(CACHE).then((cache) => cache.put(req, copy)).catch(() => {});
        }
        return res;
      }).catch(() => caches.match(req).then((c) => c || caches.match('./index.html'))),
    );
    return;
  }
  event.respondWith(
    caches.match(req).then((cached) => {
      if (cached) return cached;
      return fetch(req).then((res) => {
        if (res.ok && res.type === 'basic') {
          const copy = res.clone();
          caches.open(CACHE).then((cache) => cache.put(req, copy)).catch(() => {});
        }
        return res;
      }).catch(() => caches.match('./index.html'));
    }),
  );
});

/* Lets the app force a clean reload (About → Clear cached UI). */
self.addEventListener('message', (event) => {
  if (event.data === 'CLEAR_UI_CACHE') {
    event.waitUntil(
      caches.keys()
        .then((keys) => Promise.all(keys.map((k) => caches.delete(k))))
        .then(() => self.registration.unregister())
        .then(() => self.clients.matchAll({ type: 'window' }))
        .then((clients) => clients.forEach((c) => c.navigate(c.url))),
    );
  }
});

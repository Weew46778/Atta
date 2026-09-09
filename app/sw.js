/* آتا — کارگرِ سرویس: کشِ کاملِ اپ برای اجرایِ کاملاً آفلاین */
const CACHE = 'atta-v3-1';
const ASSETS = [
  './',
  './index.html',
  './manifest.webmanifest',
  './css/base.css',
  './js/util.js',
  './js/data-core.js',
  './js/voice.js',
  './js/content-quiz.js',
  './js/content-story.js',
  './js/content-word.js',
  './js/content-ethics.js',
  './js/content-facts.js',
  './js/content-toon.js',
  './js/content-toddler.js',
  './js/content-parent.js',
  './js/ui.js',
  './js/activities.js',
  './js/explorer.js',
  './js/toddler.js',
  './js/parent.js',
  './js/app.js',
  './assets/fonts/Vazirmatn-Regular.woff2',
  './assets/fonts/Vazirmatn-Medium.woff2',
  './assets/fonts/Vazirmatn-SemiBold.woff2',
  './assets/fonts/Vazirmatn-Bold.woff2',
  './assets/fonts/Vazirmatn-ExtraBold.woff2',
  './assets/img/icon-192.png',
  './assets/img/icon-512.png',
  './assets/img/icon-maskable-512.png',
];

self.addEventListener('install', (e) => {
  e.waitUntil(
    caches.open(CACHE).then((c) => c.addAll(ASSETS)).then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (e) => {
  e.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (e) => {
  if (e.request.method !== 'GET') return;
  const url = new URL(e.request.url);
  if (url.origin !== location.origin) return; /* فقط دارایی‌های خودمان */
  e.respondWith(
    caches.match(e.request, { ignoreSearch: true }).then((hit) => {
      if (hit) {
        /* به‌روزرسانیِ بی‌صدا در پس‌زمینه */
        fetch(e.request).then((res) => {
          if (res && res.ok) caches.open(CACHE).then((c) => c.put(e.request, res.clone()));
        }).catch(() => {});
        return hit;
      }
      return fetch(e.request).then((res) => {
        if (res && res.ok && url.protocol.startsWith('http')) {
          const clone = res.clone();
          caches.open(CACHE).then((c) => c.put(e.request, clone));
        }
        return res;
      }).catch(() => caches.match('./index.html'));
    })
  );
});

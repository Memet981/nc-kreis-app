// Speichert die Seite im Browser, damit sie auch ohne Internet läuft.
// Antwort kommt sofort aus dem Speicher, im Hintergrund wird die neue Version geholt
// (beim nächsten Öffnen ist sie dann da).
const SPEICHER = "nc-kreis-v1";
const DATEIEN = ["./", "index.html", "manifest.webmanifest", "icon.svg", "icon-192.png", "icon-512.png"];

self.addEventListener("install", ev => {
  ev.waitUntil(caches.open(SPEICHER).then(c => c.addAll(DATEIEN)).then(() => self.skipWaiting()));
});

self.addEventListener("activate", ev => {
  ev.waitUntil(
    caches.keys()
      .then(namen => Promise.all(namen.filter(n => n !== SPEICHER).map(n => caches.delete(n))))
      .then(() => self.clients.claim()),
  );
});

self.addEventListener("fetch", ev => {
  if (ev.request.method !== "GET" || new URL(ev.request.url).origin !== location.origin) return;
  ev.respondWith(
    caches.open(SPEICHER).then(async c => {
      const alt = await c.match(ev.request, { ignoreSearch: true });
      const neu = fetch(ev.request)
        .then(antwort => { if (antwort.ok) c.put(ev.request, antwort.clone()); return antwort; })
        .catch(() => alt);
      if (alt) { ev.waitUntil(neu); return alt; }
      return neu;
    }),
  );
});

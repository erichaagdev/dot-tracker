// Opens the app from a cache, so it shows its own loading screen at once while a backend scaled to zero starts. The
// build fills in the shell below with that build's version and files, so a build that changes the app changes this
// file too, and the browser installs the new worker and its cache on the next visit.

const shell = self.__SHELL__
const cacheName = `shell-${shell.version}`

// The same paths SpaForwardingController leaves to the backend, for sign-in, invite links, the API and the health check
const backendOnly = /^\/(?:api|oauth2|login|join|actuator)(?:\/|$)/

self.addEventListener('install', (event) => {
  event.waitUntil(caches.open(cacheName).then((cache) => cache.addAll(shell.files)).then(() => self.skipWaiting()))
})

self.addEventListener('activate', (event) => {
  event.waitUntil(caches.keys().then((names) => Promise.all(names.filter((name) => name !== cacheName).map((name) => caches.delete(name)))))
})

self.addEventListener('fetch', (event) => {
  const request = event.request
  const url = new URL(request.url)
  if (request.method !== 'GET' || url.origin !== self.location.origin || backendOnly.test(url.pathname)) {
    return
  }
  // A path without a file extension is a screen, which index.html opens, the way SpaForwardingController forwards it
  if (request.mode === 'navigate' && !url.pathname.includes('.')) {
    event.respondWith(fromCache('/index.html', request))
  } else if (shell.files.includes(url.pathname)) {
    event.respondWith(fromCache(url.pathname, request))
  }
})

function fromCache(path, request) {
  return caches.match(path, { cacheName }).then((cached) => cached ?? fetch(request))
}

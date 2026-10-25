// Routes live in the path, such as /books/<id>, so a link to any screen can be shared or reloaded. In-app links are
// plain <a href> elements, which followLink turns into history entries without a page load. Paths the server
// handles, such as /oauth2 and /login, load normally.

import { tick } from 'svelte'

const routes = [
  { name: 'shelf', pattern: /^\/$/ },
  { name: 'browse', pattern: /^\/browse$/ },
  { name: 'new-book', pattern: /^\/books\/new$/ },
  { name: 'book', pattern: /^\/books\/([^/]+)$/, params: ['id'] },
  { name: 'edit-book', pattern: /^\/books\/([^/]+)\/edit$/, params: ['id'] },
  { name: 'wishlist', pattern: /^\/wishlist$/ },
  { name: 'account', pattern: /^\/account$/ },
  { name: 'group', pattern: /^\/group$/ },
  { name: 'welcome', pattern: /^\/welcome$/ },
]

const serverPaths = /^\/(api|oauth2|login|logout|join|actuator)(\/|$)/

const here = $state({ path: window.location.pathname, search: window.location.search })

const current = $derived(match(here.path))

export const route = {
  get name() {
    return current.name
  },
  get params() {
    return current.params
  },
  get query() {
    return new URLSearchParams(here.search)
  },
}

/** Goes to an in-app path, as a new history entry unless replace is set. */
export function navigate(to, { replace = false } = {}) {
  const url = new URL(to, window.location.href)
  if (url.pathname + url.search === here.path + here.search) {
    return
  }
  const index = history.state?.index ?? 0
  if (replace) {
    history.replaceState({ index, from: history.state?.from }, '', url)
  } else {
    history.replaceState({ ...history.state, index, scroll: window.scrollY }, '')
    history.pushState({ index: index + 1, from: current.name }, '', url)
  }
  here.path = url.pathname
  here.search = url.search
  if (!replace) {
    window.scrollTo(0, 0)
  }
}

/** The screen this one was opened from, when it was opened inside the app. */
export function cameFrom() {
  return (history.state?.index ?? 0) > 0 ? history.state.from : undefined
}

/** Goes back to the previous screen when there is one in the app, and to the fallback path otherwise. */
export function goBack(fallback) {
  if ((history.state?.index ?? 0) > 0) {
    history.back()
  } else {
    navigate(fallback, { replace: true })
  }
}

/** Handles a click anywhere in the page, following an in-app link without a page load. */
export function followLink(event) {
  if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) {
    return
  }
  const anchor = event.target.closest?.('a[href]')
  if (!anchor || anchor.target || anchor.hasAttribute('download') || anchor.origin !== window.location.origin) {
    return
  }
  if (serverPaths.test(anchor.pathname)) {
    return
  }
  event.preventDefault()
  navigate(anchor.pathname + anchor.search)
}

// The app puts each screen's scroll position back itself, once the screen has drawn
history.scrollRestoration = 'manual'

window.addEventListener('popstate', async (event) => {
  here.path = window.location.pathname
  here.search = window.location.search
  await tick()
  window.scrollTo(0, event.state?.scroll ?? 0)
})

function match(path) {
  for (const route of routes) {
    const found = route.pattern.exec(path)
    if (found) {
      const params = Object.fromEntries((route.params ?? []).map((name, i) => [name, decodeURIComponent(found[i + 1])]))
      return { name: route.name, params }
    }
  }
  return { name: 'not-found', params: {} }
}
